package com.softwaremill.session.javadsl;

import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.model.headers.Cookie;
import akka.http.javadsl.model.headers.HttpCookie;
import akka.http.javadsl.model.headers.RawHeader;
import akka.http.javadsl.server.Route;
import akka.http.javadsl.testkit.TestRouteResult;
import akka.http.scaladsl.model.HttpResponse;
import com.softwaremill.session.CsrfCheckMode;
import com.softwaremill.session.SessionContinuity;
import com.softwaremill.session.SetSessionTransport;
import org.junit.Assert;
import org.junit.Test;

import static com.softwaremill.session.javadsl.SessionTransports.CookieST;
import static com.softwaremill.session.javadsl.SessionTransports.HeaderST;

public class OneOffTest extends HttpSessionAwareDirectivesTest {

    protected Route buildRoute(HttpSessionAwareDirectives<String> testDirectives, SessionContinuity<String> oneOff, SessionContinuity<String> refreshable, SetSessionTransport sessionTransport, CsrfCheckMode<String> checkHeader) {
        return
            route(
                path("set", () ->
                        testDirectives.setSession(oneOff, sessionTransport, SESSION, () -> complete(StatusCodes.OK))
                ),
                path("getOpt", () ->
                    testDirectives.optionalSession(oneOff, sessionTransport, session -> complete(session.toString()))
                ),
                path("getReq", () ->
                    testDirectives.requiredSession(oneOff, sessionTransport, session -> complete(session))
                ),
                path("touchReq", () ->
                    testDirectives.touchRequiredSession(oneOff, sessionTransport, session -> complete(session))
                ),
                path("invalidate", () ->
                        testDirectives.invalidateSession(oneOff, sessionTransport, () -> complete(StatusCodes.OK))
                ),
                path("full", () ->
                    testDirectives.session(oneOff, sessionTransport, sessionResult -> complete(sessionResult.toString()))
                )
            );
    }

    @Test
    public void shouldSetTheCorrectSessionCookieName_UsingCookies() {
        // given
        final Route route = createRoute(CookieST);

        // when
        TestRouteResult testRouteResult = testRoute(route)
            .run(HttpRequest.GET("/set"));

        // then
        testRouteResult
            .assertStatusCode(StatusCodes.OK)
            .assertEntity("OK");

        // and
        HttpResponse response = testRouteResult.response();
        HttpCookie csrfCookie = getSessionDataCookieValues(response);
        Assert.assertNotNull(csrfCookie.value());

    }

    @Test
    public void shouldSetTheSession_UsingCookies() {
        // given
        final Route route = createRoute(CookieST);

        // when
        TestRouteResult testRouteResult = testRoute(route)
            .run(HttpRequest.GET("/set"));

        // then
        testRouteResult
            .assertStatusCode(StatusCodes.OK)
            .assertEntity("OK");

        // and
        HttpResponse response = testRouteResult.response();

        // check _sessiondata cookie
        HttpCookie sessionDataCookie = getSessionDataCookieValues(response);
        Assert.assertTrue(sessionDataCookie.value().endsWith(URL_ENCODED_SESSION));

    }

    @Test
    public void shouldSetTheSession_UsingHeaders() {
        // given
        final Route route = createRoute(HeaderST);

        // when
        TestRouteResult testRouteResult = testRoute(route)
            .run(HttpRequest.GET("/set"));

        // then
        testRouteResult
            .assertStatusCode(StatusCodes.OK)
            .assertEntity("OK");

        // and
        HttpResponse response = testRouteResult.response();

        // check _sessiondata header
        HttpHeader sessionDataHeaderValues = getSessionDataHeaderValues(response);
        Assert.assertTrue(sessionDataHeaderValues.value().endsWith(URL_ENCODED_SESSION));

    }

    @Test
    public void shouldReadAnOptionalSessionWhenTheSessionIsSet_UsingCookies() {
        // given
        final Route route = createRoute(CookieST);

        // when
        TestRouteResult setRouteResult = testRoute(route)
            .run(HttpRequest.GET("/set"));
        HttpCookie sessionData = getSessionDataCookieValues(setRouteResult.response());

        /* 2nd request */
        // when
        TestRouteResult getOptRouteResult = testRoute(route)
            .run(HttpRequest.GET("/getOpt")
                .addHeader(Cookie.create(sessionDataCookieName, sessionData.value()))
            );

        // then
        getOptRouteResult.assertStatusCode(StatusCodes.OK);
        getOptRouteResult.assertEntity(EXPECTED_SESSION);

    }

