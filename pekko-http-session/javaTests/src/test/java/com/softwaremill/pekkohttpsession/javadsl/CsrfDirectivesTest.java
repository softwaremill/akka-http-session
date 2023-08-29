package com.softwaremill.pekkohttpsession.javadsl;

import org.apache.pekko.http.javadsl.model.FormData;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.HttpResponse;
import org.apache.pekko.http.javadsl.model.StatusCodes;
import org.apache.pekko.http.javadsl.model.headers.Cookie;
import org.apache.pekko.http.javadsl.model.headers.HttpCookie;
import org.apache.pekko.http.javadsl.model.headers.RawHeader;
import org.apache.pekko.http.javadsl.server.Route;
import org.apache.pekko.http.javadsl.testkit.TestRouteResult;
import org.apache.pekko.japi.Pair;
import com.softwaremill.pekkohttpsession.CsrfCheckMode;
import com.softwaremill.pekkohttpsession.SessionContinuity;
import com.softwaremill.pekkohttpsession.SetSessionTransport;
import org.junit.Assert;
import org.junit.Test;

public class CsrfDirectivesTest extends HttpSessionAwareDirectivesTest {

    protected Route buildRoute(HttpSessionAwareDirectives<String> testDirectives, SessionContinuity<String> oneOff, SessionContinuity<String> refreshable, SetSessionTransport sessionTransport, CsrfCheckMode<String> csrfCheckMode) {
        return route(
            testDirectives.randomTokenCsrfProtection(csrfCheckMode, () ->
                route(
                    get(() ->
                        path("site", () ->
                            complete("ok")
                        )
                    ),
                    post(() ->
                        route(
                            path("login", () ->
                                testDirectives.setNewCsrfToken(csrfCheckMode, () ->
                                    complete("ok"))),
                            path("transfer_money", () ->
                                complete("ok")
                            )
                        )
                    )
                )
            )
        );

    }

    @Test
    public void shouldSetTheCsrfCookieOnTheFirstGetRequestOnly() {
        // given
        final Route route = createCsrfRouteWithCheckHeaderMode();

        // when
        TestRouteResult testRouteResult = testRoute(route)
            .run(HttpRequest.GET("/site"));

        // then
        testRouteResult
            .assertStatusCode(StatusCodes.OK)
            .assertEntity("ok");

        // and
        HttpResponse response = testRouteResult.response();
        HttpCookie csrfCookie = getCsrfTokenCookieValues(response);
        Assert.assertNotNull(csrfCookie.value());

        /* second request */
        // when
        TestRouteResult testRouteResult2 = testRoute(route)
            .run(HttpRequest.GET("/site")
                .addHeader(Cookie.create(csrfCookieName, csrfCookie.value()))
            );

        // then
        testRouteResult2
            .assertStatusCode(StatusCodes.OK)
            .assertEntity("ok");

        // and
        HttpResponse response2 = testRouteResult2.response();
        HttpCookie cookieValues2 = getCsrfTokenCookieValues(response2);
        Assert.assertNull(cookieValues2);

    }

    @Test
    public void shouldRejectRequestsIfTheCsrfCookieDoesNotMatchTheHeaderValue() {
        // given
        final Route route = createCsrfRouteWithCheckHeaderMode();

        // when
        TestRouteResult testRouteResult = testRoute(route)
            .run(HttpRequest.GET("/site"));

        // then
        testRouteResult
            .assertStatusCode(StatusCodes.OK);

        // and
        HttpCookie csrfCookie = getCsrfTokenCookieValues(testRouteResult.response());

        /* second request */
        // when
        TestRouteResult testRouteResult2 = testRoute(route)
            .run(HttpRequest.POST("/transfer_money")
                .addHeader(Cookie.create(csrfCookieName, csrfCookie.value()))
                .addHeader(RawHeader.create(csrfSubmittedName, "something else"))
            );

        // then
        testRouteResult2
            .assertStatusCode(StatusCodes.FORBIDDEN);
    }

