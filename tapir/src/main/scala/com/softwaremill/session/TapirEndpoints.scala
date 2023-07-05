package com.softwaremill.session

import sttp.model.Method
import sttp.model.headers.CookieValueWithMeta
import sttp.tapir.EndpointInput
import sttp.tapir.server.PartialServerEndpointWithSecurityOutput

import scala.concurrent.Future

trait TapirEndpoints extends SessionEndpoints with CsrfEndpoints {

  def antiCsrfWithRequiredSession[T](
      sc: TapirSessionContinuity[T],
      st: GetSessionTransport,
      checkMode: TapirCsrfCheckMode[T]
  ): PartialServerEndpointWithSecurityOutput[
    (Seq[Option[String]], Option[String], Method, Option[String]),
    T,
    Unit,
    Unit,
    (Seq[Option[String]], Option[CookieValueWithMeta]),
    Unit,
    Any,
    Future
  ] =
    hmacTokenCsrfProtection(checkMode) {
      requiredSession(sc, st)
    }

  def antiCsrfWithOptionalSession[T](
      sc: TapirSessionContinuity[T],
      st: GetSessionTransport,
      checkMode: TapirCsrfCheckMode[T]
  ): PartialServerEndpointWithSecurityOutput[
    (Seq[Option[String]], Option[String], Method, Option[String]),
    Option[T],
    Unit,
    Unit,
    (Seq[Option[String]], Option[CookieValueWithMeta]),
    Unit,
    Any,
    Future
  ] =
    hmacTokenCsrfProtection(checkMode) {
      optionalSession(sc, st)
    }

  def setNewCsrfTokenWithSession[T, SECURITY_INPUT, SECURITY_OUTPUT](
      sc: TapirSessionContinuity[T],
      st: SetSessionTransport,
      checkMode: TapirCsrfCheckMode[T]
  )(
      body: => PartialServerEndpointWithSecurityOutput[SECURITY_INPUT,
                                                       Option[
                                                         T
                                                       ],
                                                       Unit,
                                                       Unit,
                                                       SECURITY_OUTPUT,
                                                       Unit,
                                                       Any,
                                                       Future]
  ): PartialServerEndpointWithSecurityOutput[(SECURITY_INPUT, Seq[Option[String]]),
                                             Option[
                                               T
                                             ],
                                             Unit,
                                             Unit,
                                             ((SECURITY_OUTPUT, Seq[Option[String]]), Option[CookieValueWithMeta]),
                                             Unit,
                                             Any,
                                             Future] =
    setNewCsrfToken(checkMode) {
      setSession(sc, st) {
        body
      }
    }

  def setNewCsrfTokenWithAuth[T, A](
      sc: TapirSessionContinuity[T],
      st: SetSessionTransport,
      checkMode: TapirCsrfCheckMode[T]
  )(auth: EndpointInput.Auth[A, EndpointInput.AuthType.Http])(implicit
                                                              f: A => Option[T])
    : PartialServerEndpointWithSecurityOutput[(A, Seq[Option[String]]),
                                              Option[
                                                T
                                              ],
                                              Unit,
                                              Unit,
                                              ((Unit, Seq[Option[String]]), Option[CookieValueWithMeta]),
                                              Unit,
                                              Any,
                                              Future] =
    setNewCsrfToken(checkMode) {
      setSessionWithAuth(sc, st)(auth)
    }
}

object TapirEndpoints extends TapirEndpoints
