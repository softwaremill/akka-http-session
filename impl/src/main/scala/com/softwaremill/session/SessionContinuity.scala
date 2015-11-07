package com.softwaremill.session

import scala.concurrent.ExecutionContext

sealed trait SessionContinuity[T] {
  def manager: SessionManager[T]
}

class OneOff[T] private[session] (implicit val manager: SessionManager[T]) extends SessionContinuity[T]

class Refreshable[T] private[session] (implicit
  val manager: SessionManager[T],
    val rememberMeStorage: RememberMeStorage[T],
    val ec: ExecutionContext) extends SessionContinuity[T] {
  val rememberMeManager = manager.rememberMe(rememberMeStorage)
}
