package com.softwaremill.session

import akka.http.scaladsl.model.FormData
import akka.http.scaladsl.model.headers.{Cookie, `Set-Cookie`}
import akka.http.scaladsl.server.AuthorizationFailedRejection
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.softwaremill.session.CsrfDirectives._
import com.softwaremill.session.CsrfOptions._
import org.scalatest.{Matchers, FlatSpec}

class CsrfDirectivesTest extends FlatSpec with ScalatestRouteTest with Matchers {

  import TestData._
  val cookieName = sessionConfig.csrfCookieConfig.name
  implicit val csrfCheckMode = checkHeader

  def routes[T](implicit manager: SessionManager[T], checkMode: CsrfCheckMode[T]) =
    randomTokenCsrfProtection(checkMode) {
      get {
        path("site") {
          complete {
            "ok"
          }
        }
      } ~
        post {
          path("login") {
            setNewCsrfToken(checkMode) {
              complete { "ok" }
            }
          } ~
            path("transfer_money") {
              complete { "ok" }
            }
        }
    }

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
          rejections should be(List(AuthorizationFailedRejection))
        }
    }
  }

  it should "reject requests if the csrf cookie isn't set" in {
    Get("/site") ~> routes ~> check {
      responseAs[String] should be("ok")

      Post("/transfer_money") ~>
        routes ~>
        check {
          rejections should be(List(AuthorizationFailedRejection))
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
          rejections should be(List(AuthorizationFailedRejection))
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

          csrfCookie1.cookie.value should not be (csrfCookie2.cookie.value)
        }
    }
  }
}
