package com.softwaremill.session

import scala.concurrent.Future

trait RememberMeStorage[T] {
  def lookup(selector: String): Future[Option[RememberMeLookupResult[T]]]
  def store(data: RememberMeData[T]): Future[Unit]
  def remove(selector: String): Future[Unit]
}

case class RememberMeData[T](
  forSession: T,
  selector: String,
  tokenHash: String,
  /**
   * Timestamp
   */
  expires: Long)

case class RememberMeLookupResult[T](
  tokenHash: String,
  /**
   * Timestamp
   */
  expires: Long,
  createSession: () => T)