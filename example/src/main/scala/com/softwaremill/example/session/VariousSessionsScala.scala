package com.softwaremill.example.session

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import com.softwaremill.session.SessionDirectives._
import com.softwaremill.session.SessionOptions.{oneOff, refreshable, usingCookies}
import com.softwaremill.session.SessionResult._
import com.softwaremill.session.{InMemoryRefreshTokenStorage, SessionConfig, SessionManager}
import com.typesafe.scalalogging.StrictLogging

import scala.util.{Failure, Success}

object VariousSessionsScala extends App with StrictLogging {
  implicit val system = ActorSystem("example")
  implicit val materializer = ActorMaterializer()

  import system.dispatcher

  val sessionConfig = SessionConfig.default("c05ll3lesrinf39t7mc5h6un6r0c69lgfno69dsak3vabeqamouq4328cuaekros401ajdpkh60rrtpd8ro24rbuqmgtnd1ebag6ljnb65i8a55d482ok7o0nch0bfbe")
  implicit val sessionManager = new SessionManager[MyScalaSession](sessionConfig)
  implicit val refreshTokenStorage = new InMemoryRefreshTokenStorage[MyScalaSession] {
    def log(msg: String) = logger.info(msg)
  }

  def mySetSession(v: MyScalaSession) = setSession(refreshable, usingCookies, v)

  val myRequiredSession = requiredSession(refreshable, usingCookies)
  val myInvalidateSession = invalidateSession(refreshable, usingCookies)

  val routes =
    path("secret") {
      get {
        // type: Long, or whatever the T parameter is
        requiredSession(oneOff, usingCookies) { session =>
          complete {
            "treasure"
          }
        }
      }
    } ~
      path("open") {
        get {
          // type: Option[Long] (Option[T])
          optionalSession(oneOff, usingCookies) { session =>
            complete {
              "small treasure"
            }
          }
        }
      } ~
      path("detail") {
        get {
          // type: SessionResult[Long] (SessionResult[T])
          // which can be: Decoded, DecodedLegacy, CreatedFromToken, Expired, Corrupt, NoSession
          session(oneOff, usingCookies) {
            case Decoded(session) => complete("decoded")
            case DecodedLegacy(session) => complete("decoded legacy")
            case CreatedFromToken(session) => complete("created from token")
            case NoSession => complete("no session")
            case TokenNotFound => complete("token not found")
            case Expired => complete("expired")
            case Corrupt(exc) => complete("corrupt")
            case _ => complete("unknown session result")
          }
        }
      }

  val bindingFuture = Http().bindAndHandle(routes, httpHost, httpPort)

  def httpHost = "localhost"

  def httpPort = 8080

  bindingFuture.onComplete {
    case Success(Http.ServerBinding(localAddress)) => logger.info("Listening on {}", localAddress)
    case Failure(cause) =>
      logger.error( /*cause,*/ s"Terminating, because can't bind to http://$httpHost:$httpPort!")
      system.terminate()
  }
}

