package com.softwaremill.session

import akka.http.scaladsl.server.{Directive1, Directive0}
import akka.http.scaladsl.server.Directives._

trait ClientSessionDirectives {
  def setSession[T](magnet: SessionManagerMagnet[T, T]): Directive0 =
    setCookie(magnet.manager.createClientSessionCookie(magnet.input))

  def optionalSession[T](magnet: SessionManagerMagnet[T, Unit]): Directive1[Option[T]] =
    optionalCookie(magnet.manager.clientSessionCookieName).map(_.flatMap(p => magnet.manager.decode(p.value)))

  def requiredSession[T](magnet: SessionManagerMagnet[T, Unit]): Directive1[T] =
    optionalSession(magnet).flatMap {
      case None => reject(magnet.manager.sessionCookieMissingRejection)
      case Some(data) => provide(data)
    }

  def invalidateSession[T](magnet: SessionManagerMagnet[T, Unit]): Directive0 =
    deleteCookie(magnet.manager.createClientSessionCookieWithValue(""))

  /**
   * Sets the session cookie again with the same data. Useful when using the [[SessionConfig.clientSessionMaxAgeSeconds]]
   * option, as it sets the expiry date anew.
   */
  def touchOptionalSession[T](magnet: SessionManagerMagnet[T, Unit]): Directive1[Option[T]] = {
    import magnet.manager
    optionalSession(magnet).flatMap { d => d.fold(pass)(setSession(_)) & provide(d) }
  }

  /**
   * Sets the session cookie again with the same data. Useful when using the [[SessionConfig.clientSessionMaxAgeSeconds]]
   * option, as it sets the expiry date anew.
   */
  def touchRequiredSession[T](magnet: SessionManagerMagnet[T, Unit]): Directive1[T] = {
    import magnet.manager
    requiredSession(magnet).flatMap { d => setSession(d) & provide(d) }
  }
}

object ClientSessionDirectives extends ClientSessionDirectives
