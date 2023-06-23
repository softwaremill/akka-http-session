package com.softwaremill.session

import akka.http.scaladsl.model.{FormData, StatusCodes}
import akka.http.scaladsl.model.headers.{Cookie, `Set-Cookie`}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import com.softwaremill.session.CsrfEndpoints._
import com.softwaremill.session.TapirCsrfOptions._
import sttp.tapir._
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.akkahttp.AkkaHttpServerInterpreter

import scala.concurrent.Future

class CsrfEndpointsTest extends AnyFlatSpec with ScalatestRouteTest with Matchers {

  import TestData._
  val cookieName: String = sessionConfig.csrfCookieConfig.name
  implicit val csrfCheckMode: TapirCsrfCheckMode[Map[String, String]] = checkHeader

  def siteEndpoint[T](implicit manager: SessionManager[T],
                      checkMode: TapirCsrfCheckMode[T]): ServerEndpoint[Any, Future] = {
    hmacTokenCsrfProtection(checkMode) {
      endpoint.serverSecurityLogicSuccessWithOutput(_ => Future.successful(((), ())))
    }.in("site")
      .out(stringBody)
      .get
      .serverLogicSuccess(_ => _ => Future.successful(("ok")))
  }

  def loginEndpoint[T](implicit manager: SessionManager[T],
                       checkMode: TapirCsrfCheckMode[T]): ServerEndpoint[Any, Future] = {
    hmacTokenCsrfProtection(checkMode) {
      setNewCsrfToken(checkMode)
    }.in("login")
      .out(stringBody)
      .post
      .serverLogicSuccess(_ => _ => Future.successful(("ok")))
  }

  def transferMoneyEndpoint[T](implicit manager: SessionManager[T],
                               checkMode: TapirCsrfCheckMode[T]): ServerEndpoint[Any, Future] = {
    hmacTokenCsrfProtection(checkMode) {
      endpoint.serverSecurityLogicSuccessWithOutput(_ => Future.successful(((), ())))
    }.in("transfer_money")
      .out(stringBody)
      .post
      .serverLogicSuccess(_ => _ => Future.successful(("ok")))
  }

  def routes[T](implicit manager: SessionManager[T], checkMode: TapirCsrfCheckMode[T]): Route =
    AkkaHttpServerInterpreter().toRoute(
      List(
        siteEndpoint,
        loginEndpoint,
        transferMoneyEndpoint
      )
    )

  it should "set the csrf cookie on the first get request only" in {
    Get("/site") ~> routes ~> check {
      responseAs[String] should be("ok")

      val csrfCookieOption = header[`Set-Cookie`]
      csrfCookieOption should be('defined)
      val Some(csrfCookie) = csrfCookieOption

      csrfCookie.cookie.name should be(cookieName)

      Get("/site") ~> addHeader(Cookie(cookieName, csrfCookie.cookie.value)) ~> routes ~> check {
        responseAs[String] should be("ok")

        header[`Set-Cookie`] should be(None)
      }
    }
  }

  it should "reject requests if the csrf cookie doesn't match the header value" in {
    Get("/site") ~> routes ~> check {
      responseAs[String] should be("ok")
      val Some(csrfCookie) = header[`Set-Cookie`]

      Post("/transfer_money") ~>
        addHeader(Cookie(cookieName, csrfCookie.cookie.value)) ~>
        addHeader(sessionConfig.csrfSubmittedName, "something else") ~>
        routes ~>
        check {
          assert(response.status == StatusCodes.Unauthorized)
        }
    }
  }

  it should "reject requests if the csrf cookie isn't set" in {
    Get("/site") ~> routes ~> check {
      responseAs[String] should be("ok")

      Post("/transfer_money") ~>
        routes ~>
        check {
          assert(response.status == StatusCodes.Unauthorized)
        }
    }
  }

  it should "reject requests if the csrf cookie and the header are empty" in {
    Get("/site") ~> routes ~> check {
      responseAs[String] should be("ok")

      Post("/transfer_money") ~>
        addHeader(Cookie(cookieName, "")) ~>
        addHeader(sessionConfig.csrfSubmittedName, "") ~>
        routes ~>
        check {
          assert(response.status == StatusCodes.Unauthorized)
        }
    }
  }

  it should "reject requests if the csrf cookie and the header contain illegal value" in {
    Get("/site") ~> routes ~> check {
      responseAs[String] should be("ok")

      Post("/transfer_money") ~>
        addHeader(Cookie(cookieName, "x")) ~>
        addHeader(sessionConfig.csrfSubmittedName, "x") ~>
        routes ~>
        check {
          assert(response.status == StatusCodes.Unauthorized)
        }
    }
  }

  it should "reject requests if the csrf cookie and the header contain structurally correct but incorrectly hashed value" in {
    Get("/site") ~> routes ~> check {
      responseAs[String] should be("ok")

      val wrong = s"wrong${System.currentTimeMillis()}"
      Post("/transfer_money") ~>
        addHeader(Cookie(cookieName, wrong)) ~>
        addHeader(sessionConfig.csrfSubmittedName, wrong) ~>
        routes ~>
        check {
          assert(response.status == StatusCodes.Unauthorized)
        }
    }
  }

  it should "accept requests if the csrf cookie matches the header value" in {
    Get("/site") ~> routes ~> check {
      responseAs[String] should be("ok")
      val Some(csrfCookie) = header[`Set-Cookie`]

      Post("/transfer_money") ~>
        addHeader(Cookie(cookieName, csrfCookie.cookie.value)) ~>
        addHeader(sessionConfig.csrfSubmittedName, csrfCookie.cookie.value) ~>
        routes ~>
        check {
          responseAs[String] should be("ok")
        }
    }
  }

  it should "accept requests if the csrf cookie matches the form field value" in {
    val testRoutes = routes(manager, checkHeaderAndForm)
    Get("/site") ~> testRoutes ~> check {
      responseAs[String] should be("ok")
      val Some(csrfCookie) = header[`Set-Cookie`]

      Post("/transfer_money", FormData(sessionConfig.csrfSubmittedName -> csrfCookie.cookie.value)) ~>
        addHeader(Cookie(cookieName, csrfCookie.cookie.value)) ~>
        testRoutes ~>
        check {
          responseAs[String] should be("ok")
        }
    }
  }

  it should "set a new csrf cookie when requested" in {
    Get("/site") ~> routes ~> check {
      responseAs[String] should be("ok")
      val Some(csrfCookie1) = header[`Set-Cookie`]

      Post("/login") ~>
        addHeader(Cookie(cookieName, csrfCookie1.cookie.value)) ~>
        addHeader(sessionConfig.csrfSubmittedName, csrfCookie1.cookie.value) ~>
        routes ~>
        check {
          responseAs[String] should be("ok")
          val Some(csrfCookie2) = header[`Set-Cookie`]

          csrfCookie1.cookie.value should not be csrfCookie2.cookie.value
        }
    }
  }
}
