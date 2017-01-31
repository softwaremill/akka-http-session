package com.softwaremill.example;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.dispatch.MessageDispatcher;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.model.Uri;
import akka.http.javadsl.server.Route;
import akka.http.javadsl.unmarshalling.Unmarshaller;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Flow;
import com.softwaremill.session.CheckHeader;
import com.softwaremill.session.CookieST$;
import com.softwaremill.session.javadsl.InMemoryRefreshTokenStorage;
import com.softwaremill.session.JValueSessionSerializer$;
import com.softwaremill.session.JwtSessionEncoder;
import com.softwaremill.session.RefreshTokenStorage;
import com.softwaremill.session.Refreshable;
import com.softwaremill.session.SessionConfig;
import com.softwaremill.session.SessionEncoder;
import com.softwaremill.session.SessionManager;
import com.softwaremill.session.javadsl.HttpSessionAwareDirectives;
import org.json4s.DefaultFormats$;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.CompletionStage;


public class JavaJwtExample extends HttpSessionAwareDirectives<String> {

    private static final Logger logger = LoggerFactory.getLogger(JavaJwtExample.class);
    private static final String SECRET = "c05ll3lesrinf39t7mc5h6un6r0c69lgfno69dsak3vabeqamouq4328cuaekros401ajdpkh60rrtpd8ro24rbuqmgtnd1ebag6ljnb65i8a55d482ok7o0nch0bfbe";
    private static final SessionEncoder<String> JWT_ENCODER = new JwtSessionEncoder<>(JValueSessionSerializer$.MODULE$.stringToJValueSessionSerializer(), DefaultFormats$.MODULE$);

    // in-memory refresh token storage
    private static final RefreshTokenStorage<String> REFRESH_TOKEN_STORAGE = new InMemoryRefreshTokenStorage<String>() {
        @Override
        public void log(String msg) {
            logger.info(msg);
        }
    };

    private Refreshable<String> refreshable;
    private CookieST$ sessionTransport;

    public JavaJwtExample(MessageDispatcher dispatcher) {
        super(new SessionManager<>(
                SessionConfig.defaultConfig(SECRET),
                JWT_ENCODER
            )
        );

        // use Refreshable for sessions, which needs to be refreshed or OneOff otherwise
        // using Refreshable, a refresh token is set in form of a cookie or a custom header
        refreshable = new Refreshable<>(getSessionManager(), REFRESH_TOKEN_STORAGE, dispatcher);

        // set the session transport - based on Cookies (or Headers)
        sessionTransport = CookieST$.MODULE$;
    }

    public static void main(String[] args) throws IOException {

        // ** akka-http boiler plate **
        ActorSystem system = ActorSystem.create("example");
        final ActorMaterializer materializer = ActorMaterializer.create(system);
        final Http http = Http.get(system);

        // ** akka-http-session setup **
        MessageDispatcher dispatcher = system.dispatchers().lookup("akka.actor.default-dispatcher");
        final JavaJwtExample app = new JavaJwtExample(dispatcher);

        // ** akka-http boiler plate continued **
        final Flow<HttpRequest, HttpResponse, NotUsed> routes = app.createRoutes().flow(system, materializer);
        final CompletionStage<ServerBinding> binding = http.bindAndHandle(routes, ConnectHttp.toHost("localhost", 8080), materializer);

        System.out.println("Server started, press enter to stop");
        System.in.read();

        binding
            .thenCompose(ServerBinding::unbind)
            .thenAccept(unbound -> system.terminate());
    }

    private Route createRoutes() {
        CheckHeader<String> checkHeader = new CheckHeader<>(getSessionManager());
        return
            route(
                pathSingleSlash(() ->
                    redirect(Uri.create("/site/index.html"), StatusCodes.FOUND)
                ),
                randomTokenCsrfProtection(checkHeader, () ->
                    pathPrefix("api", () ->
                        route(
                            path("do_login", () ->
                                post(() ->
                                    entity(Unmarshaller.entityToString(), body -> {
                                            logger.info("Logging in {}", body);
                                            //return setSession(refreshable, sessionTransport, new MySession(body), () ->
                                            return setSession(refreshable, sessionTransport, body, () ->
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
                            ),

                            // This should be protected and accessible only when logged in
                            path("do_logout", () ->
                                post(() ->
                                    requiredSession(refreshable, sessionTransport, session ->
                                        invalidateSession(refreshable, sessionTransport, () ->
                                            extractRequestContext(ctx -> {
                                                    //logger.info("Logging out {}", session.getUsername());
                                                    logger.info("Logging out {}", session);
                                                    return onSuccess(() -> ctx.completeWith(HttpResponse.create()), routeResult ->
                                                        complete("ok")
                                                    );
                                                }
                                            )
                                        )
                                    )
                                )
                            ),

                            // This should be protected and accessible only when logged in
                            path("current_login", () ->
                                get(() ->
                                    requiredSession(refreshable, sessionTransport, session ->
                                        extractRequestContext(ctx -> {
                                                logger.info("Current session: " + session);
                                                return onSuccess(() -> ctx.completeWith(HttpResponse.create()), routeResult ->
                                                    //complete(session.getUsername())
                                                    complete(session)
                                                );
                                            }
                                        )
                                    )
                                )
                            )
                        )
                    )
                ),
                pathPrefix("site", () ->
                    getFromResourceDirectory(""))
            );
    }
}
