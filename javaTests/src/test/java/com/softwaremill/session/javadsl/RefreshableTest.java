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

public class RefreshableTest extends HttpSessionAwareDirectivesTest {

    protected Route buildRoute(HttpSessionAwareDirectives<String> testDirectives, SessionContinuity<String> oneOff, SessionContinuity<String> refreshable, SetSessionTransport sessionTransport, CsrfCheckMode<String> checkHeader) {
        return
            route(
                path("set", () ->
                        testDirectives.setSession(refreshable, sessionTransport, SESSION, () -> complete(StatusCodes.OK))
                ),
                path("getOpt", () ->
                    testDirectives.optionalSession(refreshable, sessionTransport, session -> complete(session.toString()))
                ),
                path("getReq", () ->
                    testDirectives.requiredSession(refreshable, sessionTransport, session -> complete(session))
                ),
                path("touchReq", () ->
                    testDirectives.touchRequiredSession(refreshable, sessionTransport, session -> complete(session))
                ),
                path("invalidate", () ->
                        testDirectives.invalidateSession(refreshable, sessionTransport, () -> complete(StatusCodes.OK))
                ),
                path("full", () ->
                    testDirectives.session(refreshable, sessionTransport, sessionResult -> complete(sessionResult.toString()))
                )
            );
    }

    @Test
    public void shouldSetTheRefreshTokenCookieToExpireWhen_UsingCookies() {

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
        HttpCookie refreshToken = getRefreshTokenCookieValues(testRouteResult.response());
        Assert.assertEquals(refreshToken.getMaxAge().getAsLong(), 60L * 60L * 24L * 30L); // set to 30 days by default

    }

    @Test
    public void shouldSetBoth_TheSessionAndRefreshTokenWhen_UsingCookies() {
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

        // check _refreshtoken cookie
        HttpCookie refreshTokenCookie = getRefreshTokenCookieValues(response);
        Assert.assertNotNull(refreshTokenCookie.value());

        // check _sessiondata cookie
        HttpCookie sessionDataCookie = getSessionDataCookieValues(response);
        Assert.assertNotNull(sessionDataCookie.value());
    }

    @Test
    public void shouldSetBoth_TheSessionAndRefreshTokenWhen_UsingHeaders() {
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

        // check _refreshtoken header
        HttpHeader refreshTokenHeaderValues = getRefreshTokenHeaderValues(response);
        Assert.assertNotNull(refreshTokenHeaderValues.value());

        // check _sessiondata header
        HttpHeader sessionDataHeaderValues = getSessionDataHeaderValues(response);
        Assert.assertNotNull(sessionDataHeaderValues.value());
    }

    @Test
    public void shouldSetANewRefreshTokenWhenTheSessionIsSetAgain_UsingCookies() {
        // given
        final Route route = createRoute(CookieST);

        // when
        TestRouteResult firstTestRouteResult = testRoute(route)
            .run(HttpRequest.GET("/set"));
        String firstToken = getRefreshTokenCookieValues(firstTestRouteResult.response()).value();

        // and
        TestRouteResult secondTestRouteResult = testRoute(route).run(HttpRequest.GET("/set"));
        String secondToken = getRefreshTokenCookieValues(secondTestRouteResult.response()).value();

        // then
        Assert.assertNotEquals(firstToken, secondToken);
    }

    @Test
    public void shouldSetANewRefreshTokenWhenTheSessionIsSetAgain_UsingHeaders() {
        // given
        final Route route = createRoute(HeaderST);

        // when
        TestRouteResult firstTestRouteResult = testRoute(route)
            .run(HttpRequest.GET("/set"));
        String firstToken = getRefreshTokenHeaderValues(firstTestRouteResult.response()).value();

        // and
        TestRouteResult secondTestRouteResult = testRoute(route).run(HttpRequest.GET("/set"));
        String secondToken = getRefreshTokenHeaderValues(secondTestRouteResult.response()).value();

        // then
        Assert.assertNotEquals(firstToken, secondToken);
    }

