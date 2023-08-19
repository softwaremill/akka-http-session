package com.softwaremill.pekkohttpsession

import org.apache.pekko.http.scaladsl.server.AuthorizationFailedRejection
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import SessionDirectives._
import SessionOptions._
import org.scalatest._
import matchers.should._
import org.scalatest.flatspec.AnyFlatSpec

class RefreshableTest extends AnyFlatSpec with ScalatestRouteTest with Matchers with MultipleTransportTest {

  import TestData._

  implicit val storage: InMemoryRefreshTokenStorage[Map[String, String]] = new InMemoryRefreshTokenStorage[Map[String, String]] {
    override def log(msg: String) = println(msg)
  }

  def createRoutes(`using`: TestUsingTransport)(implicit manager: SessionManager[Map[String, String]]) = get {
    path("set") {
      setSession(refreshable, using.setSessionTransport, Map("k1" -> "v1")) {
        complete { "ok" }
      }
    } ~
      path("getOpt") {
        optionalSession(refreshable, using.getSessionTransport) { session =>
          complete { session.toString }
        }
      } ~
      path("getReq") {
        requiredSession(refreshable, using.getSessionTransport) { session =>
          complete { session.toString }
        }
      } ~
      path("touchReq") {
        touchRequiredSession(refreshable, using.getSessionTransport) { session =>
          complete { session.toString }
        }
      } ~
      path("invalidate") {
        invalidateSession(refreshable, using.getSessionTransport) {
          complete { "ok" }
        }
      }
  }

  "Using cookies" should "set the refresh token cookie to expire" in {
    Get("/set") ~> createRoutes(TestUsingCookies) ~> check {
      responseAs[String] should be("ok")

      TestUsingCookies.cookiesMap
        .get(TestUsingCookies.refreshTokenCookieName)
        .flatMap(_.maxAge)
        .getOrElse(0L) should be > (60L * 60L * 24L * 29)
    }
  }

