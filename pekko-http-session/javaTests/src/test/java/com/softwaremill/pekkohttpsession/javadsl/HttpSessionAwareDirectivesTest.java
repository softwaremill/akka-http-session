package com.softwaremill.pekkohttpsession.javadsl;

import com.softwaremill.pekkohttpsession.javadsl.HttpSessionAwareDirectives;
import com.softwaremill.pekkohttpsession.javadsl.InMemoryRefreshTokenStorage;
import com.softwaremill.pekkohttpsession.javadsl.SessionSerializers;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.http.javadsl.model.HttpHeader;
import org.apache.pekko.http.javadsl.model.HttpResponse;
import org.apache.pekko.http.javadsl.model.headers.HttpCookie;
import org.apache.pekko.http.javadsl.model.headers.SetCookie;
import org.apache.pekko.http.javadsl.server.Route;
import org.apache.pekko.http.javadsl.testkit.JUnitRouteTest;
import org.apache.pekko.stream.ActorMaterializer;
import org.apache.pekko.stream.Materializer;
import com.softwaremill.pekkohttpsession.BasicSessionEncoder;
import com.softwaremill.pekkohttpsession.CheckHeader;
import com.softwaremill.pekkohttpsession.CheckHeaderAndForm;
import com.softwaremill.pekkohttpsession.CsrfCheckMode;
import com.softwaremill.pekkohttpsession.OneOff;
import com.softwaremill.pekkohttpsession.RefreshTokenStorage;
import com.softwaremill.pekkohttpsession.Refreshable;
import com.softwaremill.pekkohttpsession.SessionConfig;
import com.softwaremill.pekkohttpsession.SessionContinuity;
import com.softwaremill.pekkohttpsession.SessionEncoder;
import com.softwaremill.pekkohttpsession.SessionManager;
import com.softwaremill.pekkohttpsession.SetSessionTransport;
import com.softwaremill.pekkohttpsession.SingleValueSessionSerializer;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;
import scala.compat.java8.JFunction0;
import scala.compat.java8.JFunction1;
import scala.util.Try;

import java.net.URLEncoder;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static java.lang.String.format;

public abstract class HttpSessionAwareDirectivesTest extends JUnitRouteTest {

    static final String SESSION = "my session object";
    static final String EXPECTED_SESSION = format("Optional[%s]", SESSION);
    static final String NO_SESSION = "Optional.empty";
    static final String URL_ENCODED_SESSION = encode();
    private static final String SECRET = "c05ll3lesrinf39t7mc5h6un6r0c69lgfno69dsak3vabeqamouq4328cuaekros401ajdpkh60rrtpd8ro24rbuqmgtnd1ebag6ljnb65i8a55d482ok7o0nch0bfbe";
    private static final String MAX_SESSION_AGE = "60 seconds";
    private static final SessionEncoder<String> ENCODER = new BasicSessionEncoder<>(
        new SingleValueSessionSerializer<>(
            ((JFunction1<String, String>) session -> session),
            ((JFunction1<String, Try<String>>) session -> Try.apply((JFunction0<String>) (() -> session))),
            SessionSerializers.StringToStringSessionSerializer
        )
    );
    // in-memory refresh token storage
    private static final RefreshTokenStorage<String> REFRESH_TOKEN_STORAGE = new InMemoryRefreshTokenStorage<String>() {
        private Logger logger = Logger.getLogger("TokenStorage");

        @Override
        public void log(String msg) {
            logger.info(msg);
        }
    };
    String refreshTokenCookieName; // _refreshtoken
    String sessionDataCookieName; // _sessiondata
    String getRefreshTokenHeaderName; // Refresh-Token
    String getSessionDataHeaderName; // Authorization
    String csrfCookieName; // XSRF-TOKEN
    String csrfSubmittedName; // X-XSRF-TOKEN
    private HttpSessionAwareDirectives<String> testDirectives;
    private String sendRefreshTokenHeaderName; // Set-Refresh-Token
    private String sendSessionDataHeaderName;  // Set-Authorization

    private static String encode() {
        try {
            return URLEncoder.encode(SESSION, "UTF-8");
        } catch (Exception e) {
            throw new RuntimeException("Could not encode session");
        }
    }

    protected Route createRoute(SetSessionTransport sessionTransport) {
        return createRoute(sessionTransport, null, getDefaultSessionManager());
    }

    protected Route createCsrfRouteWithCheckHeaderMode() {
        SessionManager<String> sessionManager = getDefaultSessionManager();
        CheckHeader<String> checkHeader = new CheckHeader<>(sessionManager);
        return createRoute(null, checkHeader, sessionManager);
    }

    protected Route createCsrfRouteWithCheckHeaderAndFormMode() {
        SessionManager<String> sessionManager = getDefaultSessionManager();
        final ActorSystem system = ActorSystem.create("CsrfDirectivesTest");
        final Materializer materializer = ActorMaterializer.create(system);
        CheckHeaderAndForm<String> checkHeaderAndForm = new CheckHeaderAndForm<>(sessionManager, materializer);
        return createRoute(null, checkHeaderAndForm, sessionManager);
    }

    protected Route createRoute(SetSessionTransport sessionTransport, SessionManager<String> manager) {
        return createRoute(sessionTransport, null, manager);
    }

