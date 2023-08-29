package com.softwaremill.pekkohttpsession

import org.apache.pekko.http.scaladsl.model.headers.`Set-Cookie`
import org.apache.pekko.http.scaladsl.server.AuthorizationFailedRejection
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import SessionDirectives._
import SessionOptions._
import org.scalatest._
import matchers.should._
import org.scalatest.flatspec.AnyFlatSpec

class OneOffTest extends AnyFlatSpec with ScalatestRouteTest with Matchers with MultipleTransportTest {

  import TestData._

  def createRoutes(`using`: TestUsingTransport)(implicit manager: SMan) = get {
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
      sessionCookieOption shouldBe defined
      val Some(sessionCookie) = sessionCookieOption

      sessionCookie.cookie.name should be(TestUsingCookies.sessionCookieName)
    }
  }

  List(TestUsingCookies, TestUsingHeaders).foreach { usingValue =>
    val p = s"Using ${usingValue.transportName}"
    def routes(implicit manager: SMan) = createRoutes(usingValue)(manager)

    p should "set the session" in {
      Get("/set") ~> routes ~> check {
        responseAs[String] should be("ok")

        val sessionOption = usingValue.getSession
        sessionOption shouldBe defined

        usingValue.isSessionExpired should be(false)
      }
    }

    p should "read an optional session when the session is set" in {
      Get("/set") ~> routes ~> check {
        val Some(s) = usingValue.getSession

        Get("/getOpt") ~> addHeader(usingValue.setSessionHeader(s)) ~> routes ~> check {
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
        val Some(s) = usingValue.getSession

        Get("/getReq") ~> addHeader(usingValue.setSessionHeader(s)) ~> routes ~> check {
          responseAs[String] should be("Map(k1 -> v1)")
        }
      }
    }

    p should "reject the request if the expiry is tampered with" in {
      Get("/set") ~> routes ~> check {
        val Some(s) = usingValue.getSession
        val Array(sig, exp, data) = s.split("-")
        val tamperedSession = s"$sig-${exp.toLong + 1}-$data"

        Get("/getReq") ~> addHeader(usingValue.setSessionHeader(tamperedSession)) ~> routes ~> check {
          rejection should be(AuthorizationFailedRejection)
        }
      }
    }

    p should "invalidate a session" in {
      Get("/set") ~> routes ~> check {
        val Some(s1) = usingValue.getSession

        Get("/invalidate") ~> addHeader(usingValue.setSessionHeader(s1)) ~> routes ~> check {
          responseAs[String] should be("ok")

          usingValue.isSessionExpired should be(true)
        }
      }
    }

    p should "reject the request if the session is not set" in {
      Get("/getReq") ~> routes ~> check {
        rejection should be(AuthorizationFailedRejection)
      }
    }

    p should "reject the request if the session is invalid" in {
      Get("/getReq") ~> addHeader(usingValue.setSessionHeader("invalid")) ~> routes ~> check {
        rejection should be(AuthorizationFailedRejection)
      }
    }

    p should "touch the session" in {
      Get("/set") ~> routes(manager_expires60_fixedTime) ~> check {
        val Some(s1) = usingValue.getSession

        Get("/touchReq") ~>
          addHeader(usingValue.setSessionHeader(s1)) ~>
          routes(manager_expires60_fixedTime_plus30s) ~>
          check {
            responseAs[String] should be("Map(k1 -> v1)")

            val Some(s2) = usingValue.getSession

            // The session should be modified with a new expiry date
            s1 should not be (s2)

            // 70 seconds from the initial request, only the touched one should work
            Get("/touchReq") ~> addHeader(usingValue.setSessionHeader(s1)) ~>
              routes(manager_expires60_fixedTime_plus70s) ~>
              check {
                rejection should be(AuthorizationFailedRejection)
              }
            Get("/touchReq") ~> addHeader(usingValue.setSessionHeader(s2)) ~>
              routes(manager_expires60_fixedTime_plus70s) ~>
              check {
                responseAs[String] should be("Map(k1 -> v1)")
              }
          }
      }
    }

    p should "reject v0.5.1 session without migration config" in {
      Get("/set") ~> routes ~> check {
        val data = Map("k1" -> "v1")
        val now = System.currentTimeMillis()
        val legacySession = Legacy.encodeV0_5_1(data, now, sessionConfig)

        Get("/getReq") ~> addHeader(usingValue.setSessionHeader(legacySession)) ~> routes ~> check {
          rejection should be(AuthorizationFailedRejection)
        }
      }
    }

    p should "migrate v0.5.1 session with migration config" in {
      Get("/set") ~> routes ~> check {
        val data = Map("k1" -> "v1")
        val now = System.currentTimeMillis()
        val legacySession = Legacy.encodeV0_5_1(data, now, sessionConfig)

        Get("/getReq") ~> addHeader(usingValue.setSessionHeader(legacySession)) ~> routes(manager_tokenMigrationFromV0_5_1) ~> check {
          usingValue.getSession shouldBe defined
          responseAs[String] should be(data.toString)
        }
      }
    }

    p should "reject v0.5.2 session without migration config" in {
      Get("/set") ~> routes ~> check {
        val data = Map("k1" -> "v1")
        val now = System.currentTimeMillis()
        val legacySession = Legacy.encodeV0_5_2(data, now, sessionConfig)

        Get("/getReq") ~> addHeader(usingValue.setSessionHeader(legacySession)) ~> routes ~> check {
          rejection should be(AuthorizationFailedRejection)
        }
      }
    }

    p should "migrate v0.5.2 session with migration config" in {
      Get("/set") ~> routes ~> check {
        val data = Map("k1" -> "v1")
        val now = System.currentTimeMillis()
        val legacySession = Legacy.encodeV0_5_2(data, now, sessionConfig)

        Get("/getReq") ~> addHeader(usingValue.setSessionHeader(legacySession)) ~> routes(manager_tokenMigrationFromV0_5_2) ~> check {
          usingValue.getSession shouldBe defined
          responseAs[String] should be(data.toString)
        }
      }
    }
  }
}
