package com.softwaremill.session

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive0, Directive1}

import scala.concurrent.ExecutionContext

/**
 * Manages cookie-based sessions with optional refresh tokens. A refresh token is written to a separate cookie.
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
      case _: OneOff[T] => setOneOffSession(sc, st, v)
      case r: Refreshable[T] => setRefreshableSession(r, st, v)
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
      case _: OneOff[T] => oneOffSession(sc, st)
      case r: Refreshable[T] => refreshableSession(r, st)
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
      case _: OneOff[T] => invalidateOneOffSession(sc, st)
      case r: Refreshable[T] => invalidateOneOffSession(sc, st) & invalidateRefreshableSession(r, st)
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
      case None => reject(sc.clientSessionManager.sessionMissingRejection)
      case Some(data) => provide(data)
    }

  /**
   * Sets the session cookie again with the same data. Useful when using the [[SessionConfig.sessionMaxAgeSeconds]]
   * option, as it sets the expiry date anew.
   */
  def touchOptionalSession[T](sc: SessionContinuity[T], st: GetSessionTransport): Directive1[Option[T]] = {
    optionalSession(sc, st).flatMap { d => d.fold(pass)(s => setOneOffSessionSameTransport(sc, st, s)) & provide(d) }
  }

  /**
   * Sets the session cookie again with the same data. Useful when using the [[SessionConfig.sessionMaxAgeSeconds]]
   * option, as it sets the expiry date anew.
   */
  def touchRequiredSession[T](sc: SessionContinuity[T], st: GetSessionTransport): Directive1[T] = {
    requiredSession(sc, st).flatMap { d => setOneOffSessionSameTransport(sc, st, d) & provide(d) }
  }

}

object SessionDirectives extends SessionDirectives

object SessionOptions {
  def oneOff[T](implicit manager: SessionManager[T]): OneOff[T] = new OneOff[T]()(manager)

  def refreshable[T](implicit
    manager: SessionManager[T],
    refreshTokenStorage: RefreshTokenStorage[T],
    ec: ExecutionContext): Refreshable[T] =
    new Refreshable[T]()(manager, refreshTokenStorage, ec)

  def usingCookies = CookieST
  def usingHeaders = HeaderST
  def usingCookiesOrHeaders = CookieOrHeaderST
}

trait OneOffSessionDirectives {
  private[session] def setOneOffSession[T](sc: SessionContinuity[T], st: SetSessionTransport, v: T): Directive0 =
    st match {
      case CookieST => setCookie(sc.clientSessionManager.createCookie(v))
      case HeaderST => respondWithHeader(sc.clientSessionManager.createHeader(v))
    }

  private[session] def setOneOffSessionSameTransport[T](sc: SessionContinuity[T], st: GetSessionTransport, v: T): Directive0 =
    read(sc, st).flatMap {
      case None => pass
      case Some((_, setSt)) => setOneOffSession(sc, setSt, v)
    }

  private def readCookie[T](sc: SessionContinuity[T]) =
    optionalCookie(sc.manager.config.sessionCookieConfig.name)
      .map(_.map(c => (c.value, CookieST: SetSessionTransport)))
  private def readHeader[T](sc: SessionContinuity[T]) =
    optionalHeaderValueByName(sc.manager.config.sessionHeaderConfig.getFromClientHeaderName)
      .map(_.map(h => (h, HeaderST: SetSessionTransport)))
  private def read[T](sc: SessionContinuity[T], st: GetSessionTransport): Directive1[Option[(String, SetSessionTransport)]] =
    st match {
      case CookieST => readCookie(sc)
      case HeaderST => readHeader(sc)
      case CookieOrHeaderST => readCookie(sc).flatMap(_.fold(readHeader(sc))(v => provide(Some(v))))
    }

