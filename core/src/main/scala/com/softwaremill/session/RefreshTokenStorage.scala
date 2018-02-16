package com.softwaremill.session

import java.time.Instant

import scala.collection.concurrent
import scala.concurrent.Future
import scala.concurrent.duration.Duration

trait RefreshTokenStorage[T] {
  def lookup(selector: String): Future[Option[RefreshTokenLookupResult[T]]]
  def store(data: RefreshTokenData[T]): Future[Unit]
  def remove(selector: String): Future[Unit]
  def schedule[S](after: Duration)(op: => Future[S]): Unit
}

case class RefreshTokenData[T](
  forSession: T,
  selector: String,
  tokenHash: String,
  /**
   * Timestamp
   */
  expires: Long)

case class RefreshTokenLookupResult[T](
  tokenHash: String,
  /**
   * Timestamp
   */
  expires: Long,
  createSession: () => T)

/**
 * Useful for testing.
 */
trait InMemoryRefreshTokenStorage[T] extends RefreshTokenStorage[T] {
  case class Store(session: T, tokenHash: String, expires: Long)
  private val _store = concurrent.TrieMap[String, Store]()

  def store: Map[String, Store] = _store.toMap

  override def lookup(selector: String): Future[Option[RefreshTokenLookupResult[T]]] = {
    Future.successful {
      val r = _store.get(selector).map(s => RefreshTokenLookupResult[T](s.tokenHash, s.expires,
        () => s.session))
      log(s"Looking up token for selector: $selector, found: ${r.isDefined}")
      r
    }
  }

  override def store(data: RefreshTokenData[T]): Future[Unit] = {
    log(s"Storing token for selector: ${data.selector}, user: ${data.forSession}, expires: ${
      Instant.ofEpochMilli(data.expires)
    }, now: ${Instant.ofEpochMilli(System.currentTimeMillis())}")
    Future.successful(_store.put(data.selector, Store(data.forSession, data.tokenHash, data.expires)))
  }

  override def remove(selector: String): Future[Unit] = {
    log(s"Removing token for selector: $selector")
    Future.successful(_store.remove(selector))
  }

  override def schedule[S](after: Duration)(op: => Future[S]): Unit = {
    log("Running scheduled operation immediately")
    op
    Future.successful(())
  }

  def log(msg: String): Unit
}