    private Route createRoute(SetSessionTransport sessionTransport, CsrfCheckMode csrfCheckMode, SessionManager<String> manager) {

        testDirectives = new HttpSessionAwareDirectives<>(manager);

        // cookie names
        refreshTokenCookieName = testDirectives.getSessionManager().config().refreshTokenCookieConfig().name();
        sessionDataCookieName = testDirectives.getSessionManager().config().sessionCookieConfig().name();

        // header names when sending to client
        sendRefreshTokenHeaderName = testDirectives.getSessionManager().config().refreshTokenHeaderConfig().sendToClientHeaderName();
        sendSessionDataHeaderName = testDirectives.getSessionManager().config().sessionHeaderConfig().sendToClientHeaderName();

        // header names when receiving from client
        getRefreshTokenHeaderName = testDirectives.getSessionManager().config().refreshTokenHeaderConfig().getFromClientHeaderName();
        getSessionDataHeaderName = testDirectives.getSessionManager().config().sessionHeaderConfig().getFromClientHeaderName();

        // csrf cookie names
        csrfCookieName = testDirectives.getSessionManager().config().csrfCookieConfig().name();
        csrfSubmittedName = testDirectives.getSessionManager().config().csrfSubmittedName();

        Refreshable<String> refreshable = new Refreshable<>(testDirectives.getSessionManager(),
            REFRESH_TOKEN_STORAGE,
            systemResource().system().dispatcher()
        );

        OneOff<String> oneOff = new OneOff<>(testDirectives.getSessionManager());


        return buildRoute(testDirectives, oneOff, refreshable, sessionTransport, csrfCheckMode);

    }

    protected abstract Route buildRoute(
        HttpSessionAwareDirectives<String> testDirectives,
        SessionContinuity<String> oneOff,
        SessionContinuity<String> refreshable,
        SetSessionTransport sessionTransport,
        CsrfCheckMode<String> checkHeader);

    protected HttpCookie getRefreshTokenCookieValues(HttpResponse response) {
        return getCookieValues(response, refreshTokenCookieName);
    }

    protected HttpCookie getSessionDataCookieValues(HttpResponse response) {
        return getCookieValues(response, sessionDataCookieName);
    }

    protected HttpCookie getCsrfTokenCookieValues(HttpResponse response) {
        return getCookieValues(response, csrfCookieName);
    }

    protected HttpHeader getRefreshTokenHeaderValues(HttpResponse response) {
        return getHeaderValues(response, sendRefreshTokenHeaderName);
    }

    protected HttpHeader getSessionDataHeaderValues(HttpResponse response) {
        return getHeaderValues(response, sendSessionDataHeaderName);
    }


    private HttpCookie getCookieValues(HttpResponse response, String cookieName) {

        /*
        cookies we may get:
         Set-Cookie: _sessiondata=1AAFE061C539EFD16A20CBC834608EAE9909A5EF-1485426400816-xmy+session+object; Path=/; HttpOnly
         Set-Cookie: _refreshtoken=t7i7iv0i7b5lbet2:nhnpqrc855anom6sffr70bicl41951dq8d3mms1u6f9pnatc5ouuhl17m74fnda5; Max-Age=2592000; Path=/; HttpOnly
         Set-Cookie: XSRF-TOKEN=1ei80a0p21paueur5smbiokpm9t13cj418fh5pv05537273uesru8utrr3c92s45; Path=/
        */

        List<HttpCookie> cookies = StreamSupport
            .stream(response.getHeaders().spliterator(), false)
            .collect(Collectors.toList())
            .stream()
            .map(header -> ((SetCookie) (header)).cookie())
            .filter(cookie -> cookie.name().equals(cookieName))
            .collect(Collectors.toList());

        return cookies.size() == 0 ? null : cookies.get(0);
    }

    private HttpHeader getHeaderValues(HttpResponse response, String headerName) {
        List<HttpHeader> headers = StreamSupport
            .stream(response.getHeaders().spliterator(), false)
            .filter(header -> headerName.equals(header.name()))
            .collect(Collectors.toList());

        // TODO should throw an Exception if size > 1, once this issue is solved: https://github.com/softwaremill/akka-http-session/issues/28
        return headers.size() == 0 ? null : headers.get(0);
    }

    private SessionManager<String> getDefaultSessionManager() {
        return new SessionManager<>(SessionConfig.defaultConfig(SECRET), ENCODER);
    }

    SessionManager<String> getExpiring60SessionManagerWithFixedTime() {
        return getExpiringSessionManagerWithFixedTime(0);
    }

    SessionManager<String> getExpiring60Plus30SessionManagerWithFixedTime() {
        return getExpiringSessionManagerWithFixedTime(30);
    }

    SessionManager<String> getExpiring60Plus70SessionManagerWithFixedTime() {
        return getExpiringSessionManagerWithFixedTime(70);
    }

    private SessionManager<String> getExpiringSessionManagerWithFixedTime(long delay) {
        return new SessionManager<String>(SessionConfig.fromConfig(ConfigFactory.load()
            .withValue("pekko.http.session.max-age", ConfigValueFactory.fromAnyRef(MAX_SESSION_AGE))
            .withValue("pekko.http.session.server-secret", ConfigValueFactory.fromAnyRef(SECRET))), ENCODER) {
            @Override
            public long nowMillis() {
                return (3028L + delay) * 1000L;
            }
        };
    }

}
