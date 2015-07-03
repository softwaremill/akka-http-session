package com.softwaremill.session

import akka.http.scaladsl.model.DateTime
import akka.http.scaladsl.model.headers.{Cookie, `Set-Cookie`}
import akka.http.scaladsl.server.AuthorizationFailedRejection
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.{ShouldMatchers, FlatSpec}
import akka.http.scaladsl.server.Directives._
import com.softwaremill.session.SessionDirectives._

class SessionDirectivesTest extends FlatSpec with ScalatestRouteTest with ShouldMatchers {

  val sessionConfig = SessionConfig.default("c05ll3lesrinf39t7mc5h6un6r0c69lgfno69dsak3vabeqamouq4328cuaekros401ajdpkh60rrtpd8ro24rbuqmgtnd1ebag6ljnb65i8a55d482ok7o0nch0bfbe")
  implicit val sessionManager = new SessionManager(sessionConfig)

  val cookieName = sessionManager.sessionCookieName

  def routes(implicit sessionManager: SessionManager) = get {
    path("set") {
      setSession(Map("k1" -> "v1")) {
        complete { "ok" }
      }
    } ~
      path("getOpt") {
        optionalSession() { session =>
          complete { session.toString }
        }
      } ~
      path("getReq") {
        requiredSession() { session =>
          complete { session.toString }
        }
      } ~
      path("touchReq") {
        touchRequiredSession() { session =>
          complete { session.toString }
        }
      } ~
      path("invalidate") {
        invalidateSession() {
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

      sessionCookie.cookie.name should be (sessionManager.sessionCookieName)
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

  it should "invalide a session" in {
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
    val cfg = sessionConfig.withSessionMaxAgeSeconds(Some(60))
    val managerNow = new SessionManager(cfg) {
      override def nowMillis = 3028L * 1000L
    }
    val managerPlus_30_seconds = new SessionManager(cfg) {
      override def nowMillis = (3028L + 30L) * 1000L
    }
    val managerPlus_70_seconds = new SessionManager(cfg) {
      override def nowMillis = (3028L + 70L) * 1000L
    }

    Get("/set") ~> routes(managerNow) ~> check {
      val Some(sessionCookie1) = header[`Set-Cookie`]

      Get("/touchReq") ~> addHeader(Cookie(cookieName, sessionCookie1.cookie.value)) ~> routes(managerPlus_30_seconds) ~> check {
        responseAs[String] should be ("Map(k1 -> v1)")

        val Some(sessionCookie2) = header[`Set-Cookie`]

        // The session cookie should be modified with a new expiry date
        sessionCookie1.cookie.value should not be (sessionCookie2.cookie.value)

        // 70 seconds from the initial cookie, only the touched one should work
        Get("/touchReq") ~> addHeader(Cookie(cookieName, sessionCookie1.cookie.value)) ~> routes(managerPlus_70_seconds) ~> check {
          rejection should be (AuthorizationFailedRejection)
        }
        Get("/touchReq") ~> addHeader(Cookie(cookieName, sessionCookie2.cookie.value)) ~> routes(managerPlus_70_seconds) ~> check {
          responseAs[String] should be ("Map(k1 -> v1)")
        }
      }
    }
  }
}
