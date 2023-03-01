package com.softwaremill.example.session;

import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.dispatch.MessageDispatcher;
import org.apache.pekko.http.javadsl.ConnectHttp;
import org.apache.pekko.http.javadsl.Http;
import org.apache.pekko.http.javadsl.ServerBinding;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.HttpResponse;
import org.apache.pekko.http.javadsl.server.Route;
import org.apache.pekko.stream.ActorMaterializer;
import org.apache.pekko.stream.javadsl.Flow;
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

public class SessionInvalidationJava extends HttpSessionAwareDirectives<MyJavaSession> {

    private static final Logger LOGGER = LoggerFactory.getLogger(SessionInvalidationJava.class);
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

    public SessionInvalidationJava(MessageDispatcher dispatcher) {
        super(new SessionManager<>(
                SessionConfig.defaultConfig(SECRET),
                BASIC_ENCODER
            )
        );

        // use Refreshable for sessions, which needs to be refreshed or OneOff otherwise
        // using Refreshable, a refresh token is set in form of a cookie or a custom header
        refreshable = new Refreshable<>(getSessionManager(), REFRESH_TOKEN_STORAGE, dispatcher);

        // set the session transport - based on Cookies (or Headers)
        sessionTransport = CookieST;
    }

    public static void main(String[] args) throws IOException {

        // ** pekko-http boiler plate **
        ActorSystem system = ActorSystem.create("example");
        final ActorMaterializer materializer = ActorMaterializer.create(system);
        final Http http = Http.get(system);

        // ** pekko-http-session setup **
        MessageDispatcher dispatcher = system.dispatchers().lookup("org.apache.pekko.actor.default-dispatcher");
        final SessionInvalidationJava app = new SessionInvalidationJava(dispatcher);

        // ** pekko-http boiler plate continued **
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
                        path("logout", () ->
                            post(() ->
                                requiredSession(refreshable, sessionTransport, session ->
                                    invalidateSession(refreshable, sessionTransport, () ->
                                        extractRequestContext(ctx -> {
                                                LOGGER.info("Logging out {}", session.getUsername());
                                                return onSuccess(() -> ctx.completeWith(HttpResponse.create()), routeResult ->
                                                    complete("ok")
                                                );
                                            }
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            );
    }
}

