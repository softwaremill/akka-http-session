package com.softwaremill.pekkohttpsession

import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import SessionDirectives._
import SessionOptions._
import org.scalatest._
import matchers.should._
import org.scalatest.flatspec.AnyFlatSpec

class OneOffSetRefreshableGetTest extends AnyFlatSpec with ScalatestRouteTest with Matchers with MultipleTransportTest {

  import TestData._

  implicit val storage: InMemoryRefreshTokenStorage[Map[String, String]] = new InMemoryRefreshTokenStorage[Map[String, String]] {
    override def log(msg: String) = println(msg)
  }

  def createRoutes(`using`: TestUsingTransport)(implicit manager: SessionManager[Map[String, String]]) = get {
    path("set") {
      setSession(oneOff, using.setSessionTransport, Map("k1" -> "v1")) {
        complete { "ok" }
      }
    } ~
      path("getOpt") {
        optionalSession(refreshable, using.getSessionTransport) { session =>
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

  List(TestUsingCookies, TestUsingHeaders).foreach { usingValue =>
    val p = s"Using ${usingValue.transportName}"
    def routes(implicit manager: SMan) = createRoutes(usingValue)(manager)

    p should "read an optional session when only the session is set" in {
      Get("/set") ~> routes ~> check {
        val Some(session) = usingValue.getSession

        Get("/getOpt") ~>
          addHeader(usingValue.setSessionHeader(session)) ~>
          routes ~>
          check {
            responseAs[String] should be("Some(Map(k1 -> v1))")
          }
      }
    }

    p should "invalidate a session" in {
      Get("/set") ~> routes ~> check {
        val Some(session) = usingValue.getSession

        Get("/invalidate") ~>
          addHeader(usingValue.setSessionHeader(session)) ~>
          routes ~>
          check {
            usingValue.isSessionExpired should be(true)
            usingValue.getRefreshToken should be(None)
          }
      }
    }

    p should "touch the session, without setting a refresh token" in {
      Get("/set") ~> routes(manager_expires60_fixedTime) ~> check {
        val Some(session1) = usingValue.getSession

        Get("/touchReq") ~>
          addHeader(usingValue.setSessionHeader(session1)) ~>
          routes(manager_expires60_fixedTime_plus30s) ~>
          check {
            responseAs[String] should be("Map(k1 -> v1)")

            val Some(session2) = usingValue.getSession
            val token2Opt = usingValue.getRefreshToken

            // The session should be modified with a new expiry date
            session1 should not be (session2)

            // No refresh token should be set
            token2Opt should be(None)

            // 70 seconds from the initial session, only the touched one should work
            Get("/touchReq") ~>
              addHeader(usingValue.setSessionHeader(session2)) ~>
              routes(manager_expires60_fixedTime_plus70s) ~>
              check {
                responseAs[String] should be("Map(k1 -> v1)")
                usingValue.getRefreshToken should be(None)
              }
          }
      }
    }
  }
}
