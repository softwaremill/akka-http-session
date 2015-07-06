package com.softwaremill.session

trait SessionDirectives
  extends ClientSessionDirectives

object SessionDirectives extends SessionDirectives

trait SessionManagerMagnet[In] {
  implicit def manager: SessionManager
  def input: In
}

object SessionManagerMagnet {
  implicit def apply[In](_input: In)(implicit _manager: SessionManager): SessionManagerMagnet[In] =
    new SessionManagerMagnet[In] {
      override val manager = _manager
      override val input = _input
    }
}
