package com.softwaremill.session

import akka.http.scaladsl.server.{Directive1, Directive0}
import akka.http.scaladsl.server.Directives._

trait ClientSessionDirectives {
  def setSession(magnet: SessionManagerMagnet[SessionData]): Directive0 =
    setCookie(magnet.manager.createClientSessionCookie(magnet.input))

  def optionalSession(magnet: SessionManagerMagnet[Unit]): Directive1[Option[SessionData]] =
    optionalCookie(magnet.manager.clientSessionCookieName).map(_.flatMap(p => magnet.manager.decode(p.value)))

  def requiredSession(magnet: SessionManagerMagnet[Unit]): Directive1[SessionData] =
    optionalSession(magnet).flatMap {
      case None => reject(magnet.manager.sessionCookieMissingRejection)
      case Some(data) => provide(data)
    }

  def invalidateSession(magnet: SessionManagerMagnet[Unit]): Directive0 =
    deleteCookie(magnet.manager.createClientSessionCookieWithValue(""))

  /**
   * Sets the session cookie again with the same data. Useful when using the [[SessionConfig.clientSessionMaxAgeSeconds]]
   * option, as it sets the expiry date anew.
   */
  def touchOptionalSession(magnet: SessionManagerMagnet[Unit]): Directive1[Option[SessionData]] = {
    import magnet.manager
    optionalSession(magnet).flatMap { d => d.fold(pass)(setSession(_)) & provide(d) }
  }

  /**
   * Sets the session cookie again with the same data. Useful when using the [[SessionConfig.clientSessionMaxAgeSeconds]]
   * option, as it sets the expiry date anew.
   */
  def touchRequiredSession(magnet: SessionManagerMagnet[Unit]): Directive1[SessionData] = {
    import magnet.manager
    requiredSession(magnet).flatMap { d => setSession(d) & provide(d) }
  }
}

object ClientSessionDirectives extends ClientSessionDirectives
