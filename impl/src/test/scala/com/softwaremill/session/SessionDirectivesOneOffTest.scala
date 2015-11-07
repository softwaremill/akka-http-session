package com.softwaremill.session

import akka.http.scaladsl.model.DateTime
import akka.http.scaladsl.model.headers.{Cookie, `Set-Cookie`}
import akka.http.scaladsl.server.AuthorizationFailedRejection
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.{ShouldMatchers, FlatSpec}
import akka.http.scaladsl.server.Directives._
import com.softwaremill.session.SessionDirectives._

class SessionDirectivesOneOffTest extends FlatSpec with ScalatestRouteTest with ShouldMatchers {

  import TestData._
  val cookieName = sessionConfig.sessionCookieConfig.name

  def routes(implicit manager: SessionManager[Map[String, String]]) = get {
    path("set") {
      setSession(oneOff, Map("k1" -> "v1")) {
        complete { "ok" }
      }
    } ~
      path("getOpt") {
        optionalSession(oneOff) { session =>
          complete { session.toString }
        }
      } ~
      path("getReq") {
        requiredSession(oneOff) { session =>
          complete { session.toString }
        }
      } ~
      path("touchReq") {
        touchRequiredSession(oneOff) { session =>
          complete { session.toString }
        }
      } ~
      path("invalidate") {
        invalidateSession(oneOff) {
          complete { "ok" }
        }
      }
  }

  it should "set the session cookie" in {
    Get("/set") ~> routes ~> check {
      responseAs[String] should be ("ok")

      val sessionCookieOption = header[`Set-Cookie`]
      sessionCookieOption should be ('defined)
      val Some(sessionCookie) = sessionCookieOption

      sessionCookie.cookie.name should be (cookieName)
    }
  }

  it should "read an optional session when the session cookie is set" in {
    Get("/set") ~> routes ~> check {
      val Some(sessionCookie) = header[`Set-Cookie`]

      Get("/getOpt") ~> addHeader(Cookie(cookieName, sessionCookie.cookie.value)) ~> routes ~> check {
        responseAs[String] should be ("Some(Map(k1 -> v1))")
      }
    }
  }

  it should "read an optional session when the session cookie is not set" in {
    Get("/getOpt") ~> routes ~> check {
      responseAs[String] should be ("None")
    }
  }

  it should "read a required session when the session cookie is set" in {
    Get("/set") ~> routes ~> check {
      val Some(sessionCookie) = header[`Set-Cookie`]

      Get("/getReq") ~> addHeader(Cookie(cookieName, sessionCookie.cookie.value)) ~> routes ~> check {
        responseAs[String] should be ("Map(k1 -> v1)")
      }
    }
  }

  it should "invalidate a session" in {
    Get("/set") ~> routes ~> check {
      val Some(sessionCookie1) = header[`Set-Cookie`]

      Get("/invalidate") ~> addHeader(Cookie(cookieName, sessionCookie1.cookie.value)) ~> routes ~> check {
        responseAs[String] should be ("ok")

        val Some(sessionCookie2) = header[`Set-Cookie`]
        sessionCookie2.cookie.expires should be (Some(DateTime.MinValue))
      }
    }
  }

  it should "reject the request if the session cookie is not set" in {
    Get("/getReq") ~> routes ~> check {
      rejection should be (AuthorizationFailedRejection)
    }
  }

  it should "reject the request if the session cookie is invalid" in {
    Get("/getReq") ~> addHeader(Cookie(cookieName, "invalid")) ~> routes ~> check {
      rejection should be (AuthorizationFailedRejection)
    }
  }

  it should "touch the session" in {
    Get("/set") ~> routes(manager_expires60_fixedTime) ~> check {
      val Some(sessionCookie1) = header[`Set-Cookie`]

      Get("/touchReq") ~>
        addHeader(Cookie(cookieName, sessionCookie1.cookie.value)) ~>
        routes(manager_expires60_fixedTime_plus30s) ~>
        check {
          responseAs[String] should be ("Map(k1 -> v1)")

          val Some(sessionCookie2) = header[`Set-Cookie`]

          // The session cookie should be modified with a new expiry date
          sessionCookie1.cookie.value should not be (sessionCookie2.cookie.value)

          // 70 seconds from the initial cookie, only the touched one should work
          Get("/touchReq") ~> addHeader(Cookie(cookieName, sessionCookie1.cookie.value)) ~>
            routes(manager_expires60_fixedTime_plus70s) ~>
            check {
              rejection should be (AuthorizationFailedRejection)
            }
          Get("/touchReq") ~> addHeader(Cookie(cookieName, sessionCookie2.cookie.value)) ~>
            routes(manager_expires60_fixedTime_plus70s) ~>
            check {
              responseAs[String] should be ("Map(k1 -> v1)")
            }
        }
    }
  }
}