    @Test
    public void shouldReadAnOptionalSessionWhenTheSessionIsSet_UsingHeaders() {
        // given
        final Route route = createRoute(HeaderST);

        // when
        TestRouteResult setRouteResult = testRoute(route)
            .run(HttpRequest.GET("/set"));
        HttpHeader sessionData = getSessionDataHeaderValues(setRouteResult.response());

        /* 2nd request */
        TestRouteResult getOptRouteResult = testRoute(route)
            .run(HttpRequest.GET("/getOpt")
                .addHeader(RawHeader.create(getSessionDataHeaderName, sessionData.value()))
            );

        // then
        getOptRouteResult.assertStatusCode(StatusCodes.OK);
        getOptRouteResult.assertEntity(EXPECTED_SESSION);

    }

    @Test
    public void shouldReadAnOptionalSessionWhenTheSessionIsNotSet_UsingCookies() {
        // given
        final Route route = createRoute(CookieST);

        // when
        TestRouteResult getOptRouteResult = testRoute(route)
            .run(HttpRequest.GET("/getOpt"));

        // then
        getOptRouteResult.assertStatusCode(StatusCodes.OK);
        getOptRouteResult.assertEntity(NO_SESSION);

    }

    @Test
    public void shouldReadAnOptionalSessionWhenTheSessionIsNotSet_UsingHeaders() {
        // given
        final Route route = createRoute(HeaderST);

        // when
        TestRouteResult getOptRouteResult = testRoute(route)
            .run(HttpRequest.GET("/getOpt"));

        // then
        getOptRouteResult.assertStatusCode(StatusCodes.OK);
        getOptRouteResult.assertEntity(NO_SESSION);

    }

    @Test
    public void shouldReadARequiredSessionWhenTheSessionIsSet_UsingCookies() {
        // given
        final Route route = createRoute(CookieST);

        // when
        TestRouteResult setRouteResult = testRoute(route)
            .run(HttpRequest.GET("/set"));
        HttpCookie sessionData = getSessionDataCookieValues(setRouteResult.response());

        /* 2nd request */
        // when
        TestRouteResult getOptRouteResult = testRoute(route)
            .run(HttpRequest.GET("/getReq")
                .addHeader(Cookie.create(sessionDataCookieName, sessionData.value()))
            );

        // then
        getOptRouteResult.assertStatusCode(StatusCodes.OK);
        getOptRouteResult.assertEntity(SESSION);

    }

    @Test
    public void shouldReadARequiredSessionWhenTheSessionIsSet_UsingHeaders() {
        // given
        final Route route = createRoute(HeaderST);

        // when
        TestRouteResult setRouteResult = testRoute(route)
            .run(HttpRequest.GET("/set"));
        HttpHeader sessionData = getSessionDataHeaderValues(setRouteResult.response());

        /* 2nd request */
        // when
        TestRouteResult getOptRouteResult = testRoute(route)
            .run(HttpRequest.GET("/getReq")
                .addHeader(RawHeader.create(getSessionDataHeaderName, sessionData.value()))
            );

        // then
        getOptRouteResult.assertStatusCode(StatusCodes.OK);
        getOptRouteResult.assertEntity(SESSION);

    }

    @Test
    public void shouldInvalidateASession_UsingCookies() {
        // given
        final Route route = createRoute(CookieST);

        // when
        TestRouteResult setRouteResult = testRoute(route)
            .run(HttpRequest.GET("/set"));
        HttpCookie sessionData = getSessionDataCookieValues(setRouteResult.response());

        /* 2nd request */
        // when
        TestRouteResult invalidateRouteResult = testRoute(route)
            .run(HttpRequest.GET("/invalidate")
                .addHeader(Cookie.create(sessionDataCookieName, sessionData.value()))
            );

        // then
        invalidateRouteResult.assertStatusCode(StatusCodes.OK);

        // and
        HttpCookie invalidatedSessionData = getSessionDataCookieValues(invalidateRouteResult.response());
        Assert.assertNotNull(invalidatedSessionData.value());

    }

