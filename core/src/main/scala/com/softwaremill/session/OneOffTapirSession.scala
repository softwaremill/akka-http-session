package com.softwaremill.session

import akka.http.scaladsl.model.DateTime
import sttp.model.StatusCode
import sttp.model.headers.CookieValueWithMeta
import sttp.monad.FutureMonad
import sttp.tapir.server.PartialServerEndpointWithSecurityOutput
import sttp.tapir.{cookie, header, _}

import scala.concurrent.{ExecutionContext, Future}

private[session] trait OneOffTapirSession[T] {
  import TapirImplicits._

  implicit def manager: SessionManager[T]

  implicit def ec: ExecutionContext

  def getSessionFromClientAsCookie: EndpointInput.Cookie[Option[String]] = {
    cookie(manager.config.sessionCookieConfig.name)
  }

  def sendSessionToClientAsCookie: EndpointIO.Header[Option[CookieValueWithMeta]] = {
    setCookieOpt(manager.config.sessionCookieConfig.name)
  }

  def getSessionFromClientAsHeader: EndpointIO.Header[Option[String]] = {
    header[Option[String]](manager.config.sessionHeaderConfig.getFromClientHeaderName)
  }

  def sendSessionToClientAsHeader: EndpointIO.Header[Option[String]] = {
    header[Option[String]](manager.config.sessionHeaderConfig.sendToClientHeaderName)
  }

  def setOneOffSession[INPUT](st: SetSessionTransport)(
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
      case CookieST => setOneOffCookieSession(endpoint)
      case HeaderST => setOneOffHeaderSession(endpoint)
    }

  private[this] def setOneOffSessionLogic[INPUT](
      input: INPUT,
      existing: Option[String]
  )(implicit f: INPUT => Option[T]): Either[Unit, Option[String]] =
    existing match {
      case Some(value) =>
        Right(
          Some(value)
        )
      case _ =>
        implicitly[Option[T]](input) match {
          case Some(v) => Right(Some(manager.clientSessionManager.encode(v)))
          case _       => Left(())
        }
    }

  def setOneOffCookieSession[INPUT](
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
    endpoint
      .securityIn(getSessionFromClientAsCookie.map(Seq(_))(_.head))
      .out(sendSessionToClientAsCookie)
      .mapOut(o => Seq(o.map(_.value)))(
        _.head.map(manager.clientSessionManager.createCookieWithValue(_).valueWithMeta)
      )
      .serverSecurityLogicWithOutput { inputs =>
        Future.successful(
          setOneOffSessionLogic(inputs._1, inputs._2.head).map(
            result =>
              (
                Seq(result),
                inputs._1
            ))
        )
      }

  def setOneOffHeaderSession[INPUT](
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
                                                                    Future] =
    endpoint
      .securityIn(getSessionFromClientAsHeader.map(Seq(_))(_.head))
      .out(sendSessionToClientAsHeader)
      .mapOut(Seq(_))(_.head)
      .serverSecurityLogicWithOutput { inputs =>
        Future.successful(
          setOneOffSessionLogic(inputs._1, inputs._2.head)
            .map(
              result =>
                (
                  Seq(result),
                  inputs._1
              ))
        )
      }

  def oneOffSession(
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
      case CookieST         => oneOffCookieSession(required)
      case HeaderST         => oneOffHeaderSession(required)
      case CookieOrHeaderST => oneOffCookieOrHeaderSession(required)
    }

  def oneOffCookieSession(
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
    endpoint
      .securityIn(getSessionFromClientAsCookie)
      .mapSecurityIn(Seq(_))(_.head)
      .out(sendSessionToClientAsCookie)
      .mapOut(o => Seq(o.map(_.value)))(oo =>
        oo.head.map(manager.clientSessionManager.createCookieWithValue(_).valueWithMeta))
      .errorOut(statusCode(StatusCode.Unauthorized))
      .serverSecurityLogicWithOutput { inputs =>
        Future.successful(
          oneOffCookieOrHeaderSessionLogic(inputs.head, None, required).map(e => (e._1._1, e._2)) match {
            case Left(l)  => Left(l)
            case Right(r) => Right((Seq(r._1), r._2))
          })
      }
  }

  def extractOneOffSession(maybeValue: Option[String]): Option[T] =
    maybeValue.flatMap(manager.clientSessionManager.decode(_).toOption)

  def oneOffHeaderSession(
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
                                             Future] =
    endpoint
      .securityIn(
        getSessionFromClientAsHeader
      )
      .mapSecurityIn(Seq(_))(_.head)
      .out(sendSessionToClientAsHeader)
      .mapOut(Seq(_))(_.head)
      .errorOut(statusCode(StatusCode.Unauthorized))
      .serverSecurityLogicWithOutput { inputs =>
        Future.successful(
          oneOffCookieOrHeaderSessionLogic(None, inputs.head, required).map(e => (e._1._2, e._2)) match {
            case Left(l)  => Left(l)
            case Right(r) => Right((Seq(r._1), r._2))
          })
      }

  def oneOffCookieOrHeaderSessionLogic(
      maybeCookie: Option[String],
      maybeHeader: Option[String],
      required: Option[Boolean]
  ): Either[Unit, ((Option[String], Option[String]), SessionResult[T])] = {
    // read session from the cookie and/or header
    val oneOff = maybeCookie.fold(maybeHeader)(Some(_))
    oneOff match {
      case Some(value) =>
        val decoded = manager.clientSessionManager.decode(value)
        decoded match {
          case s: SessionResult.DecodedLegacy[T] =>
            Right(
              ((
                 Some(manager.clientSessionManager.encode(s.session)),
                 maybeHeader.map(_ => value)
               ),
               s)
            )
          case s =>
            Right(
              ((
                 None,
                 None
               ),
               s)
            )
        }
      case _ =>
        if (required.getOrElse(false)) {
          Left(())
        } else {
          Right(((None, None), SessionResult.NoSession))
        }
    }
  }

  def oneOffCookieOrHeaderSession(
      required: Option[Boolean]
  ): PartialServerEndpointWithSecurityOutput[Seq[Option[String]],
                                             SessionResult[
                                               T
                                             ],
                                             Unit,
                                             Unit,
                                             Seq[Option[String]],
                                             Unit,
                                             Any,
                                             Future] =
    endpoint
      .securityIn(getSessionFromClientAsCookie)
      .securityIn(getSessionFromClientAsHeader)
      .mapSecurityIn(inputs => Seq(inputs._1, inputs._2))(oo => (oo.head, oo.last))
      .out(sendSessionToClientAsCookie)
      .out(sendSessionToClientAsHeader)
      .mapOut(outputs => Seq(outputs._1.map(_.value), outputs._2))(
        oo =>
          (
            oo.head.map(manager.clientSessionManager.createCookieWithValue(_).valueWithMeta),
            oo.last
        ))
      .errorOut(statusCode(StatusCode.Unauthorized))
      .serverSecurityLogicWithOutput { inputs =>
        Future.successful(
          oneOffCookieOrHeaderSessionLogic(inputs.head, inputs.last, required)
            .map(result => (Seq(result._1._1, result._1._2), result._2))
        )
      }

  private[this] def invalidateOneOffSessionLogic[SECURITY_OUTPUT, PRINCIPAL](
      result: (SECURITY_OUTPUT, PRINCIPAL),
      maybeCookie: Option[String],
      maybeHeader: Option[String]
  ): Either[Unit, (Seq[Option[String]], PRINCIPAL)] = {
    val principal = result._2
    maybeCookie match {
      case Some(_) =>
        maybeHeader match {
          case Some(_) =>
            Right(
              (Seq(
                 Some("deleted"),
                 Some("")
               ),
               principal)
            )
          case _ =>
            Right(
              (Seq(
                 Some("deleted"),
                 None
               ),
               principal)
            )
        }
      case _ =>
        maybeHeader match {
          case Some(_) => Right((Seq(None, Some("")), principal))
          case _       => Right((Seq(None, None), principal))
        }
    }
  }

  def invalidateOneOffSession[
      SECURITY_INPUT,
      PRINCIPAL
  ](
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
    body.endpoint
      .securityIn(getSessionFromClientAsCookie)
      .securityIn(getSessionFromClientAsHeader)
      .mapSecurityIn(inputs => (inputs._1, Seq(inputs._2, inputs._3)))(oo => (oo._1, oo._2.head, oo._2.last))
      .out(sendSessionToClientAsCookie)
      .out(sendSessionToClientAsHeader)
      .mapOut(outputs => Seq(outputs._1.map(_.value), outputs._2))(
        oo =>
          (
            oo.head.map(
              manager.clientSessionManager
                .createCookieWithValue(_)
                .withExpires(DateTime.MinValue)
                .valueWithMeta
            ),
            oo.last
        ))
      .serverSecurityLogicWithOutput { inputs =>
        body.securityLogic(new FutureMonad())(inputs._1).map {
          case Left(l)  => Left(l)
          case Right(r) => invalidateOneOffSessionLogic(r, inputs._2.head, inputs._2.last)
        }
      }

  def touchOneOffSession(
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
    val partial = oneOffSession(st, required)
    partial.endpoint
      .out(partial.securityOutput)
      .serverSecurityLogicWithOutput { inputs =>
        partial.securityLogic(new FutureMonad())(inputs).map {
          case Left(l) => Left(l)
          case Right(r) =>
            val session = r._2
            st match {
              case CookieST | HeaderST =>
                setOneOffSessionLogic(session.toOption, None)
                  .map(result => (Seq(result), session))
              case CookieOrHeaderST =>
                val maybeCookie = inputs.head
                val maybeHeader = inputs.last
                setOneOffSessionLogic(session.toOption, None)
                  .map(
                    result =>
                      (
                        Seq(maybeCookie.flatMap(_ => result), maybeHeader.flatMap(_ => result)),
                        session
                    ))
            }
        }
      }
  }

}
