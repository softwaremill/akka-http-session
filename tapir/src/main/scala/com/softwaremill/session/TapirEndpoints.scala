package com.softwaremill.session

import sttp.model.Method
import sttp.model.headers.CookieValueWithMeta
import sttp.tapir.Endpoint
import sttp.tapir.server.PartialServerEndpointWithSecurityOutput

import scala.concurrent.Future

trait TapirEndpoints extends SessionEndpoints with CsrfEndpoints {

  def antiCsrfWithRequiredSession[T](
      sc: TapirSessionContinuity[T],
      st: GetSessionTransport,
      checkMode: TapirCsrfCheckMode[T]
  ): PartialServerEndpointWithSecurityOutput[
    (Seq[Option[String]], Option[String], Method, Option[String], Map[String, String]),
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
    (Seq[Option[String]], Option[String], Method, Option[String], Map[String, String]),
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

  def setNewCsrfTokenWithSession[T, INPUT](
      sc: TapirSessionContinuity[T],
      st: SetSessionTransport,
      checkMode: TapirCsrfCheckMode[T]
  )(endpoint: => Endpoint[INPUT, Unit, Unit, Unit, Any])(implicit
                                                         f: INPUT => Option[T])
    : PartialServerEndpointWithSecurityOutput[(INPUT, Seq[Option[String]]),
                                              Option[
                                                T
                                              ],
                                              Unit,
                                              Unit,
                                              (Seq[Option[String]], Option[CookieValueWithMeta]),
                                              Unit,
                                              Any,
                                              Future] =
    setNewCsrfToken(checkMode) {
      setSession(sc, st) {
        endpoint
      }
    }
}

object TapirEndpoints extends TapirEndpoints