  List(TestUsingCookies, TestUsingHeaders).foreach { usingValue =>
    val p = s"Using ${usingValue.transportName}"
    def routes(implicit manager: SMan) = createRoutes(usingValue)(manager)

    p should "set both the session and refresh token" in {
      Get("/set") ~> routes ~> check {
        responseAs[String] should be("ok")

        usingValue.getSession shouldBe defined
        usingValue.countSessionHeaders should be(1)
        usingValue.getRefreshToken shouldBe defined
        usingValue.countRefreshTokenHeaders should be(1)
      }
    }

    p should "set a new refresh token when the session is set again" in {
      Get("/set") ~> routes ~> check {
        val Some(token1) = usingValue.getRefreshToken

        Get("/set") ~>
          routes ~>
          check {
            val Some(token2) = usingValue.getRefreshToken
            token1 should not be (token2)
          }
      }
    }

    p should "read an optional session when both the session and refresh token are set" in {
      Get("/set") ~> routes ~> check {
        val Some(session) = usingValue.getSession
        val Some(token) = usingValue.getRefreshToken

        Get("/getOpt") ~>
          addHeader(usingValue.setSessionHeader(session)) ~>
          addHeader(usingValue.setRefreshTokenHeader(token)) ~>
          routes ~>
          check {
            usingValue.countSessionHeaders should be(0)
            usingValue.countRefreshTokenHeaders should be(0)
            responseAs[String] should be("Some(Map(k1 -> v1))")
          }
      }
    }

    p should "read an optional session when only the session is set" in {
      Get("/set") ~> routes ~> check {
        val Some(session) = usingValue.getSession

        Get("/getOpt") ~>
          addHeader(usingValue.setSessionHeader(session)) ~>
          routes ~>
          check {
            usingValue.countSessionHeaders should be(0)
            usingValue.countRefreshTokenHeaders should be(0)
            responseAs[String] should be("Some(Map(k1 -> v1))")
          }
      }
    }

    p should "set a new session after the session is re-created" in {
      Get("/set") ~> routes ~> check {
        val Some(token1) = usingValue.getRefreshToken
        val session1 = usingValue.getSession
        session1 shouldBe defined

        Get("/getOpt") ~>
          addHeader(usingValue.setRefreshTokenHeader(token1)) ~>
          routes ~>
          check {
            usingValue.countSessionHeaders should be(1)
            usingValue.countRefreshTokenHeaders should be(1)
            val session2 = usingValue.getSession
            session2 shouldBe defined
            session2 should not be (session1)
          }
      }
    }

    p should "read an optional session when none is set" in {
      Get("/getOpt") ~> routes ~> check {
        usingValue.countSessionHeaders should be(0)
        usingValue.countRefreshTokenHeaders should be(0)
        responseAs[String] should be("None")
      }
    }

    p should "read an optional session when only the refresh token is set (re-create the session)" in {
      Get("/set") ~> routes ~> check {
        val Some(token) = usingValue.getRefreshToken

        Get("/getOpt") ~>
          addHeader(usingValue.setRefreshTokenHeader(token)) ~>
          routes ~>
          check {
            usingValue.countSessionHeaders should be(1)
            usingValue.countRefreshTokenHeaders should be(1)
            responseAs[String] should be("Some(Map(k1 -> v1))")
          }
      }
    }

    p should "set a new refresh token after the session is re-created" in {
      Get("/set") ~> routes ~> check {
        val Some(token1) = usingValue.getRefreshToken

        Get("/getOpt") ~>
          addHeader(usingValue.setRefreshTokenHeader(token1)) ~>
          routes ~>
          check {
            usingValue.countSessionHeaders should be(1)
            usingValue.countRefreshTokenHeaders should be(1)
            val Some(token2) = usingValue.getRefreshToken
            token1 should not be (token2)
          }
      }
    }

    p should "read a required session when both the session and refresh token are set" in {
      Get("/set") ~> routes ~> check {
        val Some(session) = usingValue.getSession
        val Some(token) = usingValue.getRefreshToken

        Get("/getReq") ~>
          addHeader(usingValue.setSessionHeader(session)) ~>
          addHeader(usingValue.setRefreshTokenHeader(token)) ~>
          routes ~>
          check {
            usingValue.countSessionHeaders should be(0)
            usingValue.countRefreshTokenHeaders should be(0)
            responseAs[String] should be("Map(k1 -> v1)")
          }
      }
    }

    p should "invalidate a session" in {
      Get("/set") ~> routes ~> check {
        val Some(session) = usingValue.getSession
        val Some(token) = usingValue.getRefreshToken

        Get("/invalidate") ~>
          addHeader(usingValue.setSessionHeader(session)) ~>
          addHeader(usingValue.setRefreshTokenHeader(token)) ~>
          routes ~>
          check {
            usingValue.countSessionHeaders should be(1)
            usingValue.countRefreshTokenHeaders should be(1)
            usingValue.isSessionExpired should be(true)
            usingValue.isRefreshTokenExpired should be(true)
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

    p should "reject the request if the refresh token is invalid" in {
      Get("/getReq") ~> addHeader(usingValue.setRefreshTokenHeader("invalid")) ~> routes ~> check {
        rejection should be(AuthorizationFailedRejection)
      }
    }

    p should "touch the session, keeping the refresh token token intact" in {
      Get("/set") ~> routes(manager_expires60_fixedTime) ~> check {
        val Some(session1) = usingValue.getSession
        val Some(token1) = usingValue.getRefreshToken

        Get("/touchReq") ~>
          addHeader(usingValue.setSessionHeader(session1)) ~>
          addHeader(usingValue.setRefreshTokenHeader(token1)) ~>
          routes(manager_expires60_fixedTime_plus30s) ~>
          check {
            responseAs[String] should be("Map(k1 -> v1)")

            val Some(session2) = usingValue.getSession
            val token2Opt = usingValue.getRefreshToken

            // The session should be modified with a new expiry date
            session1 should not be (session2)

            // But the refresh token token should remain the same; no new token should be set
            token2Opt should be(None)

            // 70 seconds from the initial session, only the touched one should work
            Get("/touchReq") ~>
              addHeader(usingValue.setSessionHeader(session2)) ~>
              addHeader(usingValue.setRefreshTokenHeader(token1)) ~>
              routes(manager_expires60_fixedTime_plus70s) ~>
              check {
                responseAs[String] should be("Map(k1 -> v1)")
              }
            Get("/touchReq") ~>
              addHeader(usingValue.setSessionHeader(session1)) ~>
              routes(manager_expires60_fixedTime_plus70s) ~>
              check {
                rejection should be(AuthorizationFailedRejection)
              }
            // When sending the expired session and refresh token token, a new session should start
            Get("/touchReq") ~>
              addHeader(usingValue.setSessionHeader(session1)) ~>
              addHeader(usingValue.setRefreshTokenHeader(token1)) ~>
              routes(manager_expires60_fixedTime_plus70s) ~>
              check {
                responseAs[String] should be("Map(k1 -> v1)")

                val Some(session3) = usingValue.getSession
                val token3Opt = usingValue.getRefreshToken

                // new token should be generated
                session1 should not be (session3)
                token3Opt shouldBe defined
              }
          }
      }
    }

    p should "re-create an expired session and send back new tokens without duplicate headers" in {
      Get("/set") ~> routes(manager_expires60_fixedTime) ~> check {
        val Some(session1) = usingValue.getSession
        val Some(token1) = usingValue.getRefreshToken

        Get("/touchReq") ~>
          addHeader(usingValue.setSessionHeader(session1)) ~>
          addHeader(usingValue.setRefreshTokenHeader(token1)) ~>
          routes(manager_expires60_fixedTime_plus70s) ~>
          check {
            usingValue.countSessionHeaders should be(1)
            usingValue.countRefreshTokenHeaders should be(1)

            usingValue.getSession should not be session1
            usingValue.getRefreshToken should not be token1
          }
      }
    }

    p should "touch the session and send back session without duplicate headers" in {
      Get("/set") ~> routes(manager_expires60_fixedTime) ~> check {
        val Some(session1) = usingValue.getSession
        val Some(token1) = usingValue.getRefreshToken

        Get("/touchReq") ~>
          addHeader(usingValue.setSessionHeader(session1)) ~>
          addHeader(usingValue.setRefreshTokenHeader(token1)) ~>
          routes(manager_expires60_fixedTime_plus30s) ~>
          check {
            usingValue.countSessionHeaders should be(1)
            usingValue.countRefreshTokenHeaders should be(0)

            usingValue.getSession should not be session1
            usingValue.getRefreshToken should not be defined
          }
      }
    }
  }
}
