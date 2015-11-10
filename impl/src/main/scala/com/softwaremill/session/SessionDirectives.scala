package com.softwaremill.session

import akka.http.scaladsl.server.{Directive1, Directive0}
import akka.http.scaladsl.server.Directives._

import scala.concurrent.ExecutionContext

/**
 * Manages cookie-based sessions wiht optional refresh tokens. A refresh token is written to a separate cookie.
 */
trait SessionDirectives extends OneOffSessionDirectives with RefreshableSessionDirectives {
  /**
   * Set the session cookie with the session content. The content is signed, optionally encrypted and with
   * an optional expiry date.
   *
   * If refreshable, generates a new token (removing old ones) and stores it in the refresh token cookie.
   */
  def setSession[T](sc: SessionContinuity[T], st: SetSessionTransport, v: T): Directive0 = {
    sc match {
      case _: OneOff[T] => setOneOffSession(sc, v)
      case r: Refreshable[T] => setRefreshableSession(r, v)
    }
  }

  /**
   * Read a session from the session cookie, wrapped in [[SessionResult]] describing the possible
   * success/failure outcomes.
   *
   * If refreshable, tries to create a new session based on the refresh token cookie.
   */
  def session[T](sc: SessionContinuity[T], st: GetSessionTransport): Directive1[SessionResult[T]] = {
    sc match {
      case _: OneOff[T] => oneOffSession(sc)
      case r: Refreshable[T] => refreshableSession(r)
    }
  }

  /**
   * Invalidate the session cookie.
   *
   * If refreshable, also removes the refresh token cookie and the refresh token token (from the client and token
   * store), if present.
   *
   * Note that you should use `refreshable` if you use refreshable systems even only for some users.
   */
  def invalidateSession[T](sc: SessionContinuity[T], st: GetSessionTransport): Directive0 = {
    sc match {
      case _: OneOff[T] => invalidateOneOffSession(sc)
      case r: Refreshable[T] => invalidateRefreshableSession(r)
    }
  }

  /**
   * Read an optional session from the session cookie.
   */
  def optionalSession[T](sc: SessionContinuity[T], st: GetSessionTransport): Directive1[Option[T]] =
    session(sc, st).map(_.toOption)

  /**
   * Read a required session from the session cookie.
   */
  def requiredSession[T](sc: SessionContinuity[T], st: GetSessionTransport): Directive1[T] =
    optionalSession(sc, st).flatMap {
      case None => reject(sc.manager.clientSession.cookieMissingRejection)
      case Some(data) => provide(data)
    }

  /**
   * Sets the session cookie again with the same data. Useful when using the [[SessionConfig.sessionMaxAgeSeconds]]
   * option, as it sets the expiry date anew.
   */
  def touchOptionalSession[T](sc: SessionContinuity[T], st: GetSessionTransport): Directive1[Option[T]] = {
    optionalSession(sc, st).flatMap { d => d.fold(pass)(s => setOneOffSession(sc, s)) & provide(d) }
  }

  /**
   * Sets the session cookie again with the same data. Useful when using the [[SessionConfig.sessionMaxAgeSeconds]]
   * option, as it sets the expiry date anew.
   */
  def touchRequiredSession[T](sc: SessionContinuity[T], st: GetSessionTransport): Directive1[T] = {
    requiredSession(sc, st).flatMap { d => setOneOffSession(sc, d) & provide(d) }
  }

  def oneOff[T](implicit manager: SessionManager[T]): OneOff[T] = new OneOff[T]()(manager)

  def refreshable[T](implicit
    manager: SessionManager[T],
    refreshTokenStorage: RefreshTokenStorage[T],
    ec: ExecutionContext): Refreshable[T] =
    new Refreshable[T]()(manager, refreshTokenStorage, ec)

  def usingCookies = CookieSessionTransport
  def usingHeaders = HeaderSessionTransport
  def usingCookiesOrHeaders = CookieOrHeaderSessionTransport
}

object SessionDirectives extends SessionDirectives

trait OneOffSessionDirectives {
  private[session] def setOneOffSession[T](sc: SessionContinuity[T], v: T): Directive0 =
    setCookie(sc.manager.clientSession.createCookie(v))

  private[session] def oneOffSession[T](sc: SessionContinuity[T]): Directive1[SessionResult[T]] =
    optionalCookie(sc.manager.config.sessionCookieConfig.name)
      .map {
        case Some(cookie) => sc.manager.clientSession.decode(cookie.value)
        case None => SessionResult.NoSession
      }

  private[session] def invalidateOneOffSession[T](sc: SessionContinuity[T]): Directive0 =
    deleteCookie(sc.manager.clientSession.createCookieWithValue("").copy(maxAge = None))
}

trait RefreshableSessionDirectives { this: OneOffSessionDirectives =>
  private[session] def setRefreshableSession[T](sc: Refreshable[T], v: T): Directive0 = {
    setOneOffSession(sc, v) & setRefreshTokenCookie(sc, v)
  }

  private[session] def refreshableSession[T](sc: Refreshable[T]): Directive1[SessionResult[T]] = {
    import sc.ec
    oneOffSession(sc).flatMap {
      case SessionResult.NoSession | SessionResult.Expired =>
        optionalCookie(sc.refreshTokenManager.config.refreshTokenCookieConfig.name).flatMap {
          case None => provide(SessionResult.NoSession)
          case Some(cookie) =>
            onSuccess(sc.refreshTokenManager.sessionFromCookie(cookie.value))
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
    invalidateOneOffSession(sc) & deleteCookie(sc.refreshTokenManager.createCookie("").copy(maxAge = None)) & {
      optionalCookie(sc.refreshTokenManager.config.refreshTokenCookieConfig.name).flatMap {
        case None => pass
        case Some(cookie) => onSuccess(sc.refreshTokenManager.removeToken(cookie.value))
      }
    }
  }

  private def setRefreshTokenCookie[T](sc: Refreshable[T], v: T): Directive0 = {
    import sc.ec
    optionalCookie(sc.refreshTokenManager.config.refreshTokenCookieConfig.name).flatMap { existing =>
      val createCookie = sc.refreshTokenManager.rotateToken(v, existing.map(_.value))
        .map(sc.refreshTokenManager.createCookie)

      onSuccess(createCookie).flatMap(c => setCookie(c))
    }
  }
}