package com.softwaremill.pekkoexample.session;

import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.dispatch.MessageDispatcher;
import org.apache.pekko.http.javadsl.ConnectHttp;
import org.apache.pekko.http.javadsl.Http;
import org.apache.pekko.http.javadsl.ServerBinding;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.HttpResponse;
import org.apache.pekko.http.javadsl.server.Route;
import org.apache.pekko.http.javadsl.unmarshalling.Unmarshaller;
import org.apache.pekko.stream.ActorMaterializer;
import org.apache.pekko.stream.javadsl.Flow;
import com.softwaremill.pekkohttpsession.BasicSessionEncoder;
import com.softwaremill.pekkohttpsession.CheckHeader;
import com.softwaremill.pekkohttpsession.RefreshTokenStorage;
import com.softwaremill.pekkohttpsession.Refreshable;
import com.softwaremill.pekkohttpsession.SessionConfig;
import com.softwaremill.pekkohttpsession.SessionEncoder;
import com.softwaremill.pekkohttpsession.SessionManager;
import com.softwaremill.pekkohttpsession.SetSessionTransport;
import com.softwaremill.pekkohttpsession.javadsl.HttpSessionAwareDirectives;
import com.softwaremill.pekkohttpsession.javadsl.InMemoryRefreshTokenStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.CompletionStage;

import static com.softwaremill.pekkohttpsession.javadsl.SessionTransports.CookieST;


public class SetSessionJava extends HttpSessionAwareDirectives<MyJavaSession> {

    private static final Logger LOGGER = LoggerFactory.getLogger(SetSessionJava.class);

    private static final String SECRET = "c05ll3lesrinf39t7mc5h6un6r0c69lgfno69dsak3vabeqamouq4328cuaekros401ajdpkh60rrtpd8ro24rbuqmgtnd1ebag6ljnb65i8a55d482ok7o0nch0bfbe";
    private static final SessionEncoder<MyJavaSession> BASIC_ENCODER = new BasicSessionEncoder<>(MyJavaSession.getSerializer());

    // in-memory refresh token storage
    private static final RefreshTokenStorage<MyJavaSession> REFRESH_TOKEN_STORAGE = new InMemoryRefreshTokenStorage<MyJavaSession>() {
        @Override
        public void log(String msg) {
            LOGGER.info(msg);
        }
    };

    private Refreshable<MyJavaSession> refreshable;
    private SetSessionTransport sessionTransport;

    public SetSessionJava(MessageDispatcher dispatcher) {
        super(new SessionManager<>(
                SessionConfig.defaultConfig(SECRET),
                BASIC_ENCODER
            )
        );

        refreshable = new Refreshable<>(getSessionManager(), REFRESH_TOKEN_STORAGE, dispatcher);
        sessionTransport = CookieST;
    }

    public static void main(String[] args) throws IOException {

        final ActorSystem system = ActorSystem.create("pekkoexample");
        final ActorMaterializer materializer = ActorMaterializer.create(system);
        final Http http = Http.get(system);

        final MessageDispatcher dispatcher = system.dispatchers().lookup("pekko.actor.default-dispatcher");
        final SetSessionJava app = new SetSessionJava(dispatcher);

        final Flow<HttpRequest, HttpResponse, NotUsed> routes = app.createRoutes().flow(system, materializer);
        final CompletionStage<ServerBinding> binding = http.bindAndHandle(routes, ConnectHttp.toHost("localhost", 8080), materializer);

        System.out.println("Server started, press enter to stop");
        System.in.read();

        binding
            .thenCompose(ServerBinding::unbind)
            .thenAccept(unbound -> system.terminate());
    }

    private Route createRoutes() {
        CheckHeader<MyJavaSession> checkHeader = new CheckHeader<>(getSessionManager());
        return
            route(
                randomTokenCsrfProtection(checkHeader, () ->
                    route(
                        path("login", () ->
                            post(() ->
                                entity(Unmarshaller.entityToString(), body -> {
                                        LOGGER.info("Logging in {}", body);
                                        return setSession(refreshable, sessionTransport, new MyJavaSession(body), () ->
                                            setNewCsrfToken(checkHeader, () ->
                                                extractRequestContext(ctx ->
                                                    onSuccess(() -> ctx.completeWith(HttpResponse.create()), routeResult ->
                                                        complete("ok")
                                                    )
                                                )
                                            )
                                        );
                                    }
                                )
                            )
                        )
                    )
                )
            );
    }
}

