package com.softwaremill.example.session

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import com.softwaremill.session.SessionEndpoints._
import com.softwaremill.session.TapirSessionOptions._
import com.softwaremill.session.SessionResult._
import com.softwaremill.session._
import com.typesafe.scalalogging.StrictLogging
import sttp.tapir._
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.akkahttp.AkkaHttpServerInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter

import scala.concurrent.Future
import scala.io.StdIn

object VariousSessionsTapir extends App with StrictLogging {
  implicit val system: ActorSystem = ActorSystem("example")

  import system.dispatcher

  val sessionConfig = SessionConfig.default(
    "c05ll3lesrinf39t7mc5h6un6r0c69lgfno69dsak3vabeqamouq4328cuaekros401ajdpkh60rrtpd8ro24rbuqmgtnd1ebag6ljnb65i8a55d482ok7o0nch0bfbe")
  implicit val sessionManager: SessionManager[MyScalaSession] = new SessionManager[MyScalaSession](sessionConfig)
  implicit val refreshTokenStorage: RefreshTokenStorage[MyScalaSession] =
    new InMemoryRefreshTokenStorage[MyScalaSession] {
      def log(msg: String): Unit = logger.info(msg)
    }

  val secret: ServerEndpoint[Any, Future] =
    requiredSession(oneOff, usingCookies).get
      .in("secret")
      .out(stringBody)
      .serverLogicSuccess(_ => _ => Future.successful("treasure"))

  val open: ServerEndpoint[Any, Future] =
    optionalSession(oneOff, usingCookies).get
      .in("open")
      .out(stringBody)
      .serverLogicSuccess(session => _ => Future.successful("small treasure"))

  val detail: ServerEndpoint[Any, Future] =
    session(oneOff, usingCookies, None).get
      .in("detail")
      .out(stringBody)
      .serverLogicSuccess(sessionResult =>
        _ =>
          Future.successful({
            sessionResult match {
              case Decoded(_)          => "decoded"
              case DecodedLegacy(_)    => "decoded legacy"
              case CreatedFromToken(_) => "created from token"
              case NoSession           => "no session"
              case TokenNotFound       => "token not found"
              case Expired             => "expired"
              case Corrupt(_)          => "corrupt"
            }
          }))

  val endpoints: List[ServerEndpoint[Any, Future]] =
    List(
      secret,
      open,
      detail
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
