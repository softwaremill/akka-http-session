package com.softwaremill.example.session

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives.{path, post}
import akka.stream.ActorMaterializer
import com.softwaremill.example.completeOK
import com.softwaremill.session.{InMemoryRefreshTokenStorage, SessionConfig, SessionManager}
import com.softwaremill.session.SessionDirectives.{setSession,requiredSession,invalidateSession}
import com.softwaremill.session.SessionOptions.{refreshable, usingCookies}
import com.typesafe.scalalogging.StrictLogging

import scala.util.{Failure, Success}

object SessionInvalidationScala extends App with StrictLogging {
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
    path("logout") {
      post {
        myRequiredSession { session =>
          myInvalidateSession {
            logger.info(s"Logging out $session")
            completeOK
          }
        }
      }
    }

  val bindingFuture = Http().bindAndHandle(routes, httpHost, httpPort)

  def httpHost = "localhost"

  def httpPort = 8080

  bindingFuture.onComplete {
    case Success(Http.ServerBinding(localAddress)) => logger.info("Listening on {}", localAddress)
    case Failure(cause) =>
      logger.error(/*cause,*/ s"Terminating, because can't bind to http://$httpHost:$httpPort!")
      system.terminate()
  }

}