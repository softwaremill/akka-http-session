package com.softwaremill.session

import sttp.tapir._
import sttp.tapir.server.PartialServerEndpointWithSecurityOutput

import scala.concurrent.{ExecutionContext, Future}

trait SessionEndpoints {

  def setSessionEndpoint[T, SECURITY_INPUT, ERROR_OUTPUT](
      endpoint: => Endpoint[SECURITY_INPUT, Unit, ERROR_OUTPUT, Unit, Any]
  )(implicit
    f: SECURITY_INPUT => Option[T]): PartialServerEndpointWithSecurityOutput[SECURITY_INPUT,
                                                                             Option[
                                                                               T
                                                                             ],
                                                                             Unit,
                                                                             ERROR_OUTPUT,
                                                                             Unit,
                                                                             Unit,
                                                                             Any,
                                                                             Future] =
    endpoint
      .serverSecurityLogicSuccessWithOutput(si => Future.successful(((), f(si))))

  /** Set the session cookie with the session content. The content is signed, optionally encrypted
    * and with an optional expiry date.
    *
    * If refreshable, generates a new token (removing old ones) and stores it in the refresh token
    * cookie.
    */
  def setSession[T, SECURITY_INPUT, SECURITY_OUTPUT, ERROR_OUTPUT](
      sc: TapirSessionContinuity[T],
      st: SetSessionTransport
  )(
      body: => PartialServerEndpointWithSecurityOutput[SECURITY_INPUT,
                                                       Option[
                                                         T
                                                       ],
                                                       Unit,
                                                       ERROR_OUTPUT,
                                                       SECURITY_OUTPUT,
                                                       Unit,
                                                       Any,
                                                       Future]
  ): PartialServerEndpointWithSecurityOutput[(SECURITY_INPUT, Seq[Option[String]]),
                                             Option[
                                               T
                                             ],
                                             Unit,
                                             ERROR_OUTPUT,
                                             (SECURITY_OUTPUT, Seq[Option[String]]),
                                             Unit,
                                             Any,
                                             Future] =
    sc.setSession(st)(body)

  def setSessionWithAuth[T, A](sc: TapirSessionContinuity[T], st: SetSessionTransport)(
      auth: EndpointInput.Auth[A, EndpointInput.AuthType.Http]
  )(implicit
    f: A => Option[T]): PartialServerEndpointWithSecurityOutput[
    (A, Seq[Option[String]]),
    Option[T],
    Unit,
    Unit,
    (
        Unit,
        Seq[
          Option[String]
        ]
    ),
    Unit,
    Any,
    Future
  ] =
    setSession[T, A, Unit, Unit](sc, st) {
      setSessionEndpoint {
        endpoint.securityIn(auth)
      }
    }

  /** Read a session from the session cookie, wrapped in [[SessionResult]] describing the possible
    * success/failure outcomes.
    *
    * If refreshable, tries to create a new session based on the refresh token cookie.
    */
  def session[T](
      sc: TapirSessionContinuity[T],
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
    sc.session(st, required)

  /** Invalidate the session cookie.
    *
    * If refreshable, also removes the refresh token cookie and the refresh token token (from the
    * client and token store), if present.
    *
    * Note that you should use `refreshable` if you use refreshable systems even only for some
    * users.
    */
  def invalidateSession[T, SECURITY_INPUT, PRINCIPAL](
      sc: TapirSessionContinuity[T],
      st: GetSessionTransport
  )(
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
    sc.invalidateSession(st)(body)

  /** Read an optional session from the session cookie.
    */
  def optionalSession[T](
      sc: TapirSessionContinuity[T],
      st: GetSessionTransport
  ): PartialServerEndpointWithSecurityOutput[Seq[Option[String]],
                                             Option[T],
                                             Unit,
                                             Unit,
                                             Seq[
                                               Option[String]
                                             ],
                                             Unit,
                                             Any,
                                             Future] =
    sc.optionalSession(st)

  /** Read a required session from the session cookie.
    */
  def requiredSession[T](
      sc: TapirSessionContinuity[T],
      st: GetSessionTransport
  ): PartialServerEndpointWithSecurityOutput[Seq[Option[String]],
                                             T,
                                             Unit,
                                             Unit,
                                             Seq[
                                               Option[String]
                                             ],
                                             Unit,
                                             Any,
                                             Future] =
    sc.requiredSession(st)

  def touchSession[T](
      sc: TapirSessionContinuity[T],
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
    sc.touchSession(st, required)

  /** Sets the session cookie again with the same data. Useful when using the
    * [[SessionConfig.sessionMaxAgeSeconds]] option, as it sets the expiry date anew.
    */
  def touchOptionalSession[T](
      sc: TapirSessionContinuity[T],
      st: GetSessionTransport
  ): PartialServerEndpointWithSecurityOutput[Seq[Option[String]],
                                             Option[T],
                                             Unit,
                                             Unit,
                                             Seq[
                                               Option[String]
                                             ],
                                             Unit,
                                             Any,
                                             Future] = {
    sc.touchOptionalSession(st)
  }

  /** Sets the session cookie again with the same data. Useful when using the
    * [[SessionConfig.sessionMaxAgeSeconds]] option, as it sets the expiry date anew.
    */
  def touchRequiredSession[T](
      sc: TapirSessionContinuity[T],
      st: GetSessionTransport
  ): PartialServerEndpointWithSecurityOutput[Seq[Option[String]],
                                             T,
                                             Unit,
                                             Unit,
                                             Seq[
                                               Option[String]
                                             ],
                                             Unit,
                                             Any,
                                             Future] = {
    sc.touchRequiredSession(st)
  }
}

object TapirSessionOptions {
  def oneOff[T](implicit manager: SessionManager[T], ec: ExecutionContext) = new OneOffTapir[T]()
  def refreshable[T](implicit
                     manager: SessionManager[T],
                     refreshTokenStorage: RefreshTokenStorage[T],
                     ec: ExecutionContext) = new RefreshableTapir[T]()
  def usingCookies: SetSessionTransport = CookieST
  def usingHeaders: SetSessionTransport = HeaderST
  def usingCookiesOrHeaders: GetSessionTransport = CookieOrHeaderST
}

class OneOffTapir[T](implicit val manager: SessionManager[T], val ec: ExecutionContext)
    extends OneOffTapirSessionContinuity[T]
    with OneOffTapirSession[T]

class RefreshableTapir[T](implicit
                          val manager: SessionManager[T],
                          val refreshTokenStorage: RefreshTokenStorage[T],
                          val ec: ExecutionContext)
    extends RefreshableTapirSessionContinuity[T]
    with RefreshableTapirSession[T]
    with OneOffTapirSession[T]

object SessionEndpoints extends SessionEndpoints
