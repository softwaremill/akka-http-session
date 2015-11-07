package com.softwaremill.session

import akka.http.scaladsl.server.{Directive1, Directive0}
import akka.http.scaladsl.server.Directives._

import scala.concurrent.ExecutionContext

/**
 * Manages cookie-based sessions wiht optional refresh tokens. A refresh token is written to a separate cookie.
 */
trait ClientSessionDirectives extends OneOffSessionDirectives with RefreshableSessionDirectives {
  /**
   * Set the session cookie with the session content. The content is signed, optionally encrypted and with
   * an optional expiry date.
   *
   * If refreshable, generates a new token (removing old ones) and stores it in the remember me cookie.
   */
  def setSession[T](sc: SessionContinuity[T], v: T): Directive0 = {
    sc match {
      case _: OneOff[T] => setOneOffSession(sc, v)
      case r: Refreshable[T] => setRefreshableSession(r, v)
    }
  }

  /**
   * Read a session from the session cookie, wrapped in [[SessionResult]] describing the possible
   * success/failure outcomes.
   *
   * If refreshable, tries to create a new session based on the remember me cookie.
   */
  def session[T](sc: SessionContinuity[T]): Directive1[SessionResult[T]] = {
    sc match {
      case _: OneOff[T] => oneOffSession(sc)
      case r: Refreshable[T] => refreshableSession(r)
    }
  }

  /**
   * Invalidate the session cookie.
   *
   * If refreshable, also removes the remember me cookie and the remember me token (from the client and token store).
   */
  def invalidateSession[T](sc: SessionContinuity[T]): Directive0 = {
    sc match {
      case _: OneOff[T] => invalidateOneOffSession(sc)
      case r: Refreshable[T] => invalidateRefreshableSession(r)
    }
  }

  /**
   * Read an optional session from the session cookie.
   */
  def optionalSession[T](sc: SessionContinuity[T]): Directive1[Option[T]] =
    session(sc).map(_.toOption)

  /**
   * Read a required session from the session cookie.
   */
  def requiredSession[T](sc: SessionContinuity[T]): Directive1[T] =
    optionalSession(sc).flatMap {
      case None => reject(sc.manager.clientSession.cookieMissingRejection)
      case Some(data) => provide(data)
    }

  /**
   * Sets the session cookie again with the same data. Useful when using the [[SessionConfig.clientSessionMaxAgeSeconds]]
   * option, as it sets the expiry date anew.
   */
  def touchOptionalSession[T](sc: SessionContinuity[T]): Directive1[Option[T]] = {
    optionalSession(sc).flatMap { d => d.fold(pass)(s => setSession(sc, s)) & provide(d) }
  }

  /**
   * Sets the session cookie again with the same data. Useful when using the [[SessionConfig.clientSessionMaxAgeSeconds]]
   * option, as it sets the expiry date anew.
   */
  def touchRequiredSession[T](sc: SessionContinuity[T]): Directive1[T] = {
    requiredSession(sc).flatMap { d => setOneOffSession(sc, d) & provide(d) }
  }

  def oneOff[T](implicit manager: SessionManager[T]): OneOff[T] = new OneOff[T]()(manager)

  def refreshable[T](implicit
    manager: SessionManager[T],
    rememberMeStorage: RememberMeStorage[T],
    ec: ExecutionContext): Refreshable[T] =
    new Refreshable[T]()(manager, rememberMeStorage, ec)
}

object ClientSessionDirectives extends ClientSessionDirectives

trait OneOffSessionDirectives {
  private[session] def setOneOffSession[T](sc: SessionContinuity[T], v: T): Directive0 =
    setCookie(sc.manager.clientSession.createCookie(v))

  private[session] def oneOffSession[T](sc: SessionContinuity[T]): Directive1[SessionResult[T]] =
    optionalCookie(sc.manager.config.clientSessionCookieConfig.name)
      .map {
        case Some(cookie) => sc.manager.clientSession.decode(cookie.value)
        case None => SessionResult.NoSession
      }

  private[session] def invalidateOneOffSession[T](sc: SessionContinuity[T]): Directive0 =
    deleteCookie(sc.manager.clientSession.createCookieWithValue("").copy(maxAge = None))
}

trait RefreshableSessionDirectives { this: OneOffSessionDirectives =>
  private[session] def setRefreshableSession[T](sc: Refreshable[T], v: T): Directive0 = {
    setOneOffSession(sc, v) & setRememberMeCookie(sc, v)
  }

  private[session] def refreshableSession[T](sc: Refreshable[T]): Directive1[SessionResult[T]] = {
    import sc.ec
    oneOffSession(sc).flatMap {
      case SessionResult.NoSession =>
        optionalCookie(sc.rememberMeManager.config.rememberMeCookieConfig.name).flatMap {
          case None => provide(SessionResult.NoSession)
          case Some(cookie) =>
            onSuccess(sc.rememberMeManager.sessionFromCookie(cookie.value))
              .flatMap {
                case s @ SessionResult.CreatedFromToken(session) =>
                  setRefreshableSession(sc, session) & provide(s: SessionResult[T])
                case s => provide(s)
              }
        }
      case s => provide(s)
    }
  }

  private[session] def invalidateRefreshableSession[T](sc: Refreshable[T]): Directive0 = {
    import sc.ec
    invalidateOneOffSession(sc) & deleteCookie(sc.rememberMeManager.createCookie("").copy(maxAge = None)) & {
      optionalCookie(sc.rememberMeManager.config.rememberMeCookieConfig.name).flatMap {
        case None => pass
        case Some(cookie) => onSuccess(sc.rememberMeManager.removeToken(cookie.value))
      }
    }
  }

  private def setRememberMeCookie[T](sc: Refreshable[T], v: T): Directive0 = {
    import sc.ec
    optionalCookie(sc.rememberMeManager.config.rememberMeCookieConfig.name).flatMap { existing =>
      val createCookie = sc.rememberMeManager.rotateToken(v, existing.map(_.value))
        .map(sc.rememberMeManager.createCookie)

      onSuccess(createCookie).flatMap(c => setCookie(c))
    }
  }
}