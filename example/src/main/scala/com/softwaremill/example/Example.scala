package com.softwaremill.example

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.StatusCodes._
import com.typesafe.scalalogging.slf4j.StrictLogging

import scala.io.StdIn

object Example extends App with StrictLogging {
  implicit val system = ActorSystem("example")
  implicit val materializer = ActorMaterializer()

  var currentLogin: Option[String] = None

  val routes =
    path("") {
      redirect("/site/index.html", Found)
    } ~
      pathPrefix("api") {
        path("do_login") {
          entity(as[String]) { body =>
            post { ctx =>
              logger.info(s"Logging in $body")
              currentLogin = Some(body)
              ctx.complete("ok")
            }
          }
        } ~
          // This should be protected and accessible only when logged in
          path("do_logout") {
            post { ctx =>
              logger.info(s"Logging out $currentLogin")
              currentLogin = None
              ctx.complete("ok")
            }
          } ~
          // This should be protected and accessible only when logged in
          path("current_login") {
            get { ctx =>
              currentLogin match {
                case None => ctx.complete(Unauthorized)
                case Some(login) => ctx.complete(login)
              }
            }
          }
      } ~
      pathPrefix("site") {
        getFromResourceDirectory("")
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
