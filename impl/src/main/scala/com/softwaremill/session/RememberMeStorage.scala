package com.softwaremill.session

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration.Duration

trait RememberMeStorage[T] {
  def lookup(selector: String): Future[Option[RememberMeLookupResult[T]]]
  def store(data: RememberMeData[T]): Future[Unit]
  def remove(selector: String): Future[Unit]
  def schedule[S](after: Duration)(op: => Future[S]): Unit
}

case class RememberMeData[T](
  forSession: T,
  selector: String,
  tokenHash: String,
  /**
   * Timestamp
   */
  expires: Long
)

case class RememberMeLookupResult[T](
  tokenHash: String,
  /**
   * Timestamp
   */
  expires: Long,
  createSession: () => T
)

/**
 * Useful for testing.
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

  override def schedule[S](after: Duration)(op: => Future[S]) = {
    log("Running scheduled operation immediately")
    op
    Future.successful(())
  }

  def log(msg: String): Unit
}