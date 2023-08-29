package com.softwaremill.pekkohttpsession.javadsl;

import org.apache.pekko.http.javadsl.server.AllDirectives;
import org.apache.pekko.http.javadsl.server.Route;
import com.softwaremill.pekkohttpsession.CsrfCheckMode;
import com.softwaremill.pekkohttpsession.GetSessionTransport;
import com.softwaremill.pekkohttpsession.SessionContinuity;
import com.softwaremill.pekkohttpsession.SessionManager;
import com.softwaremill.pekkohttpsession.SessionResult;
import com.softwaremill.pekkohttpsession.SetSessionTransport;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

public class HttpSessionAwareDirectives<T> extends AllDirectives {

    private final SessionManager<T> sessionManager;

    public HttpSessionAwareDirectives(SessionManager<T> sessionManager) {
        this.sessionManager = sessionManager;
    }

    public Route session(SessionContinuity sc, GetSessionTransport st, Function<SessionResult<T>, Route> continuity) {
        return SessionDirectives$.MODULE$.session(sc, st, continuity);
    }

    public Route setSession(SessionContinuity sc, SetSessionTransport st, T session, Supplier<Route> continuity) {
        return SessionDirectives$.MODULE$.setSession(sc, st, session, continuity);
    }

    public Route optionalSession(SessionContinuity sc, SetSessionTransport st, Function<Optional<T>, Route> continuity) {
        return SessionDirectives$.MODULE$.optionalSession(sc, st, continuity);
    }

    public Route requiredSession(SessionContinuity<T> sc, SetSessionTransport st, Function<T, Route> continuity) {
        return SessionDirectives$.MODULE$.requiredSession(sc, st, continuity);
    }

    public Route touchRequiredSession(SessionContinuity<T> sc, SetSessionTransport st, Function<T, Route> continuity) {
        return SessionDirectives$.MODULE$.touchRequiredSession(sc, st, continuity);
    }

    public Route invalidateSession(SessionContinuity<T> sc, SetSessionTransport st, Supplier<Route> continuity) {
        return SessionDirectives$.MODULE$.invalidateSession(sc, st, continuity);
    }

    public Route setNewCsrfToken(CsrfCheckMode<T> checkHeader, Supplier<Route> continuity) {
        return CsrfDirectives$.MODULE$.setNewCsrfToken(checkHeader, continuity);
    }

    public Route randomTokenCsrfProtection(CsrfCheckMode<T> checkHeader, Supplier<Route> continuity) {
        return CsrfDirectives$.MODULE$.randomTokenCsrfProtection(checkHeader, continuity);
    }

    public SessionManager<T> getSessionManager() {
        return sessionManager;
    }
}
