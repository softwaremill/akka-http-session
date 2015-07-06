package com.softwaremill.session

import akka.http.scaladsl.server.{Directive1, Directive0}
import akka.http.scaladsl.server.Directives._
import akka.stream.Materializer

trait CsrfDirectives {
  /**
   * Protects against CSRF attacks using a double-submit cookie. The cookie will be set on any `GET` request which
   * doesn't have the token set in the header. For all other requests, the value of the token from the CSRF cookie must
   * match the value in the custom header (or request body, if `checkFormBody` is `true`).
   *
   * Note that this scheme can be broken when not all subdomains are protected or not using HTTPS and secure cookies,
   * and the token is placed in the request body.
   *
   * See the documentation for more details.
   */
  def randomTokenCsrfProtection(magnet: SessionManagerMagnet[CsrfCheckMode]): Directive0 = {
    import magnet.manager
    csrfTokenFromCookie(magnet).flatMap {
      case Some(cookie) =>
        // if a cookie is already set, we let through all get requests (without setting a new token), or validate
        // that the token matches.
        get | submittedCsrfToken(magnet).flatMap { submitted =>
          if (submitted == cookie) {
            pass
          } else {
            reject(magnet.manager.csrfTokenInvalidRejection).toDirective[Unit]
          }
        }
      case None =>
        // if a cookie is not set, generating a new one for get requests, rejecting other
        (get & setNewCsrfToken()).recover(_ => reject(magnet.manager.csrfTokenInvalidRejection))
    }
  }

  def submittedCsrfToken(magnet: SessionManagerMagnet[CsrfCheckMode]): Directive1[String] = {
    headerValueByName(magnet.manager.csrfSubmittedName).recover { rejections =>
      magnet.input match {
        case c: CheckHeaderAndForm =>
          import c.materializer
          formField(magnet.manager.csrfSubmittedName)
        case _ => reject(rejections: _*)
      }
    }
  }

  def csrfTokenFromCookie(magnet: SessionManagerMagnet[CsrfCheckMode]): Directive1[Option[String]] =
    optionalCookie(magnet.manager.csrfCookieName).map(_.map(_.value))

  def setNewCsrfToken(magnet: SessionManagerMagnet[Unit]): Directive0 =
    setCookie(magnet.manager.createCsrfCookie())
}

object CsrfDirectives extends CsrfDirectives

sealed trait CsrfCheckMode
case object CheckHeader extends CsrfCheckMode
case class CheckHeaderAndForm(implicit val materializer: Materializer) extends CsrfCheckMode