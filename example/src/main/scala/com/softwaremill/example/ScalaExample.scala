package com.softwaremill.example

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import com.softwaremill.example.session.MyScalaSession
import com.softwaremill.session.CsrfDirectives.{randomTokenCsrfProtection, setNewCsrfToken}
import com.softwaremill.session.CsrfOptions.checkHeader
import com.softwaremill.session.SessionDirectives.{invalidateSession, requiredSession, setSession}
import com.softwaremill.session.SessionOptions.{refreshable, usingCookies}
import com.softwaremill.session.{InMemoryRefreshTokenStorage, SessionConfig, SessionManager}
import com.typesafe.scalalogging.StrictLogging

import scala.util.{Failure, Success}

object ScalaExample extends App with StrictLogging {
  implicit val system = ActorSystem("example")
  implicit val materializer = ActorMaterializer()
  implicit val ec: scala.concurrent.ExecutionContextExecutor = system.dispatcher
  implicit val refreshTokenStorage = new InMemoryRefreshTokenStorage[MyScalaSession] {
    def log(msg: String) = logger.info(msg)
  }
  implicit val sessionManager = new SessionManager[MyScalaSession](sessionConfig)
  val sessionConfig = SessionConfig.default("c05ll3lesrinf39t7mc5h6un6r0c69lgfno69dsak3vabeqamouq4328cuaekros401ajdpkh60rrtpd8ro24rbuqmgtnd1ebag6ljnb65i8a55d482ok7o0nch0bfbe")
  val myRequiredSession = requiredSession(refreshable, usingCookies)
  val myInvalidateSession = invalidateSession(refreshable, usingCookies)
  val routes =
    path("") {
      redirect("/site/index.html", StatusCodes.Found)
    } ~
      randomTokenCsrfProtection(checkHeader) {
        pathPrefix("api") {
          path("do_login") {
            post {
              entity(as[String]) { body =>
                logger.info(s"Logging in $body")

                mySetSession(MyScalaSession(body)) {
                  setNewCsrfToken(checkHeader) {
                    completeOK
                  }
                }
              }
            }
          } ~
            // This should be protected and accessible only when logged in
            path("do_logout") {
              post {
                myRequiredSession { session =>
                  myInvalidateSession {
                    logger.info(s"Logging out $session")
                    completeOK
                  }
                }
              }
            } ~
            // This should be protected and accessible only when logged in
            path("current_login") {
              get {
                myRequiredSession { session =>
                  logger.info("Current session: " + session)
                  complete(session.username)
                }
              }
            }
        } ~
          pathPrefix("site") {
            getFromResourceDirectory("")
          }
      }
  val bindingFuture = Http().bindAndHandle(routes, httpHost, httpPort)

  def mySetSession(v: MyScalaSession) = setSession(refreshable, usingCookies, v)

  def httpHost = "localhost"

  def httpPort = 8080

  bindingFuture.onComplete {
    case Success(Http.ServerBinding(localAddress)) => logger.info("Listening on {}", localAddress)
    case Failure(cause) =>
      logger.error( /*cause,*/ s"Terminating, because can't bind to http://$httpHost:$httpPort!")
      system.terminate()
  }

}