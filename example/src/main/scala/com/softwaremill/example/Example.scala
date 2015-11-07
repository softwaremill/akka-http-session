package com.softwaremill.example

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.StatusCodes._
import com.softwaremill.session._
import com.softwaremill.session.CsrfDirectives._
import com.softwaremill.session.SessionDirectives._
import com.typesafe.scalalogging.slf4j.StrictLogging

import scala.io.StdIn

object Example extends App with StrictLogging {
  implicit val system = ActorSystem("example")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  val sessionConfig = SessionConfig.default("c05ll3lesrinf39t7mc5h6un6r0c69lgfno69dsak3vabeqamouq4328cuaekros401ajdpkh60rrtpd8ro24rbuqmgtnd1ebag6ljnb65i8a55d482ok7o0nch0bfbe")
  implicit val sessionManager = new SessionManager[ExampleSession](sessionConfig)
  implicit val refreshTokenStorage = new InMemoryRefreshTokenStorage[ExampleSession] {
    def log(msg: String) = logger.info(msg)
  }

  val routes =
    path("") {
      redirect("/site/index.html", Found)
    } ~
      randomTokenCsrfProtection() {
        pathPrefix("api") {
          path("do_login") {
            post {
              entity(as[String]) { body =>
                logger.info(s"Logging in $body")

                setSession(refreshable, ExampleSession(body)) {
                  setNewCsrfToken() { ctx => ctx.complete("ok") }
                }
              }
            }
          } ~
            // This should be protected and accessible only when logged in
            path("do_logout") {
              post {
                requiredSession(refreshable) { session =>
                  invalidateSession(refreshable) { ctx =>
                    logger.info(s"Logging out $session")
                    ctx.complete("ok")
                  }
                }
              }
            } ~
            // This should be protected and accessible only when logged in
            path("current_login") {
              get {
                requiredSession(refreshable) { session => ctx =>
                  logger.info("Current session: " + session)
                  ctx.complete(session.username)
                }
              }
            }
        } ~
          pathPrefix("site") {
            getFromResourceDirectory("")
          }
      }

  val bindingFuture = Http().bindAndHandle(routes, "localhost", 8080)

  println("Server started, press enter to stop. Visit http://localhost:8080 to see the demo.")
  StdIn.readLine()

  import system.dispatcher
  bindingFuture
    .flatMap(_.unbind())
    .onComplete { _ =>
      system.shutdown()
      println("Server stopped")
    }
}

case class ExampleSession(username: String)

object ExampleSession {
  implicit def serializer: SessionSerializer[ExampleSession] = new ToMapSessionSerializer[ExampleSession] {
    private val Key = "u"
    override def serializeToMap(t: ExampleSession) = Map(Key -> t.username)
    override def deserializeFromMap(m: Map[String, String]) = ExampleSession(m(Key))
  }
}