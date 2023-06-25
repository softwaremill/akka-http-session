package com.softwaremill.session

import sttp.monad.FutureMonad
import sttp.tapir._
import sttp.tapir.server.PartialServerEndpointWithSecurityOutput

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

sealed trait TapirSessionContinuity[T] {
  implicit def manager: SessionManager[T]

  implicit def ec: ExecutionContext

  def options2session(values: Seq[Option[String]]): Option[T] = //TODO find a more appropriate name
    values.flatMap(extractSession).headOption

  def extractSession(maybeValue: Option[String]): Option[T]

  def setSession[INPUT](st: SetSessionTransport)(endpoint: Endpoint[INPUT, Unit, Unit, Unit, Any])(
      implicit f: INPUT => Option[T]
  ): PartialServerEndpointWithSecurityOutput[(INPUT, Seq[Option[String]]),
                                             Option[
                                               T
                                             ],
                                             Unit,
                                             Unit,
                                             Seq[Option[String]],
                                             Unit,
                                             Any,
                                             Future]

  def session(
      st: GetSessionTransport,
      required: Option[Boolean]
  ): PartialServerEndpointWithSecurityOutput[Seq[Option[String]],
                                             SessionResult[T],
                                             Unit,
                                             Unit,
                                             Seq[
                                               Option[String]
                                             ],
                                             Unit,
                                             Any,
                                             Future]

  def optionalSession(st: GetSessionTransport): PartialServerEndpointWithSecurityOutput[Seq[
                                                                                          Option[String]
                                                                                        ],
                                                                                        Option[T],
                                                                                        Unit,
                                                                                        Unit,
                                                                                        Seq[Option[String]],
                                                                                        Unit,
                                                                                        Any,
                                                                                        Future] = {
    val partial = session(st, Some(false))
    partial.endpoint
      .out(partial.securityOutput)
      .serverSecurityLogicWithOutput { inputs =>
        partial.securityLogic(new FutureMonad())(inputs).map {
          case Left(l)  => Left(l)
          case Right(r) => Right((r._1, r._2.toOption))
        }
      }
  }

  def requiredSession(st: GetSessionTransport): PartialServerEndpointWithSecurityOutput[Seq[
                                                                                          Option[String]
                                                                                        ],
                                                                                        T,
                                                                                        Unit,
                                                                                        Unit,
                                                                                        Seq[Option[String]],
                                                                                        Unit,
                                                                                        Any,
                                                                                        Future] = {
    val partial = session(st, Some(true))
    partial.endpoint
      .out(partial.securityOutput)
      .serverSecurityLogicWithOutput { inputs =>
        partial.securityLogic(new FutureMonad())(inputs).map {
          case Left(l) => Left(l)
          case Right(r) =>
            r._2.toOption match {
              case Some(session) => Right((r._1, session))
              case _             => Left(())
            }
        }
      }
  }

  def invalidateSession[
      SECURITY_INPUT,
      PRINCIPAL
  ](st: GetSessionTransport)(
      body: => PartialServerEndpointWithSecurityOutput[
        SECURITY_INPUT,
        PRINCIPAL,
        Unit,
        Unit,
        _,
        Unit,
        Any,
        Future
      ]
  ): PartialServerEndpointWithSecurityOutput[
    (SECURITY_INPUT, Seq[Option[String]]),
    PRINCIPAL,
    Unit,
    Unit,
    Seq[Option[String]],
    Unit,
    Any,
    Future
  ]

  def touchSession(
      st: GetSessionTransport,
      required: Option[Boolean]
  ): PartialServerEndpointWithSecurityOutput[Seq[Option[String]],
                                             SessionResult[T],
                                             Unit,
                                             Unit,
                                             Seq[
                                               Option[String]
                                             ],
                                             Unit,
                                             Any,
                                             Future]

  def touchOptionalSession(st: GetSessionTransport): PartialServerEndpointWithSecurityOutput[Seq[
                                                                                               Option[String]
                                                                                             ],
                                                                                             Option[T],
                                                                                             Unit,
                                                                                             Unit,
                                                                                             Seq[Option[String]],
                                                                                             Unit,
                                                                                             Any,
                                                                                             Future] = {
    val partial = touchSession(st, Some(false))
    partial.endpoint
      .out(partial.securityOutput)
      .serverSecurityLogicWithOutput { inputs =>
        partial.securityLogic(new FutureMonad())(inputs).map {
          case Left(l)  => Left(l)
          case Right(r) => Right((r._1, r._2.toOption))
        }
      }
  }

  def touchRequiredSession(st: GetSessionTransport): PartialServerEndpointWithSecurityOutput[Seq[
                                                                                               Option[String]
                                                                                             ],
                                                                                             T,
                                                                                             Unit,
                                                                                             Unit,
                                                                                             Seq[Option[String]],
                                                                                             Unit,
                                                                                             Any,
                                                                                             Future] = {
    val partial = touchSession(st, Some(true))
    partial.endpoint
      .out(partial.securityOutput)
      .serverSecurityLogicWithOutput { inputs =>
        partial.securityLogic(new FutureMonad())(inputs).map {
          case Left(l) => Left(l)
          case Right(r) =>
            r._2.toOption match {
              case Some(session) => Right((r._1, session))
              case _             => Left(())
            }
        }
      }
  }

}