    @Test
    public void shouldRejectRequestsIfTheCsrfCookieIsNotSet() {
        // given
        final Route route = createCsrfRouteWithCheckHeaderMode();

        // when
        TestRouteResult testRouteResult = testRoute(route)
            .run(HttpRequest.GET("/site"));

        // then
        testRouteResult
            .assertStatusCode(StatusCodes.OK);

        /* second request */
        // when
        TestRouteResult testRouteResult2 = testRoute(route)
            .run(HttpRequest.POST("/transfer_money"));

        // then
        testRouteResult2
            .assertStatusCode(StatusCodes.FORBIDDEN);


    }

    @Test
    public void shouldRejectRequestsIfTheCsrfCookieAndTheHeaderAreEmpty() {
        // given
        final Route route = createCsrfRouteWithCheckHeaderMode();

        // when
        TestRouteResult testRouteResult = testRoute(route)
          .run(HttpRequest.GET("/site"));

        // then
        testRouteResult
          .assertStatusCode(StatusCodes.OK);

        /* second request */
        // when
        TestRouteResult testRouteResult2 = testRoute(route)
          .run(HttpRequest.POST("/transfer_money")
            .addHeader(Cookie.create(csrfCookieName, ""))
            .addHeader(RawHeader.create(csrfSubmittedName, ""))
          );

        // then
        testRouteResult2
          .assertStatusCode(StatusCodes.FORBIDDEN);

    }

    @Test
    public void shouldAcceptRequestsIfTheCsrfCookieMatchesTheHeaderValue() {
        // given
        final Route route = createCsrfRouteWithCheckHeaderMode();

        // when
        TestRouteResult testRouteResult = testRoute(route)
            .run(HttpRequest.GET("/site"));

        // then
        testRouteResult
            .assertStatusCode(StatusCodes.OK);

        // and
        HttpCookie csrfCookie = getCsrfTokenCookieValues(testRouteResult.response());

        /* second request */
        // when
        TestRouteResult testRouteResult2 = testRoute(route)
            .run(HttpRequest.POST("/transfer_money")
                .addHeader(Cookie.create(csrfCookieName, csrfCookie.value()))
                .addHeader(RawHeader.create(csrfSubmittedName, csrfCookie.value()))
            );

        // then
        testRouteResult2
            .assertStatusCode(StatusCodes.OK)
            .assertEntity("ok");

    }

    @Test
    public void shouldAcceptRequestsIfTheCsrfCookieMatchesTheFormFieldValue() {
        // given
        final Route route = createCsrfRouteWithCheckHeaderAndFormMode();

        // when
        TestRouteResult testRouteResult = testRoute(route)
            .run(HttpRequest.GET("/site"));

        // then
        testRouteResult
            .assertStatusCode(StatusCodes.OK);

        // and
        HttpCookie csrfCookie = getCsrfTokenCookieValues(testRouteResult.response());

        /* second request */
        // when
        final FormData formData = FormData.create(
            Pair.create(csrfSubmittedName, csrfCookie.value())
        );
        TestRouteResult testRouteResult2 = testRoute(route)
            .run(HttpRequest.POST("/transfer_money").withEntity(formData.toEntity())
                .addHeader(Cookie.create(csrfCookieName, csrfCookie.value()))
            );

        // then
        testRouteResult2
            .assertStatusCode(StatusCodes.OK)
            .assertEntity("ok");
    }

    @Test
    public void shouldSetANewCsrfCookieWhenRequested() {
        // given
        final Route route = createCsrfRouteWithCheckHeaderMode();

        // when
        TestRouteResult testRouteResult = testRoute(route)
            .run(HttpRequest.GET("/site"));

        // then
        testRouteResult
            .assertStatusCode(StatusCodes.OK);

        // and
        HttpCookie csrfCookie = getCsrfTokenCookieValues(testRouteResult.response());

        /* second request */
        // when
        TestRouteResult testRouteResult2 = testRoute(route)
            .run(HttpRequest.POST("/login")
                .addHeader(Cookie.create(csrfCookieName, csrfCookie.value()))
                .addHeader(RawHeader.create(csrfSubmittedName, csrfCookie.value()))
            );

        // then
        testRouteResult2
            .assertStatusCode(StatusCodes.OK)
            .assertEntity("ok");

        // and
        HttpCookie csrfCookie2 = getCsrfTokenCookieValues(testRouteResult2.response());
        Assert.assertNotEquals(csrfCookie.value(), csrfCookie2.value());

    }

}
