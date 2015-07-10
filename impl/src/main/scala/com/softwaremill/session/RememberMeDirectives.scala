package com.softwaremill.session

import akka.http.scaladsl.server.{Directive1, Directive0}
import akka.http.scaladsl.server.Directives._
import ClientSessionDirectives._

import scala.concurrent.ExecutionContext

/**
 * Contains directives analogous to the ones from [[ClientSessionDirectives]], but in a *persistent* variant.
 * A persistent session consists of a regular, session-cookie-based session and a cookie containing a
 * remember-me token.
 */
trait RememberMeDirectives {
  /**
   * Same as [[ClientSessionDirectives.setSession]], plus also generates a new remember me token (removing old ones)
   * and stores it in the remember me cookie.
   */
  def setPersistentSession[T](magnet: RememberMeStorageMagnet[T, T]): Directive0 = {
    import magnet._
    setSession(magnet.input) & setRememberMeCookie(magnet)
  }

  /**
   * Same as [[ClientSessionDirectives.optionalSession]], but also tries to create a new session based on the remember
   * me cookie, if no session is present.
   */
  def optionalPersistentSession[T](magnet: RememberMeStorageMagnet[T, Unit]): Directive1[Option[T]] = {
    import magnet._
    optionalSession().flatMap {
      case s @ Some(_) => provide(s)
      case None => optionalCookie(magnet.rememberMeManager.config.rememberMeCookieConfig.name).flatMap {
        case None => provide(None)
        case Some(cookie) =>
          onSuccess(magnet.rememberMeManager.sessionFromCookie(cookie.value))
            .flatMap {
              case None => provide(None)
              case s @ Some(session) => setPersistentSession(session) & provide(s: Option[T])
            }
      }
    }
  }

  /**
   * Same as [[ClientSessionDirectives.requiredSession]], but also tries to create a new session based on the remember
   * me cookie, if no session is present.
   */
  def requiredPersistentSession[T](magnet: RememberMeStorageMagnet[T, Unit]): Directive1[T] =
    optionalPersistentSession(magnet).flatMap {
      case None => reject(magnet.clientSessionManager.cookieMissingRejection)
      case Some(data) => provide(data)
    }

  /**
   * Same as [[ClientSessionDirectives.invalidateSession)]], but also removes the remember me cookie and the remember
   * me token (from the client and token store).
   */
  def invalidatePersistentSession[T](magnet: RememberMeStorageMagnet[T, Unit]): Directive0 = {
    import magnet._
    invalidateSession() & deleteCookie(magnet.rememberMeManager.createCookie("").copy(maxAge = None)) & {
      optionalCookie(magnet.rememberMeManager.config.rememberMeCookieConfig.name).flatMap {
        case None => pass
        case Some(cookie) => onSuccess(magnet.rememberMeManager.removeToken(cookie.value))
      }
    }
  }

  /**
   * Same as [[ClientSessionDirectives.touchOptionalSession]]; if the user session is already present, keeps the same
   * remember me token if one is present.
   */
  def touchOptionalPersistentSession[T](magnet: RememberMeStorageMagnet[T, Unit]): Directive1[Option[T]] = {
    import magnet._
    optionalPersistentSession(magnet).flatMap { d => d.fold(pass)(setSession(_)) & provide(d) }
  }

  /**
   * Same as [[ClientSessionDirectives.touchRequiredSession]]; if the user session is already present, keeps the same
   * remember me token if one is present.
   */
  def touchRequiredPersistentSession[T](magnet: RememberMeStorageMagnet[T, Unit]): Directive1[T] = {
    import magnet._
    requiredPersistentSession(magnet).flatMap { d => setSession(d) & provide(d) }
  }

  def setRememberMeCookie[T](magnet: RememberMeStorageMagnet[T, T]): Directive0 = {
    import magnet._
    optionalCookie(magnet.rememberMeManager.config.rememberMeCookieConfig.name).flatMap { existing =>
      val createCookie = magnet.rememberMeManager.rotateToken(magnet.input, existing.map(_.value))
        .map(magnet.rememberMeManager.createCookie)

      onSuccess(createCookie).flatMap(c => setCookie(c))
    }
  }
}

object RememberMeDirectives extends RememberMeDirectives

trait RememberMeStorageMagnet[T, In] {
  implicit def rememberMeManager: RememberMeManager[T]
  implicit def clientSessionManager: ClientSessionManager[T]
  implicit def ec: ExecutionContext
  def input: In
}

object RememberMeStorageMagnet {
  implicit def forSeparateManagers[T, In](_input: In)(implicit
    _rememberMeManager: RememberMeManager[T],
    _clientSessionManager: ClientSessionManager[T],
    _ec: ExecutionContext): RememberMeStorageMagnet[T, In] =
    new RememberMeStorageMagnet[T, In] {
      override val rememberMeManager = _rememberMeManager
      override val clientSessionManager = _clientSessionManager
      override val ec = _ec
      override val input = _input
    }

  implicit def forSessionManager[T, In](_input: In)(implicit
    _storage: RememberMeStorage[T],
    _manager: SessionManager[T],
    _ec: ExecutionContext): RememberMeStorageMagnet[T, In] =
    new RememberMeStorageMagnet[T, In] {
      override val rememberMeManager = _manager.rememberMe(_storage)
      override val clientSessionManager = _manager.clientSession
      override val ec = _ec
      override val input = _input
    }
}