    @Test
    public void shouldInvalidateASession_UsingHeaders() {
        // given
        final Route route = createRoute(HeaderST);

        // when
        TestRouteResult setRouteResult = testRoute(route)
            .run(HttpRequest.GET("/set"));
        HttpHeader sessionData = getSessionDataHeaderValues(setRouteResult.response());

        /* 2nd request */
        // when
        TestRouteResult invalidateRouteResult = testRoute(route)
            .run(HttpRequest.GET("/invalidate")
                .addHeader(RawHeader.create(getSessionDataHeaderName, sessionData.value()))
            );

        // then
        invalidateRouteResult.assertStatusCode(StatusCodes.OK);

        // and
        HttpHeader invalidatedSessionData = getSessionDataHeaderValues(invalidateRouteResult.response());
        Assert.assertEquals("", invalidatedSessionData.value());

    }

    @Test
    public void shouldRejectTheRequestIfTheSessionIsNotSet_UsingCookies() {
        // given
        final Route route = createRoute(CookieST);

        // when
        TestRouteResult getOptRouteResult = testRoute(route)
            .run(HttpRequest.GET("/getReq"));

        // then
        getOptRouteResult.assertStatusCode(StatusCodes.FORBIDDEN);

    }

    @Test
    public void shouldRejectTheRequestIfTheSessionIsNotSet_UsingHeaders() {
        // given
        final Route route = createRoute(HeaderST);

        // when
        TestRouteResult getReqRouteResult = testRoute(route)
            .run(HttpRequest.GET("/getReq"));


        // then
        getReqRouteResult.assertStatusCode(StatusCodes.FORBIDDEN);

    }

    @Test
    public void shouldRejectTheRequestIfTheSessionIsInvalid_UsingCookies() {
        // given
        final Route route = createRoute(CookieST);

        // when
        TestRouteResult getOptRouteResult = testRoute(route)
            .run(HttpRequest.GET("/getReq")
                .addHeader(Cookie.create(sessionDataCookieName, "invalid"))
            );

        // then
        getOptRouteResult.assertStatusCode(StatusCodes.FORBIDDEN);

    }

    @Test
    public void shouldRejectTheRequestIfTheSessionIsInvalid_UsingHeaders() {
        // given
        final Route route = createRoute(HeaderST);

        // when
        TestRouteResult getOptRouteResult = testRoute(route)
            .run(HttpRequest.GET("/getReq")
                .addHeader(RawHeader.create(getSessionDataHeaderName, "invalid"))
            );

        // then
        getOptRouteResult.assertStatusCode(StatusCodes.FORBIDDEN);

    }

    @Test
    public void shouldTouchTheSession_UsingCookies() {
        final Route route_fixed = createRoute(CookieST, getExpiring60SessionManagerWithFixedTime());
        final Route route_fixed_plus30s = createRoute(CookieST, getExpiring60Plus30SessionManagerWithFixedTime());
        final Route route_fixed_plus70s = createRoute(CookieST, getExpiring60Plus70SessionManagerWithFixedTime());

        TestRouteResult setRouteResult = testRoute(route_fixed)
            .run(HttpRequest.GET("/set"));
        HttpCookie session1 = getSessionDataCookieValues(setRouteResult.response());

        TestRouteResult touchReqRouteResult = testRoute(route_fixed_plus30s)
            .run(HttpRequest.GET("/touchReq")
                .addHeader(Cookie.create(sessionDataCookieName, session1.value()))
            );

        HttpCookie session2 = getSessionDataCookieValues(touchReqRouteResult.response());

        touchReqRouteResult.assertStatusCode(StatusCodes.OK);
        touchReqRouteResult.assertEntity(SESSION);

        // The session should be modified with a new expiry date
        Assert.assertNotEquals(session1.value(), session2.value());

        // 70 seconds from the initial session, only the touched one should work
        TestRouteResult touchReqRouteResultAfter70 = testRoute(route_fixed_plus70s)
            .run(HttpRequest.GET("/touchReq")
                .addHeader(Cookie.create(sessionDataCookieName, session2.value())) // this session did not expire, it was touched
            );

        touchReqRouteResultAfter70.assertStatusCode(StatusCodes.OK);
        touchReqRouteResultAfter70.assertEntity(SESSION);

        TestRouteResult touchReqRouteResultAfter70Expired = testRoute(route_fixed_plus70s)
            .run(HttpRequest.GET("/touchReq")
                .addHeader(Cookie.create(sessionDataCookieName, session1.value())) // this session did expire, it was not touched
            );

        touchReqRouteResultAfter70Expired.assertStatusCode(StatusCodes.FORBIDDEN);

    }

