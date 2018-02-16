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

public class OneOffSetRefreshableGetTest extends HttpSessionAwareDirectivesTest {

    protected Route buildRoute(HttpSessionAwareDirectives<String> testDirectives, SessionContinuity<String> oneOff, SessionContinuity<String> refreshable, SetSessionTransport sessionTransport, CsrfCheckMode<String> checkHeader) {
        return
            route(
                path("set", () ->
                    testDirectives.setSession(oneOff, sessionTransport, SESSION, () -> complete(StatusCodes.OK))
                ),
                path("getOpt", () ->
                    testDirectives.optionalSession(refreshable, sessionTransport, session -> complete(session.toString()))
                ),
                path("touchReq", () ->
                    testDirectives.touchRequiredSession(refreshable, sessionTransport, session -> complete(session))
                ),
                path("invalidate", () ->
                    testDirectives.invalidateSession(refreshable, sessionTransport, () -> complete(StatusCodes.OK))
                )

            );
    }

    @Test
    public void shouldReadAnOptionalSessionWhenOnlyTheSessionIsSet_UsingCookies() {
        // given
        final Route route = createRoute(CookieST);

        // and
        TestRouteResult setRouteResult = testRoute(route)
            .run(HttpRequest.GET("/set"));
        HttpCookie sessionData = getSessionDataCookieValues(setRouteResult.response());

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
    public void shouldReadAnOptionalSessionWhenOnlyTheSessionIsSet_UsingHeaders() {
        // given
        final Route route = createRoute(HeaderST);

        // and
        TestRouteResult setRouteResult = testRoute(route)
            .run(HttpRequest.GET("/set"));
        HttpHeader sessionData = getSessionDataHeaderValues(setRouteResult.response());

        // when
        TestRouteResult getOptRouteResult = testRoute(route)
            .run(HttpRequest.GET("/getOpt")
                .addHeader(RawHeader.create(getSessionDataHeaderName, sessionData.value()))
            );

        // then
        getOptRouteResult.assertStatusCode(StatusCodes.OK);
        getOptRouteResult.assertEntity(EXPECTED_SESSION);
    }

    @Test
    public void shouldInvalidateASession_UsingCookies() {
        // given
        final Route route = createRoute(CookieST);

        // and
        TestRouteResult setRouteResult = testRoute(route)
            .run(HttpRequest.GET("/set"));
        HttpCookie sessionData = getSessionDataCookieValues(setRouteResult.response());

        // when
        TestRouteResult getInvalidationRouteResult = testRoute(route)
            .run(HttpRequest.GET("/invalidate")
                .addHeader(Cookie.create(sessionDataCookieName, sessionData.value()))
            );

        // then
        getInvalidationRouteResult.assertStatusCode(StatusCodes.OK);

        // and
        HttpCookie invalidationRefreshToken = getRefreshTokenCookieValues(getInvalidationRouteResult.response());
        HttpCookie invalidationSessionData = getSessionDataCookieValues(getInvalidationRouteResult.response());
        Assert.assertNull(invalidationRefreshToken);
        Assert.assertTrue(invalidationSessionData.getExpires().isPresent());

    }

    @Test
    public void shouldInvalidateASession_UsingHeaders() {
        // given
        final Route route = createRoute(HeaderST);

        // and
        TestRouteResult setRouteResult = testRoute(route)
            .run(HttpRequest.GET("/set"));
        HttpHeader sessionData = getSessionDataHeaderValues(setRouteResult.response());

        // when
        TestRouteResult getInvalidationRouteResult = testRoute(route)
            .run(HttpRequest.GET("/invalidate")
                .addHeader(RawHeader.create(getSessionDataHeaderName, sessionData.value()))
            );

        // then
        getInvalidationRouteResult.assertStatusCode(StatusCodes.OK);

        // and
        HttpResponse response = getInvalidationRouteResult.response();

        // check _refreshtoken header
        HttpHeader refreshTokenHeaderValues = getRefreshTokenHeaderValues(response);
        Assert.assertNull(refreshTokenHeaderValues);

        // check _sessiondata header
        HttpHeader sessionDataHeaderValues = getSessionDataHeaderValues(response);
        Assert.assertTrue("".equals(sessionDataHeaderValues.value()));

    }

    @Test
    public void shouldTouchTheSession_WithoutSettingARefreshToken_UsingCookies() {
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

        HttpCookie token2 = getRefreshTokenCookieValues(touchReqRouteResult.response());
        HttpCookie session2 = getSessionDataCookieValues(touchReqRouteResult.response());

        touchReqRouteResult.assertStatusCode(StatusCodes.OK);
        touchReqRouteResult.assertEntity(SESSION);

        // The session should be modified with a new expiry date
        Assert.assertNotEquals(session1.value(), session2.value());

        // No refresh token should be set
        Assert.assertNull(token2);

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
    public void shouldTouchTheSession_WithoutSettingARefreshToken_UsingHeaders() {
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

        HttpHeader token2 = getRefreshTokenHeaderValues(touchReqRouteResult.response());
        HttpHeader session2 = getSessionDataHeaderValues(touchReqRouteResult.response());

        touchReqRouteResult.assertStatusCode(StatusCodes.OK);
        touchReqRouteResult.assertEntity(SESSION);

        // The session should be modified with a new expiry date
        Assert.assertNotEquals(session1.value(), session2.value());

        // But the refresh token token should remain the same; no new token should be set
        Assert.assertNull(token2);

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

}