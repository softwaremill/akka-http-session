package com.softwaremill.session

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.softwaremill.session.SessionEndpoints._
import com.softwaremill.session.TapirSessionOptions._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should._
import sttp.tapir._
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.akkahttp.AkkaHttpServerInterpreter

import scala.concurrent.Future

class OneOffSetRefreshableGetTapirTest
    extends AnyFlatSpec
    with ScalatestRouteTest
    with Matchers
    with MultipleTransportTest {

  import TestData._

  implicit val storage: RefreshTokenStorage[Map[String, String]] =
    new InMemoryRefreshTokenStorage[Map[String, String]] {
      override def log(msg: String): Unit = println(msg)
    }

  def setEndpoint(using: TestUsingTransport)(
      implicit manager: SessionManager[Map[String, String]]): ServerEndpoint[Any, Future] =
    setSession(oneOff, using.setSessionTransport) {
      endpoint
    }(_ => Some(Map("k1" -> "v1")))
      .in("set")
      .out(stringBody)
      .serverLogicSuccess(_ => _ => Future.successful(("ok")))

  def getOptEndpoint(using: TestUsingTransport)(
      implicit manager: SessionManager[Map[String, String]]): ServerEndpoint[Any, Future] =
    optionalSession(refreshable, using.getSessionTransport)
      .in("getOpt")
      .out(stringBody)
      .serverLogicSuccess(session => _ => Future.successful(session.toString))

  def touchReqEndpoint(using: TestUsingTransport)(
      implicit manager: SessionManager[Map[String, String]]): ServerEndpoint[Any, Future] =
    touchRequiredSession(refreshable, using.getSessionTransport)
      .in("touchReq")
      .out(stringBody)
      .serverLogicSuccess(session => _ => Future.successful(session.toString))

  def invalidateEndpoint(using: TestUsingTransport)(
      implicit manager: SessionManager[Map[String, String]]): ServerEndpoint[Any, Future] =
    invalidateSession(refreshable /*FIXME, using.getSessionTransport*/ ) {
      endpoint.serverSecurityLogicSuccessWithOutput(_ => Future.successful(((), ())))
    }.in("invalidate")
      .out(stringBody)
      .serverLogicSuccess(_ => _ => Future.successful("ok"))

  def createRoutes(using: TestUsingTransport)(implicit manager: SessionManager[Map[String, String]]): Route =
    get {
      AkkaHttpServerInterpreter().toRoute(
        List(
          setEndpoint(using),
          getOptEndpoint(using),
          touchReqEndpoint(using),
          invalidateEndpoint(using)
        )
      )
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
            using.isSessionExpired should be(true)
            using.getRefreshToken should be(None)
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
            token2Opt should be(None)

            // 70 seconds from the initial session, only the touched one should work
            Get("/touchReq") ~>
              addHeader(using.setSessionHeader(session2)) ~>
              routes(manager_expires60_fixedTime_plus70s) ~>
              check {
                responseAs[String] should be("Map(k1 -> v1)")
                using.getRefreshToken should be(None)
              }
          }
      }
    }
  }
}