    @Test
    public void shouldTouchTheSession_KeepingTheRefreshTokenIntact_UsingHeaders() {
        final Route route_fixed = createRoute(HeaderST, getExpiring60SessionManagerWithFixedTime());
        final Route route_fixed_plus30s = createRoute(HeaderST, getExpiring60Plus30SessionManagerWithFixedTime());
        final Route route_fixed_plus70s = createRoute(HeaderST, getExpiring60Plus70SessionManagerWithFixedTime());

        TestRouteResult setRouteResult = testRoute(route_fixed)
            .run(HttpRequest.GET("/set"));
        HttpHeader session1 = getSessionDataHeaderValues(setRouteResult.response());

        TestRouteResult touchReqRouteResult = testRoute(route_fixed_plus30s)
            .run(HttpRequest.GET("/touchReq")
                .addHeader(RawHeader.create(getSessionDataHeaderName, session1.value()))
            );

        HttpHeader session2 = getSessionDataHeaderValues(touchReqRouteResult.response());

        touchReqRouteResult.assertStatusCode(StatusCodes.OK);
        touchReqRouteResult.assertEntity(SESSION);

        // The session should be modified with a new expiry date
        Assert.assertNotEquals(session1.value(), session2.value());

        // 70 seconds from the initial session, only the touched one should work
        TestRouteResult touchReqRouteResultAfter70 = testRoute(route_fixed_plus70s)
            .run(HttpRequest.GET("/touchReq")
                .addHeader(RawHeader.create(getSessionDataHeaderName, session2.value())) // this session did not expire, it was touched
            );

        touchReqRouteResultAfter70.assertStatusCode(StatusCodes.OK);
        touchReqRouteResultAfter70.assertEntity(SESSION);

        TestRouteResult touchReqRouteResultAfter70Expired = testRoute(route_fixed_plus70s)
            .run(HttpRequest.GET("/touchReq")
                .addHeader(RawHeader.create(getSessionDataHeaderName, session1.value())) // this session did expire, it was not touched
            );

        touchReqRouteResultAfter70Expired.assertStatusCode(StatusCodes.FORBIDDEN);
    }

    @Test
    public void shouldReturnDecodedWhenSessionExists() {
        // given
        final Route route = createRoute(CookieST);

        // when
        TestRouteResult setRouteResult = testRoute(route).run(HttpRequest.GET("/set"));
        HttpCookie sessionData = getSessionDataCookieValues(setRouteResult.response());

        /* 2nd request */
        // when
        TestRouteResult fullResult = testRoute(route)
            .run(
                HttpRequest.GET("/full").addHeader(Cookie.create(sessionDataCookieName, sessionData.value()))
            );

        // then
        fullResult.assertStatusCode(StatusCodes.OK);
        fullResult.assertEntity("Decoded(my session object)");
    }

    @Test
    public void shouldReturnNoSessionWhenNoSessionExists() {
        // given
        final Route route = createRoute(CookieST);

        // when
        TestRouteResult fullResult = testRoute(route)
            .run(
                HttpRequest.GET("/full")
            );

        // then
        fullResult.assertStatusCode(StatusCodes.OK);
        fullResult.assertEntity("NoSession");
    }

    @Test
    public void shouldReturnExpiredWhenSessionExpires() {
        final Route route_fixed = createRoute(CookieST, getExpiring60SessionManagerWithFixedTime());
        final Route route_fixed_plus70s = createRoute(CookieST, getExpiring60Plus70SessionManagerWithFixedTime());

        // given
        TestRouteResult setRouteResult = testRoute(route_fixed)
            .run(HttpRequest.GET("/set"));
        HttpCookie session1 = getSessionDataCookieValues(setRouteResult.response());

        TestRouteResult touchReqRouteResultAfter70Expired = testRoute(route_fixed_plus70s)
            .run(HttpRequest.GET("/touchReq")
                .addHeader(Cookie.create(sessionDataCookieName, session1.value()))
            );
        touchReqRouteResultAfter70Expired.assertStatusCode(StatusCodes.FORBIDDEN);

        // when
        TestRouteResult fullResult = testRoute(route_fixed_plus70s)
            .run(HttpRequest.GET("/full")
                .addHeader(Cookie.create(sessionDataCookieName, session1.value()))
            );

        // then
        fullResult.assertStatusCode(StatusCodes.OK);
        fullResult.assertEntity("Expired");
    }

}