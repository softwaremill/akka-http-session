package com.softwaremill.example.session

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import com.softwaremill.session.CsrfEndpoints._
import com.softwaremill.session.TapirCsrfOptions._
import com.softwaremill.session.SessionEndpoints._
import com.softwaremill.session.TapirSessionOptions._
import com.softwaremill.session._
import com.typesafe.scalalogging.StrictLogging
import sttp.model.headers.WWWAuthenticateChallenge
import sttp.tapir.EndpointInput.AuthType
import sttp.tapir.{EndpointInput, auth, stringBody}
import sttp.tapir.model.UsernamePassword
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.akkahttp.AkkaHttpServerInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter

import scala.concurrent.Future
import scala.io.StdIn

object SetSessionTapir extends App with StrictLogging {
  implicit val system: ActorSystem = ActorSystem("example")

  import system.dispatcher

  val sessionConfig = SessionConfig.default(
    "c05ll3lesrinf39t7mc5h6un6r0c69lgfno69dsak3vabeqamouq4328cuaekros401ajdpkh60rrtpd8ro24rbuqmgtnd1ebag6ljnb65i8a55d482ok7o0nch0bfbe")
  implicit val sessionManager: SessionManager[MyScalaSession] = new SessionManager[MyScalaSession](sessionConfig)
  implicit val refreshTokenStorage: RefreshTokenStorage[MyScalaSession] =
    new InMemoryRefreshTokenStorage[MyScalaSession] {
      def log(msg: String): Unit = logger.info(msg)
    }

  def myAuth: EndpointInput.Auth[UsernamePassword, AuthType.Http] =
    auth.basic[UsernamePassword](WWWAuthenticateChallenge.basic("example"))

  implicit def f: UsernamePassword => Option[MyScalaSession] = up => Some(MyScalaSession(up.username))

  val login: ServerEndpoint[Any, Future] =
    setNewCsrfToken(checkHeader) {
      setSessionWithAuth(refreshable, usingCookies){
        myAuth
      }
      // equivalent to
      //      setSession(refreshable, usingCookies) {
      //        setSessionEndpoint {
      //          endpoint.securityIn(myAuth)
      //        }
      //      }
    }.post
      .in("api")
      .in("do_login")
      .out(stringBody)
      .serverLogicSuccess(maybeSession => _ => Future.successful("Hello " + maybeSession.map(_.username).getOrElse("")))

  val endpoints: List[ServerEndpoint[Any, Future]] = List(login)

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
