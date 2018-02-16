package com.softwaremill.session

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.AuthorizationFailedRejection
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.softwaremill.session.SessionDirectives._
import com.softwaremill.session.SessionOptions._
import org.scalatest.{FlatSpec, Matchers}

class RefreshableTest extends FlatSpec with ScalatestRouteTest with Matchers with MultipleTransportTest {

  import TestData._

  implicit val storage = new InMemoryRefreshTokenStorage[Map[String, String]] {
    override def log(msg: String) = println(msg)
  }

  def createRoutes(using: TestUsingTransport)(implicit manager: SessionManager[Map[String, String]]) = get {
    path("set") {
      setSession(refreshable, using.setSessionTransport, Map("k1" -> "v1")) { complete(StatusCodes.OK) }
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
        invalidateSession(refreshable, using.getSessionTransport) { complete(StatusCodes.OK) }
      }
  }

  "Using cookies" should "set the refresh token cookie to expire" in {
    Get("/set") ~> createRoutes(TestUsingCookies) ~> check {
      responseAs[String] should be("OK")

      TestUsingCookies.cookiesMap.get(TestUsingCookies.refreshTokenCookieName).flatMap(_.maxAge)
        .getOrElse(0L) should be > (60L * 60L * 24L * 29)
    }
  }

  List(TestUsingCookies, TestUsingHeaders).foreach { using =>
    val p = s"Using ${using.transportName}"
    def routes(implicit manager: SMan) = createRoutes(using)(manager)

    p should "set both the session and refresh token" in {
      Get("/set") ~> routes ~> check {
        responseAs[String] should be("OK")

        using.getSession should be('defined)
        using.getRefreshToken should be('defined)
      }
    }

    p should "set a new refresh token when the session is set again" in {
      Get("/set") ~> routes ~> check {
        val Some(token1) = using.getRefreshToken

        Get("/set") ~>
          routes ~>
          check {
            val Some(token2) = using.getRefreshToken
            token1 should not be (token2)
          }
      }
    }

    p should "read an optional session when both the session and refresh token are set" in {
      Get("/set") ~> routes ~> check {
        val Some(session) = using.getSession
        val Some(token) = using.getRefreshToken

        Get("/getOpt") ~>
          addHeader(using.setSessionHeader(session)) ~>
          addHeader(using.setRefreshTokenHeader(token)) ~>
          routes ~>
          check {
            responseAs[String] should be("Some(Map(k1 -> v1))")
          }
      }
    }

    p should "read an optional session when only the session is set" in {
      Get("/set") ~> routes ~> check {
        val Some(session) = using.getSession

        Get("/getOpt") ~>
          addHeader(using.setSessionHeader(session)) ~>
          routes ~>
          check {
            responseAs[String] should be("Some(Map(k1 -> v1))")
          }
      }
    }

    p should "set a new session after the session is re-created" in {
      Get("/set") ~> routes ~> check {
        val Some(token1) = using.getRefreshToken
        val session1 = using.getSession
        session1 should be('defined)

        Get("/getOpt") ~>
          addHeader(using.setRefreshTokenHeader(token1)) ~>
          routes ~>
          check {
            val session2 = using.getSession
            session2 should be('defined)
            session2 should not be (session1)
          }
      }
    }

    p should "read an optional session when none is set" in {
      Get("/getOpt") ~> routes ~> check {
        responseAs[String] should be("None")
      }
    }

    p should "read an optional session when only the refresh token is set (re-create the session)" in {
      Get("/set") ~> routes ~> check {
        val Some(token) = using.getRefreshToken

        Get("/getOpt") ~>
          addHeader(using.setRefreshTokenHeader(token)) ~>
          routes ~>
          check {
            responseAs[String] should be("Some(Map(k1 -> v1))")
          }
      }
    }

    p should "set a new refresh token after the session is re-created" in {
      Get("/set") ~> routes ~> check {
        val Some(token1) = using.getRefreshToken

        Get("/getOpt") ~>
          addHeader(using.setRefreshTokenHeader(token1)) ~>
          routes ~>
          check {
            val Some(token2) = using.getRefreshToken
            token1 should not be (token2)
          }
      }
    }

    p should "read a required session when both the session and refresh token are set" in {
      Get("/set") ~> routes ~> check {
        val Some(session) = using.getSession
        val Some(token) = using.getRefreshToken

        Get("/getReq") ~>
          addHeader(using.setSessionHeader(session)) ~>
          addHeader(using.setRefreshTokenHeader(token)) ~>
          routes ~>
          check {
            responseAs[String] should be("Map(k1 -> v1)")
          }
      }
    }

    p should "invalidate a session" in {
      Get("/set") ~> routes ~> check {
        val Some(session) = using.getSession
        val Some(token) = using.getRefreshToken

        Get("/invalidate") ~>
          addHeader(using.setSessionHeader(session)) ~>
          addHeader(using.setRefreshTokenHeader(token)) ~>
          routes ~>
          check {
            using.isSessionExpired should be (true)
            using.isRefreshTokenExpired should be (true)
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

    p should "reject the request if the refresh token is invalid" in {
      Get("/getReq") ~> addHeader(using.setRefreshTokenHeader("invalid")) ~> routes ~> check {
        rejection should be(AuthorizationFailedRejection)
      }
    }

    p should "touch the session, keeping the refresh token token intact" in {
      Get("/set") ~> routes(manager_expires60_fixedTime) ~> check {
        val Some(session1) = using.getSession
        val Some(token1) = using.getRefreshToken

        Get("/touchReq") ~>
          addHeader(using.setSessionHeader(session1)) ~>
          addHeader(using.setRefreshTokenHeader(token1)) ~>
          routes(manager_expires60_fixedTime_plus30s) ~>
          check {
            responseAs[String] should be("Map(k1 -> v1)")

            val Some(session2) = using.getSession
            val token2Opt = using.getRefreshToken

            // The session should be modified with a new expiry date
            session1 should not be (session2)

            // But the refresh token token should remain the same; no new token should be set
            token2Opt should be(None)

            // 70 seconds from the initial session, only the touched one should work
            Get("/touchReq") ~>
              addHeader(using.setSessionHeader(session2)) ~>
              addHeader(using.setRefreshTokenHeader(token1)) ~>
              routes(manager_expires60_fixedTime_plus70s) ~>
              check {
                responseAs[String] should be("Map(k1 -> v1)")
              }
            Get("/touchReq") ~>
              addHeader(using.setSessionHeader(session1)) ~>
              routes(manager_expires60_fixedTime_plus70s) ~>
              check {
                rejection should be(AuthorizationFailedRejection)
              }
            // When sending the expired session and refresh token token, a new session should start
            Get("/touchReq") ~>
              addHeader(using.setSessionHeader(session1)) ~>
              addHeader(using.setRefreshTokenHeader(token1)) ~>
              routes(manager_expires60_fixedTime_plus70s) ~>
              check {
                responseAs[String] should be("Map(k1 -> v1)")

                val Some(session3) = using.getSession
                val token3Opt = using.getRefreshToken

                // new token should be generated
                session1 should not be (session3)
                token3Opt should be ('defined)
              }
          }
      }
    }
  }
}