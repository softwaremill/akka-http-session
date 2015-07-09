package com.softwaremill.session

import scala.collection.mutable
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

/**
 * In-memory remember me storage implementation. Useful for testing.
 */
trait InMemoryRememberMeStorage[T] extends RememberMeStorage[T] {
  case class Store(session: T, tokenHash: String, expires: Long)
  private val _store = mutable.Map[String, Store]()

  def store: Map[String, Store] = _store.toMap

  override def lookup(selector: String) = {
    Future.successful {
      val r = _store.get(selector).map(s => RememberMeLookupResult[T](s.tokenHash, s.expires,
        () => s.session))
      log(s"Looking up token for selector: $selector, found: ${r.isDefined}")
      r
    }
  }

  override def store(data: RememberMeData[T]) = {
    log(s"Storing token for selector: ${data.selector}, user: ${data.forSession}, " +
      s"expires: ${data.expires}, now: ${System.currentTimeMillis()}")
    Future.successful(_store.put(data.selector, Store(data.forSession, data.tokenHash, data.expires)))
  }

  override def remove(selector: String) = {
    log(s"Removing token for selector: $selector")
    Future.successful(_store.remove(selector))
  }

  def log(msg: String): Unit
}