package com.softwaremill.session

import akka.http.scaladsl.server.{Directive1, Directive0}
import akka.http.scaladsl.server.Directives._
import ClientSessionDirectives._

import scala.concurrent.ExecutionContext

/**
 * Contains directives analogous to the ones from [[ClientSessionDirectives]], but in a *persistent* variant.
 * A persistent session consists of a regular, session-cookie-based session and a cookie containing a
 * remember-me-token.
 */
trait RememberMeDirectives {
  /**
   * Same as [[ClientSessionDirectives.setSession]], plus also generates a new remember me token (removing old ones)
   * and stores it in the remember me cookie.
   */
  def setPersistentSession[T](magnet: RememberMeStorageMagnet[T, T]): Directive0 = {
    import magnet.manager
    setSession(magnet.input) & setRememberMeCookie(magnet)
  }

  /**
   * Same as [[ClientSessionDirectives.optionalSession]], but also tries to create a new session based on the remember
   * me cookie, if no session is present.
   */
  def optionalPersistentSession[T](magnet: RememberMeStorageMagnet[T, Unit]): Directive1[Option[T]] = {
    import magnet.{manager, storage, ec}
    optionalSession().flatMap {
      case s@Some(_) => provide(s)
      case None => optionalCookie(magnet.manager.config.rememberMeCookieConfig.name).flatMap {
        case None => provide(None)
        case Some(cookie) =>
          onSuccess(magnet.manager.sessionFromRememberMeCookie(magnet.storage)(cookie.value))
            .flatMap {
            case None => provide(None)
            case s@Some(session) => setPersistentSession(session) & provide(s: Option[T])
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
      case None => reject(magnet.manager.clientSessionCookieMissingRejection)
      case Some(data) => provide(data)
    }

  /**
   * Same as [[ClientSessionDirectives.invalidateSession)]], but also removes the remember me cookie and the remember
   * me token.
   */
  def invalidatePersistentSession[T](magnet: RememberMeStorageMagnet[T, Unit]): Directive0 = {
    import magnet.{manager, ec}
    invalidateSession() & deleteCookie(magnet.manager.createRememberMeCookie("").copy(maxAge = None)) & {
      optionalCookie(magnet.manager.config.rememberMeCookieConfig.name).flatMap {
        case None => pass
        case Some(cookie) => onSuccess(magnet.manager.removeRememberMeToken(magnet.storage)(cookie.value))
      }
    }
  }

  /**
   * Same as [[ClientSessionDirectives.touchOptionalSession]]; if the user session is already present, keeps the same
   * remember me token if one is present.
   */
  def touchOptionalPersistentSession[T](magnet: RememberMeStorageMagnet[T, Unit]): Directive1[Option[T]] = {
    import magnet.manager
    optionalPersistentSession(magnet).flatMap { d => d.fold(pass)(setSession(_)) & provide(d) }
  }

  /**
   * Same as [[ClientSessionDirectives.touchRequiredSession]]; if the user session is already present, keeps the same
   * remember me token if one is present.
   */
  def touchRequiredPersistentSession[T](magnet: RememberMeStorageMagnet[T, Unit]): Directive1[T] = {
    import magnet.manager
    requiredPersistentSession(magnet).flatMap { d => setSession(d) & provide(d) }
  }

  def setRememberMeCookie[T](magnet: RememberMeStorageMagnet[T, T]): Directive0 = {
    import magnet.ec
    optionalCookie(magnet.manager.config.rememberMeCookieConfig.name).flatMap { existing =>
      val createCookie = magnet.manager.createAndStoreRememberMeToken(magnet.storage)(magnet.input, existing.map(_.value))
        .map(magnet.manager.createRememberMeCookie)

      onSuccess(createCookie).flatMap(c => setCookie(c))
    }
  }
}

object RememberMeDirectives extends RememberMeDirectives

trait RememberMeStorageMagnet[T, In] {
  implicit def storage: RememberMeStorage[T]
  implicit def manager: RememberMeManager[T] with ClientSessionManager[T]
  implicit def ec: ExecutionContext
  def input: In
}

object RememberMeStorageMagnet {
  implicit def apply[T, In](_input: In)
    (implicit _storage: RememberMeStorage[T],
      _manager: RememberMeManager[T] with ClientSessionManager[T],
      _ec: ExecutionContext): RememberMeStorageMagnet[T, In] =
    new RememberMeStorageMagnet[T, In] {
      override val storage = _storage
      override val manager = _manager
      override val ec = _ec
      override val input = _input
    }
}
