package com.softwaremill.session

trait SessionDirectives
  extends ClientSessionDirectives
  with CsrfDirectives

object SessionDirectives extends SessionDirectives

trait SessionManagerMagnet[T, In] {
  implicit def manager: SessionManager[T]
  def input: In
}

object SessionManagerMagnet {
  implicit def apply[T, In](_input: In)(implicit _manager: SessionManager[T]): SessionManagerMagnet[T, In] =
    new SessionManagerMagnet[T, In] {
      override val manager = _manager
      override val input = _input
    }

  implicit def apply[T](_input: Unit)(implicit _manager: SessionManager[T]): SessionManagerMagnet[T, CsrfCheckMode] =
    new SessionManagerMagnet[T, CsrfCheckMode] {
      override val manager = _manager
      override val input = CheckHeader
    }
}
