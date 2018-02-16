package com.softwaremill.example.session;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.dispatch.MessageDispatcher;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.Route;
import akka.http.javadsl.unmarshalling.Unmarshaller;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Flow;
import com.softwaremill.session.BasicSessionEncoder;
import com.softwaremill.session.CheckHeader;
import com.softwaremill.session.RefreshTokenStorage;
import com.softwaremill.session.Refreshable;
import com.softwaremill.session.SessionConfig;
import com.softwaremill.session.SessionEncoder;
import com.softwaremill.session.SessionManager;
import com.softwaremill.session.SetSessionTransport;
import com.softwaremill.session.javadsl.HttpSessionAwareDirectives;
import com.softwaremill.session.javadsl.InMemoryRefreshTokenStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.CompletionStage;

import static com.softwaremill.session.javadsl.SessionTransports.CookieST;


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

        final ActorSystem system = ActorSystem.create("example");
        final ActorMaterializer materializer = ActorMaterializer.create(system);
        final Http http = Http.get(system);

        final MessageDispatcher dispatcher = system.dispatchers().lookup("akka.actor.default-dispatcher");
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
                                                        complete(StatusCodes.OK)
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