trait OneOffTapirSessionContinuity[T] extends TapirSessionContinuity[T] {
  _: OneOffTapirSession[T] =>

  override def extractSession(maybeValue: Option[String]): Option[T] = extractOneOffSession(
    maybeValue
  )

  override def setSession[INPUT](
      st: SetSessionTransport
  )(endpoint: Endpoint[INPUT, Unit, Unit, Unit, Any])(
      implicit
      f: INPUT => Option[T]): PartialServerEndpointWithSecurityOutput[(INPUT, Seq[Option[String]]),
                                                                      Option[
                                                                        T
                                                                      ],
                                                                      Unit,
                                                                      Unit,
                                                                      Seq[Option[String]],
                                                                      Unit,
                                                                      Any,
                                                                      Future] =
    setOneOffSession(st)(endpoint)

  override def session(
      st: GetSessionTransport,
      required: Option[Boolean]
  ): PartialServerEndpointWithSecurityOutput[Seq[Option[String]],
                                             SessionResult[T],
                                             Unit,
                                             Unit,
                                             Seq[
                                               Option[String]
                                             ],
                                             Unit,
                                             Any,
                                             Future] =
    oneOffSession(st, required)

  override def invalidateSession[SECURITY_INPUT, PRINCIPAL](st: GetSessionTransport)(
      body: => PartialServerEndpointWithSecurityOutput[
        SECURITY_INPUT,
        PRINCIPAL,
        Unit,
        Unit,
        _,
        Unit,
        Any,
        Future
      ]
  ): PartialServerEndpointWithSecurityOutput[
    (SECURITY_INPUT, Seq[Option[String]]),
    PRINCIPAL,
    Unit,
    Unit,
    Seq[Option[String]],
    Unit,
    Any,
    Future
  ] =
    invalidateOneOffSession(st)(body)

  override def touchSession(
      st: GetSessionTransport,
      required: Option[Boolean]
  ): PartialServerEndpointWithSecurityOutput[Seq[Option[String]],
                                             SessionResult[T],
                                             Unit,
                                             Unit,
                                             Seq[
                                               Option[String]
                                             ],
                                             Unit,
                                             Any,
                                             Future] = touchOneOffSession(st, required)
}

trait RefreshableTapirSessionContinuity[T] extends TapirSessionContinuity[T] with Completion {
  this: RefreshableTapirSession[T] with OneOffTapirSession[T] =>

  override def extractSession(maybeValue: Option[String]): Option[T] = extractRefreshableSession(
    maybeValue
  )

  def removeToken(value: String): Try[Unit] =
    refreshable.refreshTokenManager.removeToken(value).complete()

  override def setSession[INPUT](
      st: SetSessionTransport
  )(endpoint: Endpoint[INPUT, Unit, Unit, Unit, Any])(
      implicit
      f: INPUT => Option[T]): PartialServerEndpointWithSecurityOutput[(INPUT, Seq[Option[String]]),
                                                                      Option[
                                                                        T
                                                                      ],
                                                                      Unit,
                                                                      Unit,
                                                                      Seq[Option[String]],
                                                                      Unit,
                                                                      Any,
                                                                      Future] =
    setRefreshableSession(st)(endpoint)

  override def session(
      st: GetSessionTransport,
      required: Option[Boolean]
  ): PartialServerEndpointWithSecurityOutput[Seq[Option[String]],
                                             SessionResult[T],
                                             Unit,
                                             Unit,
                                             Seq[
                                               Option[String]
                                             ],
                                             Unit,
                                             Any,
                                             Future] = refreshableSession(st, required)

  override def invalidateSession[SECURITY_INPUT, PRINCIPAL](st: GetSessionTransport)(
      body: => PartialServerEndpointWithSecurityOutput[
        SECURITY_INPUT,
        PRINCIPAL,
        Unit,
        Unit,
        _,
        Unit,
        Any,
        Future
      ]
  ): PartialServerEndpointWithSecurityOutput[
    (SECURITY_INPUT, Seq[Option[String]]),
    PRINCIPAL,
    Unit,
    Unit,
    Seq[Option[String]],
    Unit,
    Any,
    Future
  ] =
    invalidateRefreshableSession(st)(body)

  override def touchSession(
      st: GetSessionTransport,
      required: Option[Boolean]
  ): PartialServerEndpointWithSecurityOutput[Seq[Option[String]],
                                             SessionResult[T],
                                             Unit,
                                             Unit,
                                             Seq[
                                               Option[String]
                                             ],
                                             Unit,
                                             Any,
                                             Future] = touchRefreshableSession(st, required)
}
