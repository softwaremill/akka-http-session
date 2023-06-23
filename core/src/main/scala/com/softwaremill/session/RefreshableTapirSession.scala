package com.softwaremill.session

import akka.http.scaladsl.model.DateTime
import sttp.model.StatusCode
import sttp.model.headers.CookieValueWithMeta
import sttp.monad.FutureMonad
import sttp.tapir.server.PartialServerEndpointWithSecurityOutput
import sttp.tapir.{Endpoint, EndpointIO, EndpointInput, cookie, header, setCookieOpt, statusCode}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

private[session] trait RefreshableTapirSession[T] extends Completion {
  this: OneOffTapirSession[T] =>
  import com.softwaremill.session.TapirImplicits._

  implicit def refreshTokenStorage: RefreshTokenStorage[T]

  implicit def ec: ExecutionContext

  def refreshable: Refreshable[T] = SessionOptions.refreshable

  def getRefreshTokenFromClientAsCookie: EndpointInput.Cookie[Option[String]] = {
    cookie(manager.config.refreshTokenCookieConfig.name)
  }

  def sendRefreshTokenToClientAsCookie: EndpointIO.Header[Option[CookieValueWithMeta]] = {
    setCookieOpt(manager.config.refreshTokenCookieConfig.name)
  }

  def getRefreshTokenFromClientAsHeader: EndpointIO.Header[Option[String]] = {
    header[Option[String]](manager.config.refreshTokenHeaderConfig.getFromClientHeaderName)
  }

  def sendRefreshTokenToClientAsHeader: EndpointIO.Header[Option[String]] = {
    header[Option[String]](manager.config.refreshTokenHeaderConfig.sendToClientHeaderName)
  }

  def setRefreshableSession[INPUT](st: SetSessionTransport)(
      endpoint: => Endpoint[INPUT, Unit, Unit, Unit, Any]
  )(implicit
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
    st match {
      case CookieST => setRefreshableCookieSession(endpoint)
      case HeaderST => setRefreshableHeaderSession(endpoint)
    }

  private[this] def rotateToken(
      v: T,
      existing: Option[String]
  ): Option[String] = {
    refreshable.refreshTokenManager
      .rotateToken(v, existing)
      .complete() match {
      case Success(value) => Some(value)
      case Failure(_)     => None
    }
  }

  def setRefreshableSessionLogic[INPUT](
      input: INPUT,
      existing: Option[String]
  )(implicit f: INPUT => Option[T]): Either[Unit, Option[String]] =
    implicitly[Option[T]](input) match {
      case Some(v) => Right(rotateToken(v, existing))
      case _       => Left(())
    }

  def setRefreshableCookieSession[INPUT](
      endpoint: Endpoint[INPUT, Unit, Unit, Unit, Any]
  )(implicit
    f: INPUT => Option[T]): PartialServerEndpointWithSecurityOutput[(INPUT, Seq[Option[String]]),
                                                                    Option[
                                                                      T
                                                                    ],
                                                                    Unit,
                                                                    Unit,
                                                                    Seq[Option[String]],
                                                                    Unit,
                                                                    Any,
                                                                    Future] = {
    val partial = setOneOffSession(CookieST)(endpoint)
    partial.endpoint
      .securityIn(getRefreshTokenFromClientAsCookie)
      .mapSecurityIn(a => (a._1, a._2 :+ a._3))(oo => (oo._1, Seq(oo._2.head), oo._2.last))
      .out(partial.securityOutput)
      .out(sendRefreshTokenToClientAsCookie)
      .mapOut(o => o._1 :+ o._2.map(_.value))(oo =>
        (Seq(oo.head), oo.last.map(refreshable.refreshTokenManager.createCookie(_).valueWithMeta)))
      .serverSecurityLogicWithOutput { inputs =>
        partial
          .securityLogic(new FutureMonad())((inputs._1, Seq(inputs._2.head)))
          .map {
            case Left(l) => Left(l)
            case Right(r) =>
              setRefreshableSessionLogic(r._2, inputs._2.last)
                .map(
                  result =>
                    (
                      r._1 :+ result,
                      r._2
                  ))
          }
      }
  }

  def setRefreshableHeaderSession[INPUT](
      endpoint: Endpoint[INPUT, Unit, Unit, Unit, Any]
  )(implicit
    f: INPUT => Option[T]): PartialServerEndpointWithSecurityOutput[(INPUT, Seq[Option[String]]),
                                                                    Option[
                                                                      T
                                                                    ],
                                                                    Unit,
                                                                    Unit,
                                                                    Seq[Option[String]],
                                                                    Unit,
                                                                    Any,
                                                                    Future] = {
    val partial = setOneOffSession(HeaderST)(endpoint)
    partial.endpoint
      .securityIn(getRefreshTokenFromClientAsHeader)
      .mapSecurityIn(a => (a._1, a._2 :+ a._3))(oo => (oo._1, Seq(oo._2.head), oo._2.last))
      .out(partial.securityOutput)
      .out(sendRefreshTokenToClientAsHeader)
      .mapOut(o => o._1 :+ o._2)(oo => (Seq(oo.head), oo.last))
      .serverSecurityLogicWithOutput { inputs =>
        partial
          .securityLogic(new FutureMonad())((inputs._1, Seq(inputs._2.head)))
          .map {
            case Left(l) => Left(l)
            case Right(r) =>
              setRefreshableSessionLogic(r._2, inputs._2.last)
                .map(
                  result =>
                    (
                      r._1 :+ result,
                      r._2
                  ))
          }
      }
  }

  def extractRefreshableSession(maybeValue: Option[String]): Option[T] = {
    extractOneOffSession(maybeValue) match {
      case None =>
        maybeValue match {
          case Some(value) =>
            refreshable.refreshTokenManager
              .sessionFromValue(value)
              .complete() match {
              case Success(value) => value.toOption
              case Failure(_)     => None
            }
          case _ => None
        }
      case some => some
    }
  }

  def refreshableSession(
      st: GetSessionTransport,
      required: Option[Boolean] = None
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
    st match {
      case CookieST         => refreshableCookieSession(required)
      case HeaderST         => refreshableHeaderSession(required)
      case CookieOrHeaderST => refreshableCookieOrHeaderSession(required)
    }

  private[this] def refreshableSessionLogic(
      oneOffSession: Option[SessionResult[T]],
      maybeCookie: Option[String],
      maybeHeader: Option[String],
      st: GetSessionTransport,
      required: Option[Boolean],
      touch: Boolean = false
  ): Either[Unit, (Seq[Option[String]], SessionResult[T])] = {

    // read refresh token from cookie or header
    val refreshToken = maybeCookie.fold(maybeHeader)(Some(_))

    def setRefreshToken(): Either[Unit, (Option[String], Option[String], SessionResult[T])] = {
      refreshToken match {
        case Some(value) =>
          refreshable.refreshTokenManager
            .sessionFromValue(value)
            .complete() match {
            case Success(value) =>
              value match {
                case s @ SessionResult.CreatedFromToken(session) =>
                  val newSession = Some(manager.clientSessionManager.encode(session))
                  val newToken = rotateToken(session, refreshToken)
                  Right((newSession, newToken, s))
                case s =>
                  if (touch) {
                    val newSession = s.toOption.map(manager.clientSessionManager.encode(_))
                    val newToken = s.toOption.flatMap(rotateToken(_, refreshToken))
                    Right((newSession, newToken, s))
                  } else {
                    Right((None, None, s))
                  }
              }
            case Failure(_) =>
              if (required.getOrElse(false))
                Left(())
              else {
                val newSession = None
                val newToken = None
                Right((newSession, newToken, SessionResult.NoSession))
              }
          }
        case _ =>
          if (required.getOrElse(false))
            Left(())
          else {
            val newSession = None
            val newToken = None
            Right((newSession, newToken, SessionResult.NoSession))
          }
      }
    }

    (oneOffSession match {
      case Some(result) =>
        result match {
          case SessionResult.NoSession | SessionResult.Expired => setRefreshToken()
          case s =>
            if (touch) {
              val newSession = s.toOption.map(manager.clientSessionManager.encode(_))
              val newToken =
                refreshToken match {
                  case Some(value) =>
                    refreshable.refreshTokenManager
                      .sessionFromValue(value)
                      .complete() match {
                      case Success(value) =>
                        value match {
                          case _ @SessionResult.CreatedFromToken(_) => None
                          case _                                    => s.toOption.flatMap(rotateToken(_, refreshToken))
                        }
                      case _ => None
                    }
                  case _ => None
                }
              Right((newSession, newToken, s))
            } else {
              val newSession = None
              val newToken = None
              Right((newSession, newToken, s))
            }
        }
      case _ => setRefreshToken()
    }) match {
      case Left(l) => Left(l)
      case Right((newSession, newToken, s)) =>
        st match {
          case CookieST | HeaderST =>
            Right((Seq(newSession, newToken), s))
          case CookieOrHeaderST =>
            Right(
              (
                Seq(
                  maybeCookie.flatMap(_ => newSession),
                  maybeHeader.flatMap(_ => newSession),
                  maybeCookie.flatMap(_ => newToken),
                  maybeHeader.flatMap(_ => newToken)
                ),
                SessionResult.NoSession
              )
            )
        }
    }
  }

  def refreshableCookieSession(
      required: Option[Boolean] = None
  ): PartialServerEndpointWithSecurityOutput[Seq[Option[String]],
                                             SessionResult[
                                               T
                                             ],
                                             Unit,
                                             Unit,
                                             Seq[Option[String]],
                                             Unit,
                                             Any,
                                             Future] = {
    val partial = oneOffCookieSession(Some(false))
    partial.endpoint
      .securityIn(getRefreshTokenFromClientAsCookie)
      .mapSecurityIn(inputs => inputs._1 :+ inputs._2)(seq => (seq.reverse.tail, seq.last))
      .out(partial.securityOutput)
      .out(sendRefreshTokenToClientAsCookie)
      .mapOut(outputs => outputs._1 :+ outputs._2.map(_.value))(
        seq =>
          (
            seq.reverse.tail,
            seq.last.map(refreshable.refreshTokenManager.createCookie(_).valueWithMeta)
        ))
      .errorOut(statusCode(StatusCode.Unauthorized))
      .serverSecurityLogicWithOutput { inputs =>
        val oneOffInputs: Seq[Option[String]] = Seq(inputs.head)
        val refreshToken = inputs.last
        partial.securityLogic(new FutureMonad())(oneOffInputs).map {
          case Left(l) => Left(l)
          case Right(r) =>
            refreshableSessionLogic(
              Some(r._2),
              refreshToken,
              None,
              CookieST,
              required
            )
        }
      }
  }

  def refreshableHeaderSession(
      required: Option[Boolean] = None
  ): PartialServerEndpointWithSecurityOutput[Seq[Option[String]],
                                             SessionResult[
                                               T
                                             ],
                                             Unit,
                                             Unit,
                                             Seq[Option[String]],
                                             Unit,
                                             Any,
                                             Future] = {
    val partial = oneOffHeaderSession(Some(false))
    partial.endpoint
      .securityIn(getRefreshTokenFromClientAsHeader)
      .mapSecurityIn(inputs => inputs._1 :+ inputs._2)(seq => (seq.reverse.tail, seq.last))
      .out(partial.securityOutput)
      .out(sendRefreshTokenToClientAsHeader)
      .mapOut(outputs => outputs._1 :+ outputs._2)(seq => (seq.reverse.tail, seq.last))
      .errorOut(statusCode(StatusCode.Unauthorized))
      .serverSecurityLogicWithOutput { inputs =>
        val oneOffInputs: Seq[Option[String]] = Seq(inputs.head)
        val refreshToken = inputs.last
        partial.securityLogic(new FutureMonad())(oneOffInputs).map {
          case Left(l) => Left(l)
          case Right(r) =>
            refreshableSessionLogic(
              Some(r._2),
              None,
              refreshToken,
              HeaderST,
              required
            )
        }
      }
  }

  def refreshableCookieOrHeaderSession(
      required: Option[Boolean] = None
  ): PartialServerEndpointWithSecurityOutput[Seq[Option[String]],
                                             SessionResult[T],
                                             Unit,
                                             Unit,
                                             Seq[
                                               Option[String]
                                             ],
                                             Unit,
                                             Any,
                                             Future] = {
    val partial = oneOffCookieOrHeaderSession(Some(false))
    partial.endpoint
      .securityIn(getRefreshTokenFromClientAsCookie)
      .securityIn(getRefreshTokenFromClientAsHeader)
      .mapSecurityIn(inputs => inputs._1 :+ inputs._2 :+ inputs._3)(oo =>
        (oo.take(oo.size - 2), oo.takeRight(2).head, oo.last))
      .out(partial.securityOutput)
      .out(sendRefreshTokenToClientAsCookie)
      .out(sendRefreshTokenToClientAsHeader)
      .mapOut(outputs => outputs._1 :+ outputs._2.map(_.value) :+ outputs._3)(
        oo =>
          (
            oo.take(oo.size - 2),
            oo.takeRight(2).head.map(refreshable.refreshTokenManager.createCookie(_).valueWithMeta),
            oo.last
        ))
      .errorOut(statusCode(StatusCode.Unauthorized))
      .serverSecurityLogicWithOutput { inputs =>
        val oneOffInputs: Seq[Option[String]] = inputs.take(2)
        val maybeCookie = inputs.takeRight(2).head
        val maybeHeader = inputs.last
        partial.securityLogic(new FutureMonad())(oneOffInputs).map {
          case Left(l) => Left(l)
          case Right(r) =>
            refreshableSessionLogic(
              Some(r._2),
              maybeCookie,
              maybeHeader,
              CookieOrHeaderST,
              required
            )
        }
      }
  }

  private[this] def invalidateRefreshableSessionLogic[PRINCIPAL](
      result: (Seq[Option[String]], PRINCIPAL),
      cookie: Option[String],
      header: Option[String]
  ): Either[
    Nothing,
    (
        Seq[Option[String]],
        PRINCIPAL
    )
  ] = {
    val principal = result._2
    cookie match {
      case Some(c) =>
        refreshable.refreshTokenManager.removeToken(c).complete() match {
          case _ =>
            header match {
              case Some(_) => Right((result._1 :+ Some("deleted") :+ Some(""), principal))
              case _       => Right((result._1 :+ Some("deleted") :+ None, principal))
            }
        }
      case _ =>
        header match {
          case Some(h) =>
            refreshable.refreshTokenManager.removeToken(h).complete() match {
              case _ => Right((result._1 :+ None :+ Some(""), principal))
            }
          case _ => Right((result._1 :+ None :+ None, principal))
        }
    }
  }

  def invalidateRefreshableSession[
      SECURITY_INPUT,
      PRINCIPAL
  ](
      partial: PartialServerEndpointWithSecurityOutput[
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
  ] = {
    val partialOneOffSession = invalidateOneOffSession(partial)
    partialOneOffSession.endpoint
      .securityIn(getRefreshTokenFromClientAsCookie)
      .securityIn(getRefreshTokenFromClientAsHeader)
      .mapSecurityIn(inputs => (inputs._1, inputs._2 :+ inputs._3 :+ inputs._4))(oo =>
        (oo._1, oo._2.take(oo._2.size - 2), oo._2.takeRight(2).head, oo._2.last))
      .out(partialOneOffSession.securityOutput)
      .out(sendRefreshTokenToClientAsCookie)
      .out(sendRefreshTokenToClientAsHeader)
      .mapOut(outputs => outputs._1 :+ outputs._2.map(_.value) :+ outputs._3)(
        oo =>
          (
            oo.take(oo.size - 2),
            oo.takeRight(2)
              .head
              .map(
                refreshable.refreshTokenManager
                  .createCookie(_)
                  .withExpires(DateTime.MinValue)
                  .valueWithMeta
              ),
            oo.last
        ))
      .serverSecurityLogicWithOutput { inputs =>
        partialOneOffSession
          .securityLogic(new FutureMonad())((inputs._1, inputs._2.take(inputs._2.size - 2)))
          .map {
            case Left(l) => Left(l)
            case Right(r) =>
              invalidateRefreshableSessionLogic(r, inputs._2.takeRight(2).head, inputs._2.last)
          }
      }
  }

  def touchRefreshableSession(
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
                                             Future] = {
    val partial = refreshableSession(st, required)
    partial.endpoint
      .out(partial.securityOutput)
      .serverSecurityLogicWithOutput { inputs =>
        partial.securityLogic(new FutureMonad())(inputs).map {
          case Left(l) => Left(l)
          case Right(r) =>
            val session = r._2
            st match {
              case CookieST =>
                refreshableSessionLogic(
                  Some(session),
                  inputs.last,
                  None,
                  CookieST,
                  Some(false),
                  touch = true
                )
              case HeaderST =>
                refreshableSessionLogic(
                  Some(session),
                  None,
                  inputs.last,
                  HeaderST,
                  Some(false),
                  touch = true
                )
              case CookieOrHeaderST =>
                val maybeCookie = inputs.takeRight(2).head
                val maybeHeader = inputs.last
                refreshableSessionLogic(
                  Some(session),
                  maybeCookie,
                  maybeHeader,
                  CookieOrHeaderST,
                  Some(false),
                  touch = true
                )
            }
        }
      }
  }
}