    @Test
    public void shouldReadAnOptionalSessionWhenBoth_TheSessionAndRefreshTokenAreSet_UsingCookies() {
        // given
        final Route route = createRoute(CookieST);

        // and
        TestRouteResult setRouteResult = testRoute(route)
            .run(HttpRequest.GET("/set"));
        HttpCookie refreshToken = getRefreshTokenCookieValues(setRouteResult.response());
        HttpCookie sessionData = getSessionDataCookieValues(setRouteResult.response());

        // when
        TestRouteResult getOptRouteResult = testRoute(route)
            .run(HttpRequest.GET("/getOpt")
                .addHeader(Cookie.create(refreshTokenCookieName, refreshToken.value()))
                .addHeader(Cookie.create(sessionDataCookieName, sessionData.value()))
            );

        // then
        getOptRouteResult.assertStatusCode(StatusCodes.OK);
        getOptRouteResult.assertEntity(EXPECTED_SESSION);
    }

    @Test
    public void shouldReadAnOptionalSessionWhenBoth_TheSessionAndRefreshTokenAreSet_UsingHeaders() {
        // given
        final Route route = createRoute(HeaderST);

        // and
        TestRouteResult setRouteResult = testRoute(route)
            .run(HttpRequest.GET("/set"));
        HttpHeader refreshToken = getRefreshTokenHeaderValues(setRouteResult.response());
        HttpHeader sessionData = getSessionDataHeaderValues(setRouteResult.response());

        // when
        TestRouteResult getOptRouteResult = testRoute(route)
            .run(HttpRequest.GET("/getOpt")
                .addHeader(RawHeader.create(getRefreshTokenHeaderName, refreshToken.value()))
                .addHeader(RawHeader.create(getSessionDataHeaderName, sessionData.value()))
            );

        // then
        getOptRouteResult.assertStatusCode(StatusCodes.OK);
        getOptRouteResult.assertEntity(EXPECTED_SESSION);
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
    public void shouldReadAnOptionalSessionWhenNoneIsSet_UsingCookies() {
        // given
        final Route route = createRoute(CookieST);

        // when
        TestRouteResult getOptRouteResult = testRoute(route).run(HttpRequest.GET("/getOpt"));

        // then
        getOptRouteResult.assertStatusCode(StatusCodes.OK);
        getOptRouteResult.assertEntity(NO_SESSION);
    }

    @Test
    public void shouldReadAnOptionalSessionWhenNoneIsSet_UsingHeaders() {
        // given
        final Route route = createRoute(HeaderST);

        // when
        TestRouteResult getOptRouteResult = testRoute(route).run(HttpRequest.GET("/getOpt"));

        // then
        getOptRouteResult.assertStatusCode(StatusCodes.OK);
        getOptRouteResult.assertEntity(NO_SESSION);
    }

    @Test
    public void shouldReadAnOptionalSessionWhenOnlyTheRefreshTokenIsSet_RecreateTheSession_UsingCookies() {
        // given
        final Route route = createRoute(CookieST);

        // and
        TestRouteResult setRouteResult = testRoute(route)
            .run(HttpRequest.GET("/set"));
        HttpCookie refreshToken = getRefreshTokenCookieValues(setRouteResult.response());

        // when
        TestRouteResult getOptRouteResult = testRoute(route)
            .run(HttpRequest.GET("/getOpt")
                .addHeader(Cookie.create(refreshTokenCookieName, refreshToken.value()))
            );

        // then
        getOptRouteResult.assertStatusCode(StatusCodes.OK);
        getOptRouteResult.assertEntity(EXPECTED_SESSION);
    }

    @Test
    public void shouldReadAnOptionalSessionWhenOnlyTheRefreshTokenIsSet_RecreateTheSession_UsingHeaders() {
        // given
        final Route route = createRoute(HeaderST);

        // and
        TestRouteResult setRouteResult = testRoute(route)
            .run(HttpRequest.GET("/set"));
        HttpHeader refreshToken = getRefreshTokenHeaderValues(setRouteResult.response());

        // when
        TestRouteResult getOptRouteResult = testRoute(route)
            .run(HttpRequest.GET("/getOpt")
                .addHeader(RawHeader.create(getRefreshTokenHeaderName, refreshToken.value()))
            );

        // then
        getOptRouteResult.assertStatusCode(StatusCodes.OK);
        getOptRouteResult.assertEntity(EXPECTED_SESSION);
    }

    @Test
    public void shouldSetANewRefreshTokenAfterTheSessionIsRecreated_UsingCookies() {
        // given
        final Route route = createRoute(CookieST);

        // and
        TestRouteResult setRouteResult = testRoute(route)
            .run(HttpRequest.GET("/set"));
        HttpCookie refreshToken1 = getRefreshTokenCookieValues(setRouteResult.response());

        // when
        TestRouteResult getOptRouteResult = testRoute(route)
            .run(HttpRequest.GET("/getOpt")
                .addHeader(Cookie.create(refreshTokenCookieName, refreshToken1.value()))
            );
        HttpCookie refreshToken2 = getRefreshTokenCookieValues(getOptRouteResult.response());

        // then
        Assert.assertNotEquals(refreshToken1.value(), refreshToken2.value());
    }

    @Test
    public void shouldSetANewRefreshTokenAfterTheSessionIsRecreated_UsingHeaders() {
        // given
        final Route route = createRoute(HeaderST);

        // and
        TestRouteResult setRouteResult = testRoute(route)
            .run(HttpRequest.GET("/set"));
        HttpHeader refreshToken1 = getRefreshTokenHeaderValues(setRouteResult.response());

        // when
        TestRouteResult getOptRouteResult = testRoute(route)
            .run(HttpRequest.GET("/getOpt")
                .addHeader(RawHeader.create(getRefreshTokenHeaderName, refreshToken1.value()))
            );
        HttpHeader refreshToken2 = getRefreshTokenHeaderValues(getOptRouteResult.response());

        // then
        Assert.assertNotEquals(refreshToken1.value(), refreshToken2.value());
    }


    @Test
    public void shouldReadARequiredSessionWhenBoth_TheSessionAndRefreshTokensAreSet_UsingCookies() {
        // given
        final Route route = createRoute(CookieST);

        // and
        TestRouteResult setRouteResult = testRoute(route)
            .run(HttpRequest.GET("/set"));
        HttpCookie refreshToken = getRefreshTokenCookieValues(setRouteResult.response());
        HttpCookie sessionData = getSessionDataCookieValues(setRouteResult.response());

        // when
        TestRouteResult getReqRouteResult = testRoute(route)
            .run(HttpRequest.GET("/getReq")
                .addHeader(Cookie.create(refreshTokenCookieName, refreshToken.value()))
                .addHeader(Cookie.create(sessionDataCookieName, sessionData.value()))
            );

        // then
        getReqRouteResult.assertStatusCode(StatusCodes.OK);
        getReqRouteResult.assertEntity(SESSION);

    }

    @Test
    public void shouldReadARequiredSessionWhenBoth_TheSessionAndRefreshTokensAreSet_UsingHeaders() {
        // given
        final Route route = createRoute(HeaderST);

        // and
        TestRouteResult setRouteResult = testRoute(route)
            .run(HttpRequest.GET("/set"));
        HttpHeader refreshToken = getRefreshTokenHeaderValues(setRouteResult.response());
        HttpHeader sessionData = getSessionDataHeaderValues(setRouteResult.response());

        // when
        TestRouteResult getReqRouteResult = testRoute(route)
            .run(HttpRequest.GET("/getReq")
                .addHeader(RawHeader.create(getRefreshTokenHeaderName, refreshToken.value()))
                .addHeader(RawHeader.create(getSessionDataHeaderName, sessionData.value()))
            );

        // then
        getReqRouteResult.assertStatusCode(StatusCodes.OK);
        getReqRouteResult.assertEntity(SESSION);

    }

    @Test
    public void shouldInvalidateASession_UsingCookies() {
        // given
        final Route route = createRoute(CookieST);

        // and
        TestRouteResult setRouteResult = testRoute(route)
            .run(HttpRequest.GET("/set"));
        HttpCookie refreshToken = getRefreshTokenCookieValues(setRouteResult.response());
        HttpCookie sessionData = getSessionDataCookieValues(setRouteResult.response());

        // when
        TestRouteResult getInvalidationRouteResult = testRoute(route)
            .run(HttpRequest.GET("/invalidate")
                .addHeader(Cookie.create(refreshTokenCookieName, refreshToken.value()))
                .addHeader(Cookie.create(sessionDataCookieName, sessionData.value()))
            );

        // then
        getInvalidationRouteResult.assertStatusCode(StatusCodes.OK);

        // and
        HttpCookie invalidationRefreshToken = getRefreshTokenCookieValues(getInvalidationRouteResult.response());
        HttpCookie invalidationSessionData = getSessionDataCookieValues(getInvalidationRouteResult.response());
        Assert.assertTrue(invalidationRefreshToken.getExpires().isPresent());
        Assert.assertTrue(invalidationSessionData.getExpires().isPresent());

    }

    @Test
    public void shouldInvalidateASession_UsingHeaders() {
        // given
        final Route route = createRoute(HeaderST);

        // and
        TestRouteResult setRouteResult = testRoute(route)
            .run(HttpRequest.GET("/set"));
        HttpHeader refreshToken = getRefreshTokenHeaderValues(setRouteResult.response());
        HttpHeader sessionData = getSessionDataHeaderValues(setRouteResult.response());

        // when
        TestRouteResult getInvalidationRouteResult = testRoute(route)
            .run(HttpRequest.GET("/invalidate")
                .addHeader(RawHeader.create(getRefreshTokenHeaderName, refreshToken.value()))
                .addHeader(RawHeader.create(getSessionDataHeaderName, sessionData.value()))
            );

        // then
        getInvalidationRouteResult.assertStatusCode(StatusCodes.OK);

        // and
        HttpResponse response = getInvalidationRouteResult.response();

        // check _refreshtoken header
        HttpHeader refreshTokenHeaderValues = getRefreshTokenHeaderValues(response);
        Assert.assertTrue("".equals(refreshTokenHeaderValues.value()));

        // check _sessiondata header
        HttpHeader sessionDataHeaderValues = getSessionDataHeaderValues(response);
        Assert.assertTrue("".equals(sessionDataHeaderValues.value()));

    }

    @Test
    public void shouldRejectTheRequestIfTheSessionIsNotSet_UsingCookies() {
        // given
        final Route route = createRoute(CookieST);

        // when
        TestRouteResult getReqRouteResult = testRoute(route)
            .run(HttpRequest.GET("/getReq")
            );

        // then
        getReqRouteResult.assertStatusCode(StatusCodes.FORBIDDEN);
    }

    @Test
    public void shouldRejectTheRequestIfTheSessionIsNotSet_UsingHeaders() {
        // given
        final Route route = createRoute(HeaderST);

        // when
        TestRouteResult getReqRouteResult = testRoute(route)
            .run(HttpRequest.GET("/getReq")
            );

        // then
        getReqRouteResult.assertStatusCode(StatusCodes.FORBIDDEN);
    }

    @Test
    public void shouldRejectTheRequestIfTheSessionIsInvalid_UsingCookies() {
        // given
        final Route route = createRoute(CookieST);

        // when
        TestRouteResult getReqRouteResult = testRoute(route)
            .run(HttpRequest.GET("/getReq")
                .addHeader(Cookie.create(sessionDataCookieName, "invalid"))
            );

        // then
        getReqRouteResult.assertStatusCode(StatusCodes.FORBIDDEN);
    }


    @Test
    public void shouldRejectTheRequestIfTheSessionIsInvalid_UsingHeaders() {
        // given
        final Route route = createRoute(HeaderST);

        // when
        TestRouteResult getReqRouteResult = testRoute(route)
            .run(HttpRequest.GET("/getReq")
                .addHeader(RawHeader.create(getSessionDataHeaderName, "invalid"))

            );

        // then
        getReqRouteResult.assertStatusCode(StatusCodes.FORBIDDEN);
    }

    @Test
    public void shouldRejectTheRequestIfTheRefreshTokenIsInvalid_UsingCookies() {
        // given
        final Route route = createRoute(CookieST);

        // when
        TestRouteResult getReqRouteResult = testRoute(route)
            .run(HttpRequest.GET("/getReq")
                .addHeader(Cookie.create(refreshTokenCookieName, "invalid"))
            );

        // then
        getReqRouteResult.assertStatusCode(StatusCodes.FORBIDDEN);
    }


    @Test
    public void shouldRejectTheRequestIfTheRefreshTokenIsInvalid_UsingHeaders() {
        // given
        final Route route = createRoute(HeaderST);

        // when
        TestRouteResult getReqRouteResult = testRoute(route)
            .run(HttpRequest.GET("/getReq")
                .addHeader(Cookie.create(refreshTokenCookieName, "invalid"))
            );

        // then
        getReqRouteResult.assertStatusCode(StatusCodes.FORBIDDEN);
    }

    @Test
    public void shouldSetANewSessionAfterTheSessionIsReCreated_UsingCookies() {
        // given
        final Route route = createRoute(CookieST);

        // and
        TestRouteResult setRouteResult = testRoute(route)
            .run(HttpRequest.GET("/set"));
        HttpCookie refreshToken = getRefreshTokenCookieValues(setRouteResult.response());
        HttpCookie sessionData = getSessionDataCookieValues(setRouteResult.response());

        // then
        Assert.assertNotNull(sessionData.value());

        /* 2nd request */
        // when
        TestRouteResult getOptResult = testRoute(route)
            .run(HttpRequest.GET("/getOpt")
                    .addHeader(Cookie.create(refreshTokenCookieName, refreshToken.value()))
                //.addHeader(Cookie.create(sessionDataCookieName, sessionData.value())) // TODO why doe this cause headers to disappear in the result?
            );

        // then
        getOptResult.assertStatusCode(StatusCodes.OK);
        getOptResult.assertEntity(EXPECTED_SESSION);

        // and
        HttpCookie sessionData2 = getSessionDataCookieValues(getOptResult.response());
        Assert.assertTrue(sessionData2.value().endsWith(URL_ENCODED_SESSION));
        Assert.assertNotEquals(sessionData.value(), sessionData2.value());
    }

    /*
        p should "set a new session after the session is re-created" in {
      Get("/set") ~> routes ~> check {
        val Some(token1) = using.getRefreshToken
        val session1 = using.getSession
        session1 should be ('defined)

        Get("/getOpt") ~>
          addHeader(using.setRefreshTokenHeader(token1)) ~>
          routes ~>
          check {
            val session2 = using.getSession
            session2 should be ('defined)
            session2 should not be (session1)
          }
      }
    }
     */


    @Test
    public void shouldTouchTheSession_KeepingTheRefreshTokenIntact_UsingCookies() {
        final Route route_fixed = createRoute(CookieST, getExpiring60SessionManagerWithFixedTime());
        final Route route_fixed_plus30s = createRoute(CookieST, getExpiring60Plus30SessionManagerWithFixedTime());
        final Route route_fixed_plus70s = createRoute(CookieST, getExpiring60Plus70SessionManagerWithFixedTime());

        TestRouteResult setRouteResult = testRoute(route_fixed)
            .run(HttpRequest.GET("/set"));
        HttpCookie token1 = getRefreshTokenCookieValues(setRouteResult.response());
        HttpCookie session1 = getSessionDataCookieValues(setRouteResult.response());

        TestRouteResult touchReqRouteResult = testRoute(route_fixed_plus30s)
            .run(HttpRequest.GET("/touchReq")
                .addHeader(Cookie.create(refreshTokenCookieName, token1.value()))
                .addHeader(Cookie.create(sessionDataCookieName, session1.value()))
            );

        HttpCookie token2 = getRefreshTokenCookieValues(touchReqRouteResult.response());
        HttpCookie session2 = getSessionDataCookieValues(touchReqRouteResult.response());

        touchReqRouteResult.assertStatusCode(StatusCodes.OK);
        touchReqRouteResult.assertEntity(SESSION);

        // The session should be modified with a new expiry date
        Assert.assertNotEquals(session1.value(), session2.value());

        // But the refresh token token should remain the same; no new token should be set
        Assert.assertNull(token2);

        // 70 seconds from the initial session, only the touched one should work
        TestRouteResult touchReqRouteResultAfter70 = testRoute(route_fixed_plus70s)
            .run(HttpRequest.GET("/touchReq")
                .addHeader(Cookie.create(refreshTokenCookieName, token1.value()))
                .addHeader(Cookie.create(sessionDataCookieName, session2.value())) // this session did not expire, it was touched
            );

        touchReqRouteResultAfter70.assertStatusCode(StatusCodes.OK);
        touchReqRouteResultAfter70.assertEntity(SESSION);

        TestRouteResult touchReqRouteResultAfter70Expired = testRoute(route_fixed_plus70s)
            .run(HttpRequest.GET("/touchReq")
                .addHeader(Cookie.create(sessionDataCookieName, session1.value())) // this session did expire, it was not touched
            );

        touchReqRouteResultAfter70Expired.assertStatusCode(StatusCodes.FORBIDDEN);

        // When sending the expired session and refresh token token, a new session should start
        TestRouteResult touchReqRouteResultAfter70Renewed = testRoute(route_fixed_plus70s)
            .run(HttpRequest.GET("/touchReq")
                .addHeader(Cookie.create(refreshTokenCookieName, token1.value()))
                .addHeader(Cookie.create(sessionDataCookieName, session1.value())) // this session did expire, it was not touched, but a refresh token was sent, to renew the session
            );

        HttpCookie token3 = getRefreshTokenCookieValues(touchReqRouteResultAfter70Renewed.response());
        HttpCookie session3 = getSessionDataCookieValues(touchReqRouteResultAfter70Renewed.response());

        touchReqRouteResultAfter70Renewed.assertStatusCode(StatusCodes.OK);
        touchReqRouteResultAfter70Renewed.assertEntity(SESSION);

        // session3 should have new expiry date
        Assert.assertNotEquals(session1.value(), session3.value());
        // new token should be generated
        Assert.assertNotNull(token3.value());

    }

    @Test
    public void shouldTouchTheSession_KeepingTheRefreshTokenIntact_UsingHeaders() {
        final Route route_fixed = createRoute(HeaderST, getExpiring60SessionManagerWithFixedTime());
        final Route route_fixed_plus30s = createRoute(HeaderST, getExpiring60Plus30SessionManagerWithFixedTime());
        final Route route_fixed_plus70s = createRoute(HeaderST, getExpiring60Plus70SessionManagerWithFixedTime());

        TestRouteResult setRouteResult = testRoute(route_fixed)
            .run(HttpRequest.GET("/set"));
        HttpHeader token1 = getRefreshTokenHeaderValues(setRouteResult.response());
        HttpHeader session1 = getSessionDataHeaderValues(setRouteResult.response());

        TestRouteResult touchReqRouteResult = testRoute(route_fixed_plus30s)
            .run(HttpRequest.GET("/touchReq")
                .addHeader(RawHeader.create(getRefreshTokenHeaderName, token1.value()))
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
                .addHeader(RawHeader.create(getRefreshTokenHeaderName, token1.value()))
                .addHeader(RawHeader.create(getSessionDataHeaderName, session2.value())) // this session did not expire, it was touched
            );

        touchReqRouteResultAfter70.assertStatusCode(StatusCodes.OK);
        touchReqRouteResultAfter70.assertEntity(SESSION);

        TestRouteResult touchReqRouteResultAfter70Expired = testRoute(route_fixed_plus70s)
            .run(HttpRequest.GET("/touchReq")
                .addHeader(RawHeader.create(getSessionDataHeaderName, session1.value())) // this session did expire, it was not touched
            );

        touchReqRouteResultAfter70Expired.assertStatusCode(StatusCodes.FORBIDDEN);

        // When sending the expired session and refresh token, a new session should start
        TestRouteResult touchReqRouteResultAfter70Renewed = testRoute(route_fixed_plus70s)
            .run(HttpRequest.GET("/touchReq")
                .addHeader(RawHeader.create(getRefreshTokenHeaderName, token1.value()))
                .addHeader(RawHeader.create(getSessionDataHeaderName, session1.value())) // this session did expire, it was not touched, but a refresh token was sent, to renew the session
            );

        HttpHeader token3 = getRefreshTokenHeaderValues(touchReqRouteResultAfter70Renewed.response());
        HttpHeader session3 = getSessionDataHeaderValues(touchReqRouteResultAfter70Renewed.response());

        touchReqRouteResultAfter70Renewed.assertStatusCode(StatusCodes.OK);
        touchReqRouteResultAfter70Renewed.assertEntity(SESSION);

        Assert.assertNotEquals(session1.value(), session3.value());
        // new token should be generated
        Assert.assertNotNull(token3);
    }

    @Test
    public void shouldReturnCreatedFromTokenWhenTokenIsSent() {
        // given
        final Route route = createRoute(CookieST);

        //and
        TestRouteResult setRouteResult = testRoute(route)
            .run(HttpRequest.GET("/set"));
        HttpCookie refreshToken = getRefreshTokenCookieValues(setRouteResult.response());
        
        /* 2nd request */
        // when
        TestRouteResult fullResult = testRoute(route)
            .run(
                HttpRequest.GET("/full").addHeader(Cookie.create(refreshTokenCookieName, refreshToken.value()))
            );

        // then
        fullResult.assertStatusCode(StatusCodes.OK);
        fullResult.assertEntity("CreatedFromToken(my session object)");
    }

    @Test
    public void shouldReturnCorruptWhenRefreshableSetButSessionCookieIsSent() {
        // given
        final Route route = createRoute(CookieST);

        //and
        TestRouteResult setRouteResult = testRoute(route)
            .run(HttpRequest.GET("/set"));
        HttpCookie refreshToken = getRefreshTokenCookieValues(setRouteResult.response());
        
        /* 2nd request */
        // whenCreatedFromToken(my session object)
        TestRouteResult fullResult = testRoute(route)
            .run(
                HttpRequest.GET("/full").addHeader(Cookie.create(sessionDataCookieName, refreshToken.value()))
            );

        // then
        fullResult.assertStatusCode(StatusCodes.OK);
        fullResult.assertEntity("Corrupt(java.lang.ArrayIndexOutOfBoundsException: 1)");
    }

    @Test
    public void shouldReturnTokenNotFoundWhenNoTokenSend() {
        // given
        final Route route = createRoute(CookieST);

        //and
        TestRouteResult setRouteResult = testRoute(route)
            .run(HttpRequest.GET("/set"));
        HttpCookie refreshToken = getRefreshTokenCookieValues(setRouteResult.response());

        testRoute(route)
            .run(HttpRequest.GET("/invalidate")
                .addHeader(Cookie.create(refreshTokenCookieName, refreshToken.value()))
            );

        TestRouteResult fullResult = testRoute(route)
            .run(
                HttpRequest.GET("/full").addHeader(Cookie.create(refreshTokenCookieName, refreshToken.value()))
            );

        // then
        fullResult.assertStatusCode(StatusCodes.OK);
        fullResult.assertEntity("TokenNotFound");
    }

}