package com.softwaremill.example.session

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import com.softwaremill.session.SessionEndpoints._
import com.softwaremill.session.TapirSessionOptions._
import com.softwaremill.session._
import com.typesafe.scalalogging.StrictLogging
import sttp.tapir._
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.akkahttp.AkkaHttpServerInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter

import scala.concurrent.Future
import scala.io.StdIn

object SessionInvalidationTapir extends App with StrictLogging {
  implicit val system: ActorSystem = ActorSystem("example")

  import system.dispatcher

  val sessionConfig = SessionConfig.default(
    "c05ll3lesrinf39t7mc5h6un6r0c69lgfno69dsak3vabeqamouq4328cuaekros401ajdpkh60rrtpd8ro24rbuqmgtnd1ebag6ljnb65i8a55d482ok7o0nch0bfbe")
  implicit val sessionManager: SessionManager[MyScalaSession] = new SessionManager[MyScalaSession](sessionConfig)
  implicit val refreshTokenStorage: RefreshTokenStorage[MyScalaSession] =
    new InMemoryRefreshTokenStorage[MyScalaSession] {
      def log(msg: String): Unit = logger.info(msg)
    }

  val logout: ServerEndpoint[Any, Future] =
    invalidateSession(refreshable, usingCookies) {
      requiredSession(refreshable, usingCookies)
    }.post
      .in("logout")
      .out(stringBody)
      .serverLogicSuccess(session =>
        _ => {
          logger.info(s"Logging out $session")
          Future.successful("ok")
      })

  val endpoints: List[ServerEndpoint[Any, Future]] =
    List(
      logout
    )

  val swaggerEndpoints: List[ServerEndpoint[Any, Future]] =
    SwaggerInterpreter().fromEndpoints(endpoints.map(_.endpoint), "example", "v1.0")

  val routes = AkkaHttpServerInterpreter().toRoute(endpoints ++ swaggerEndpoints)

  val bindingFuture = Http().newServerAt("localhost", 8080).bind(routes)

  println("Server started, press enter to stop. Visit http://localhost:8080/docs to see the swagger documentation.")
  StdIn.readLine()

  bindingFuture
    .flatMap(_.unbind())
    .onComplete { _ =>
      system.terminate()
      println("Server stopped")
    }
}
