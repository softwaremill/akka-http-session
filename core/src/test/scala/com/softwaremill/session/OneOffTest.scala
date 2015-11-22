package com.softwaremill.session

import akka.http.scaladsl.model.headers.`Set-Cookie`
import akka.http.scaladsl.server.AuthorizationFailedRejection
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.softwaremill.session.SessionDirectives._
import org.scalatest.{FlatSpec, ShouldMatchers}

class OneOffTest extends FlatSpec with ScalatestRouteTest with ShouldMatchers with MultipleTransportTest {

  import TestData._

  def createRoutes(using: TestUsingTransport)(implicit manager: SMan) = get {
    path("set") {
      setSession(oneOff, using.setSessionTransport, Map("k1" -> "v1")) {
        complete {
          "ok"
        }
      }
    } ~
      path("getOpt") {
        optionalSession(oneOff, using.getSessionTransport) { session =>
          complete {
            session.toString
          }
        }
      } ~
      path("getReq") {
        requiredSession(oneOff, using.getSessionTransport) { session =>
          complete {
            session.toString
          }
        }
      } ~
      path("touchReq") {
        touchRequiredSession(oneOff, using.getSessionTransport) { session =>
          complete {
            session.toString
          }
        }
      } ~
      path("invalidate") {
        invalidateSession(oneOff, using.getSessionTransport) {
          complete {
            "ok"
          }
        }
      }
  }

  "Using cookies" should "set the correct session cookie name" in {
    Get("/set") ~> createRoutes(TestUsingCookies) ~> check {
      val sessionCookieOption = header[`Set-Cookie`]
      sessionCookieOption should be('defined)
      val Some(sessionCookie) = sessionCookieOption

      sessionCookie.cookie.name should be(TestUsingCookies.sessionCookieName)
    }
  }

  List(TestUsingCookies, TestUsingHeaders).foreach { using =>
    val p = s"Using ${using.transportName}"
    def routes(implicit manager: SMan) = createRoutes(using)(manager)

    p should "set the session" in {
      Get("/set") ~> routes ~> check {
        responseAs[String] should be("ok")

        val sessionOption = using.getSession
        sessionOption should be('defined)

        using.isSessionExpired should be (false)
      }
    }

    p should "read an optional session when the session is set" in {
      Get("/set") ~> routes ~> check {
        val Some(s) = using.getSession

        Get("/getOpt") ~> addHeader(using.setSessionHeader(s)) ~> routes ~> check {
          responseAs[String] should be("Some(Map(k1 -> v1))")
        }
      }
    }

    p should "read an optional session when the session is not set" in {
      Get("/getOpt") ~> routes ~> check {
        responseAs[String] should be("None")
      }
    }

    p should "read a required session when the session is set" in {
      Get("/set") ~> routes ~> check {
        val Some(s) = using.getSession

        Get("/getReq") ~> addHeader(using.setSessionHeader(s)) ~> routes ~> check {
          responseAs[String] should be("Map(k1 -> v1)")
        }
      }
    }

    p should "invalidate a session" in {
      Get("/set") ~> routes ~> check {
        val Some(s1) = using.getSession

        Get("/invalidate") ~> addHeader(using.setSessionHeader(s1)) ~> routes ~> check {
          responseAs[String] should be("ok")

          using.isSessionExpired should be (true)
        }
      }
    }

    p should "reject the request if the session is not set" in {
      Get("/getReq") ~> routes ~> check {
        rejection should be(AuthorizationFailedRejection)
      }
    }

    p should "reject the request if the session is invalid" in {
      Get("/getReq") ~> addHeader(using.setSessionHeader("invalid")) ~> routes ~> check {
        rejection should be(AuthorizationFailedRejection)
      }
    }

    p should "touch the session" in {
      Get("/set") ~> routes(manager_expires60_fixedTime) ~> check {
        val Some(s1) = using.getSession

        Get("/touchReq") ~>
          addHeader(using.setSessionHeader(s1)) ~>
          routes(manager_expires60_fixedTime_plus30s) ~>
          check {
            responseAs[String] should be("Map(k1 -> v1)")

            val Some(s2) = using.getSession

            // The session should be modified with a new expiry date
            s1 should not be (s2)

            // 70 seconds from the initial request, only the touched one should work
            Get("/touchReq") ~> addHeader(using.setSessionHeader(s1)) ~>
              routes(manager_expires60_fixedTime_plus70s) ~>
              check {
                rejection should be(AuthorizationFailedRejection)
              }
            Get("/touchReq") ~> addHeader(using.setSessionHeader(s2)) ~>
              routes(manager_expires60_fixedTime_plus70s) ~>
              check {
                responseAs[String] should be("Map(k1 -> v1)")
              }
          }
      }
    }
  }
}
