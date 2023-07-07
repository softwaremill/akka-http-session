package com.softwaremill.session

import sttp.model.Method
import sttp.model.headers.CookieValueWithMeta
import sttp.tapir.{EndpointIO, RawPart}
import sttp.tapir.server.PartialServerEndpointWithSecurityOutput

import scala.concurrent.{ExecutionContext, Future}

trait CsrfEndpoints {

  def hmacTokenCsrfProtection[T, SECURITY_INPUT, PRINCIPAL, ERROR_OUTPUT, SECURITY_OUTPUT](
      checkMode: TapirCsrfCheckMode[T]
  )(
      body: => PartialServerEndpointWithSecurityOutput[
        SECURITY_INPUT,
        PRINCIPAL,
        Unit,
        ERROR_OUTPUT,
        SECURITY_OUTPUT,
        Unit,
        Any,
        Future
      ]
  ): PartialServerEndpointWithSecurityOutput[
    (SECURITY_INPUT, Option[String], Method, Option[String]),
    PRINCIPAL,
    Unit,
    _,
    (SECURITY_OUTPUT, Option[CookieValueWithMeta]),
    Unit,
    Any,
    Future
  ] =
    checkMode.hmacTokenCsrfProtection {
      body
    }

  def hmacTokenCsrfProtectionWithFormOrMultipart[T, SECURITY_INPUT, PRINCIPAL, SECURITY_OUTPUT, F](
      checkMode: TapirCsrfCheckMode[T],
      form: Either[EndpointIO.Body[String, F], EndpointIO.Body[Seq[RawPart], F]]
  )(
      body: => PartialServerEndpointWithSecurityOutput[
        SECURITY_INPUT,
        PRINCIPAL,
        Unit,
        Unit,
        SECURITY_OUTPUT,
        Unit,
        Any,
        Future
      ]
  )(implicit f: F => Option[String]): PartialServerEndpointWithSecurityOutput[
    (SECURITY_INPUT, Option[String], Method, Option[String], F),
    PRINCIPAL,
    Unit,
    Unit,
    (SECURITY_OUTPUT, Option[CookieValueWithMeta]),
    Unit,
    Any,
    Future
  ] =
    checkMode.hmacTokenCsrfProtectionWithFormOrMultipart(form) {
      body
    }

  def setNewCsrfToken[T, SECURITY_INPUT, PRINCIPAL, ERROR_OUTPUT, SECURITY_OUTPUT](
      checkMode: TapirCsrfCheckMode[T]
  )(
      body: => PartialServerEndpointWithSecurityOutput[
        SECURITY_INPUT,
        PRINCIPAL,
        Unit,
        ERROR_OUTPUT,
        SECURITY_OUTPUT,
        Unit,
        Any,
        Future
      ]
  ): PartialServerEndpointWithSecurityOutput[
    SECURITY_INPUT,
    PRINCIPAL,
    Unit,
    ERROR_OUTPUT,
    (SECURITY_OUTPUT, Option[CookieValueWithMeta]),
    Unit,
    Any,
    Future
  ] =
    checkMode.setNewCsrfToken {
      body
    }
}

object CsrfEndpoints extends CsrfEndpoints

object TapirCsrfOptions {
  def checkHeader[T](implicit manager: SessionManager[T], ec: ExecutionContext) =
    new TapirCsrfCheckHeader[T]()
  def checkHeaderAndForm[T](implicit manager: SessionManager[T], ec: ExecutionContext) =
    new TapirCsrfCheckHeaderAndForm[T]()
}

sealed trait TapirCsrfCheckMode[T] extends TapirCsrf[T] { _: CsrfCheck =>
  def manager: SessionManager[T]
  def ec: ExecutionContext
  def csrfManager: CsrfManager[T] = manager.csrfManager
}

class TapirCsrfCheckHeader[T](implicit val manager: SessionManager[T], val ec: ExecutionContext)
    extends TapirCsrfCheckMode[T]
    with CsrfCheckHeader

class TapirCsrfCheckHeaderAndForm[T](implicit
                                     val manager: SessionManager[T],
                                     val ec: ExecutionContext)
    extends TapirCsrfCheckMode[T]
    with CsrfCheckHeaderAndForm
