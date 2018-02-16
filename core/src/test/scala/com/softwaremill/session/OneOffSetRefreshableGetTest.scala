package com.softwaremill.session

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.softwaremill.session.SessionDirectives._
import com.softwaremill.session.SessionOptions._
import org.scalatest.{FlatSpec, Matchers}

class OneOffSetRefreshableGetTest extends FlatSpec with ScalatestRouteTest with Matchers with MultipleTransportTest {

  import TestData._

  implicit val storage = new InMemoryRefreshTokenStorage[Map[String, String]] {
    override def log(msg: String) = println(msg)
  }

  def createRoutes(using: TestUsingTransport)(implicit manager: SessionManager[Map[String, String]]) = get {
    path("set") {
      setSession(oneOff, using.setSessionTransport, Map("k1" -> "v1")) {
        complete(StatusCodes.OK)
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
          complete(StatusCodes.OK)
        }
      }
  }

  List(TestUsingCookies, TestUsingHeaders).foreach { using =>
    val p = s"Using ${using.transportName}"
    def routes(implicit manager: SMan) = createRoutes(using)(manager)

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

    p should "invalidate a session" in {
      Get("/set") ~> routes ~> check {
        val Some(session) = using.getSession

        Get("/invalidate") ~>
          addHeader(using.setSessionHeader(session)) ~>
          routes ~>
          check {
            using.isSessionExpired should be (true)
            using.getRefreshToken should be (None)
          }
      }
    }

    p should "touch the session, without setting a refresh token" in {
      Get("/set") ~> routes(manager_expires60_fixedTime) ~> check {
        val Some(session1) = using.getSession

        Get("/touchReq") ~>
          addHeader(using.setSessionHeader(session1)) ~>
          routes(manager_expires60_fixedTime_plus30s) ~>
          check {
            responseAs[String] should be("Map(k1 -> v1)")

            val Some(session2) = using.getSession
            val token2Opt = using.getRefreshToken

            // The session should be modified with a new expiry date
            session1 should not be (session2)

            // No refresh token should be set
            token2Opt should be (None)

            // 70 seconds from the initial session, only the touched one should work
            Get("/touchReq") ~>
              addHeader(using.setSessionHeader(session2)) ~>
              routes(manager_expires60_fixedTime_plus70s) ~>
              check {
                responseAs[String] should be ("Map(k1 -> v1)")
                using.getRefreshToken should be (None)
              }
          }
      }
    }
  }
}