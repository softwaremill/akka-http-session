package com.softwaremill.session

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive0, Directive1}
import akka.stream.Materializer

trait CsrfDirectives {

  /**
    * Protects against CSRF attacks using a double-submit cookie. The cookie will be set on any `GET` request which
    * doesn't have the token set in the header. For all other requests, the value of the token from the CSRF cookie must
    * match the value in the custom header (or request body, if `checkFormBody` is `true`).
    *
    * Note that this scheme can be broken when not all subdomains are protected or not using HTTPS and secure cookies,
    * and the token is placed in the request body (not in the header).
    *
    * See the documentation for more details.
    */
  def randomTokenCsrfProtection[T](checkMode: CsrfCheckMode[T]): Directive0 = {
    csrfTokenFromCookie(checkMode).flatMap {
      case Some(cookie) =>
        // if a cookie is already set, we let through all get requests (without setting a new token), or validate
        // that the token matches.
        get.recover { _ =>
          submittedCsrfToken(checkMode).flatMap { submitted =>
            if (submitted == cookie && !cookie.isEmpty) {
              pass
            } else {
              reject(checkMode.csrfManager.tokenInvalidRejection).toDirective[Unit]
            }
          }
        }
      case None =>
        // if a cookie is not set, generating a new one for get requests, rejecting other
        (get & setNewCsrfToken(checkMode)).recover(_ => reject(checkMode.csrfManager.tokenInvalidRejection))
    }
  }

  def submittedCsrfToken[T](checkMode: CsrfCheckMode[T]): Directive1[String] = {
    headerValueByName(checkMode.manager.config.csrfSubmittedName).recover { rejections =>
      checkMode match {
        case c: CheckHeaderAndForm[T] =>
          import c.materializer
          formField(checkMode.manager.config.csrfSubmittedName)
        case _ => reject(rejections: _*)
      }
    }
  }

  def csrfTokenFromCookie[T](checkMode: CsrfCheckMode[T]): Directive1[Option[String]] =
    optionalCookie(checkMode.manager.config.csrfCookieConfig.name).map(_.map(_.value))

  def setNewCsrfToken[T](checkMode: CsrfCheckMode[T]): Directive0 =
    setCookie(checkMode.csrfManager.createCookie())
}

object CsrfDirectives extends CsrfDirectives

sealed trait CsrfCheckMode[T] {
  def manager: SessionManager[T]
  def csrfManager = manager.csrfManager
}
class CheckHeader[T] private[session] (implicit val manager: SessionManager[T]) extends CsrfCheckMode[T]
class CheckHeaderAndForm[T] private[session] (implicit
                                              val manager: SessionManager[T],
                                              val materializer: Materializer)
    extends CsrfCheckMode[T]

object CsrfOptions {
  def checkHeader[T](implicit manager: SessionManager[T]): CheckHeader[T] = new CheckHeader[T]()
  def checkHeaderAndForm[T](implicit manager: SessionManager[T], materializer: Materializer): CheckHeaderAndForm[T] =
    new CheckHeaderAndForm[T]()
}
