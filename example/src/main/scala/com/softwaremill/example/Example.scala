package com.softwaremill.example

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.StatusCodes._
import com.softwaremill.session._
import com.softwaremill.session.SessionDirectives._
import com.typesafe.scalalogging.slf4j.StrictLogging
import scala.collection.mutable
import scala.concurrent.Future

import scala.io.StdIn

object Example extends App with StrictLogging {
  implicit val system = ActorSystem("example")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  val sessionConfig = SessionConfig.default("c05ll3lesrinf39t7mc5h6un6r0c69lgfno69dsak3vabeqamouq4328cuaekros401ajdpkh60rrtpd8ro24rbuqmgtnd1ebag6ljnb65i8a55d482ok7o0nch0bfbe")
  implicit val sessionManager = new SessionManager[ExampleSession](sessionConfig)
  implicit val rememberMeStorage = new RememberMeStorage[ExampleSession] {
    case class Store(username: String, tokenHash: String, expires: Long)
    private val store = mutable.Map[String, Store]()
    override def lookup(selector: String) = {
      Future.successful {
        val r = store.get(selector).map(s => RememberMeLookupResult[ExampleSession](s.tokenHash, s.expires,
          () => ExampleSession(s.username)))
        logger.info(s"Looking up token for selector: $selector, found: ${r.isDefined}")
        r
      }
    }
    override def store(data: RememberMeData[ExampleSession]) = {
      logger.info(s"Storing token for selector: ${data.selector}, user: ${data.forSession.username}, " +
        s"expires: ${data.expires}, now: ${System.currentTimeMillis()}")
      Future.successful(store.put(data.selector, Store(data.forSession.username, data.tokenHash, data.expires)))
    }
    override def remove(selector: String) = {
      logger.info(s"Removing token for selector: $selector")
      Future.successful(store.remove(selector))
    }
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

                setPersistentSession(ExampleSession(body)) {
                  setNewCsrfToken() { ctx => ctx.complete("ok") }
                }
              }
            }
          } ~
            // This should be protected and accessible only when logged in
            path("do_logout") {
              post {
                requiredPersistentSession() { session =>
                  invalidatePersistentSession() { ctx =>
                    logger.info(s"Logging out $session")
                    ctx.complete("ok")
                  }
                }
              }
            } ~
            // This should be protected and accessible only when logged in
            path("current_login") {
              get {
                requiredPersistentSession() { session => ctx =>
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

  println("Server started, press enter to stop")
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