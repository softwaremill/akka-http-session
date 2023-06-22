package com.softwaremill.session

import sttp.model.Method
import sttp.model.headers.CookieValueWithMeta
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

}

object TapirEndpoints extends TapirEndpoints
