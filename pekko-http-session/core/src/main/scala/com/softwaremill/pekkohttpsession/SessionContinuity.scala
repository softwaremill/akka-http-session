package com.softwaremill.pekkohttpsession

import scala.concurrent.ExecutionContext

sealed trait SessionContinuity[T] {
  def manager: SessionManager[T]
  def clientSessionManager = manager.clientSessionManager
}

class OneOff[T] private[pekkohttpsession] (implicit val manager: SessionManager[T]) extends SessionContinuity[T]

class Refreshable[T] private[pekkohttpsession] (implicit
                                       val manager: SessionManager[T],
                                       val refreshTokenStorage: RefreshTokenStorage[T],
                                       val ec: ExecutionContext)
    extends SessionContinuity[T] {
  val refreshTokenManager = manager.createRefreshTokenManager(refreshTokenStorage)
}