  private[session] def oneOffSession[T](sc: SessionContinuity[T], st: GetSessionTransport): Directive1[SessionResult[T]] =
    read(sc, st).flatMap {
      case None => provide(SessionResult.NoSession)
      case Some((v, setSt)) =>
        sc.clientSessionManager.decode(v) match {
          case s: SessionResult.DecodedLegacy[T] => setOneOffSession(sc, setSt, s.session) & provide(s: SessionResult[T])
          case s => provide(s)
        }
    }

  private[session] def invalidateOneOffSession[T](sc: SessionContinuity[T], st: GetSessionTransport): Directive0 = {
    readCookie(sc).flatMap {
      case None =>
        readHeader(sc).flatMap {
          case None => pass
          case Some(_) => respondWithHeader(sc.clientSessionManager.createHeaderWithValue(""))
        }

      case Some(_) => deleteCookie(sc.clientSessionManager.createCookieWithValue("").copy(maxAge = None))
    }
  }
}

trait RefreshableSessionDirectives { this: OneOffSessionDirectives =>
  private[session] def setRefreshableSession[T](sc: Refreshable[T], st: SetSessionTransport, v: T): Directive0 = {
    setOneOffSession(sc, st, v) & setRefreshToken(sc, st, v)
  }

  private def readCookie[T](sc: SessionContinuity[T]) =
    optionalCookie(sc.manager.config.refreshTokenCookieConfig.name)
      .map(_.map(c => (c.value, CookieST: SetSessionTransport)))
  private def readHeader[T](sc: SessionContinuity[T]) =
    optionalHeaderValueByName(sc.manager.config.refreshTokenHeaderConfig.getFromClientHeaderName)
      .map(_.map(h => (h, HeaderST: SetSessionTransport)))
  private def read[T](sc: SessionContinuity[T], st: GetSessionTransport): Directive1[Option[(String, SetSessionTransport)]] =
    st match {
      case CookieST => readCookie(sc)
      case HeaderST => readHeader(sc)
      case CookieOrHeaderST => readCookie(sc).flatMap(_.fold(readHeader(sc))(v => provide(Some(v))))
    }

  private[session] def refreshableSession[T](sc: Refreshable[T], st: GetSessionTransport): Directive1[SessionResult[T]] = {
    import sc.ec
    oneOffSession(sc, st).flatMap {
      case SessionResult.NoSession | SessionResult.Expired =>
        read(sc, st).flatMap {
          case None => provide(SessionResult.NoSession)
          case Some((v, setSt)) =>
            onSuccess(sc.refreshTokenManager.sessionFromValue(v))
              .flatMap {
                case s @ SessionResult.CreatedFromToken(session) =>
                  setRefreshableSession(sc, setSt, session) & provide(s: SessionResult[T])
                case s => provide(s)
              }
        }
      case s => provide(s)
    }
  }

  private[session] def invalidateRefreshableSession[T](sc: Refreshable[T], st: GetSessionTransport): Directive0 = {
    import sc.ec
    read(sc, st).flatMap {
      case None => pass
      case Some((v, setSt)) =>
        val deleteTokenOnClient = setSt match {
          case CookieST => deleteCookie(sc.refreshTokenManager.createCookie("").copy(maxAge = None))
          case HeaderST => respondWithHeader(sc.refreshTokenManager.createHeader(""))
        }

        deleteTokenOnClient &
          onSuccess(sc.refreshTokenManager.removeToken(v))
    }
  }

  private def setRefreshToken[T](sc: Refreshable[T], st: SetSessionTransport, v: T): Directive0 = {
    import sc.ec
    read(sc, st).flatMap { existing =>
      val newToken = sc.refreshTokenManager.rotateToken(v, existing.map(_._1))

      st match {
        case CookieST =>
          val createCookie = newToken.map(sc.refreshTokenManager.createCookie)
          onSuccess(createCookie).flatMap(c => setCookie(c))
        case HeaderST =>
          val createHeader = newToken.map(sc.refreshTokenManager.createHeader)
          onSuccess(createHeader).flatMap(c => respondWithHeader(c))
      }
    }
  }
}