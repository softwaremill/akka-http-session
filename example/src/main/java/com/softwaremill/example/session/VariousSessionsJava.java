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
import com.softwaremill.session.OneOff;
import com.softwaremill.session.SessionConfig;
import com.softwaremill.session.SessionEncoder;
import com.softwaremill.session.SessionManager;
import com.softwaremill.session.SessionResult;
import com.softwaremill.session.SessionResult.Corrupt;
import com.softwaremill.session.SessionResult.CreatedFromToken;
import com.softwaremill.session.SessionResult.Decoded;
import com.softwaremill.session.SessionResult.Expired$;
import com.softwaremill.session.SessionResult.NoSession$;
import com.softwaremill.session.SessionResult.TokenNotFound$;
import com.softwaremill.session.SetSessionTransport;
import com.softwaremill.session.javadsl.HttpSessionAwareDirectives;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.CompletionStage;

import static com.softwaremill.session.javadsl.SessionTransports.CookieST;

public class VariousSessionsJava extends HttpSessionAwareDirectives<MyJavaSession> {

    private static final Logger LOGGER = LoggerFactory.getLogger(VariousSessionsJava.class);

    private static final String SECRET = "c05ll3lesrinf39t7mc5h6un6r0c69lgfno69dsak3vabeqamouq4328cuaekros401ajdpkh60rrtpd8ro24rbuqmgtnd1ebag6ljnb65i8a55d482ok7o0nch0bfbe";
    private static final SessionEncoder<MyJavaSession> BASIC_ENCODER = new BasicSessionEncoder<>(MyJavaSession.getSerializer());

    private OneOff<MyJavaSession> oneOff;
    private SetSessionTransport sessionTransport;

    public VariousSessionsJava(MessageDispatcher dispatcher) {
        super(new SessionManager<>(
                SessionConfig.defaultConfig(SECRET),
                BASIC_ENCODER
            )
        );

        oneOff = new OneOff<>(getSessionManager());
        sessionTransport = CookieST;
    }

    public static void main(String[] args) throws IOException {

        final ActorSystem system = ActorSystem.create("example");
        final ActorMaterializer materializer = ActorMaterializer.create(system);
        final Http http = Http.get(system);

        final MessageDispatcher dispatcher = system.dispatchers().lookup("org.apache.pekko.actor.default-dispatcher");
        final VariousSessionsJava app = new VariousSessionsJava(dispatcher);

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
                        path("secret", () ->
                            get(() -> requiredSession(oneOff, sessionTransport, myJavaSession -> complete("treasure")))
                        ),
                        path("open", () ->
                            get(() -> optionalSession(oneOff, sessionTransport, myJavaSession -> complete("small treasure")))
                        ),
                        path("detail", () -> session(oneOff, sessionTransport, sessionResult -> {
                            if (sessionResult instanceof Decoded) {
                                return complete("decoded");
                            } else if (sessionResult instanceof CreatedFromToken) {
                                return complete("created from token");
                            } else if (NoSession$.MODULE$.equals(sessionResult)) {
                                return complete("no session");
                            } else if (TokenNotFound$.MODULE$.equals(sessionResult)) {
                                return complete("token not found");
                            } else if (Expired$.MODULE$.equals(sessionResult)) {
                                return complete("expired");
                            } else if (((SessionResult<?>) sessionResult) instanceof Corrupt) {
                                return complete("corrupt");
                            }
                            LOGGER.error("Unknown session result: {}", sessionResult);
                            throw new RuntimeException("What's going on here?");
                        }))
                    )
                )
            );
    }
}

