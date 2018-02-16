package com.softwaremill.session

import java.util.concurrent.TimeUnit

import akka.http.scaladsl.model.headers.{HttpCookie, RawHeader}
import akka.http.scaladsl.server.AuthorizationFailedRejection

import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}

class SessionManager[T](val config: SessionConfig)(implicit sessionEncoder: SessionEncoder[T]) { manager =>

  val clientSessionManager: ClientSessionManager[T] = new ClientSessionManager[T] {
    override def config = manager.config
    override def sessionEncoder = manager.sessionEncoder
    override def nowMillis = manager.nowMillis
  }

  val csrfManager: CsrfManager[T] = new CsrfManager[T] {
    override def config = manager.config
  }

  def createRefreshTokenManager(_storage: RefreshTokenStorage[T]): RefreshTokenManager[T] = new RefreshTokenManager[T] {
    override def config = manager.config
    override def nowMillis = manager.nowMillis
    override def storage = _storage
  }

  def nowMillis = System.currentTimeMillis()
}

// Partially based on the implementation from Play! [[https://github.com/playframework]]
// see https://github.com/playframework/playframework/blob/master/framework/src/play/src/main/scala/play/api/mvc/Http.scala
trait ClientSessionManager[T] {
  def config: SessionConfig
  def sessionEncoder: SessionEncoder[T]
  def nowMillis: Long

  def createCookie(data: T) = createCookieWithValue(encode(data))

  def createCookieWithValue(value: String) = HttpCookie(
    name = config.sessionCookieConfig.name,
    value = value,
    expires = None,
    maxAge = None,
    domain = config.sessionCookieConfig.domain,
    path = config.sessionCookieConfig.path,
    secure = config.sessionCookieConfig.secure,
    httpOnly = config.sessionCookieConfig.httpOnly)

  def encode(data: T): String = sessionEncoder.encode(data, nowMillis, config)

  def createHeader(data: T) = createHeaderWithValue(encode(data))

  def createHeaderWithValue(value: String) = RawHeader(
    name = config.sessionHeaderConfig.sendToClientHeaderName,
    value = value)

  def decode(data: String): SessionResult[T] = {
    sessionEncoder.decode(data, config).map { dr =>
      val expired = config.sessionMaxAgeSeconds.fold(false)(_ => nowMillis > dr.expires.getOrElse(Long.MaxValue))
      if (expired) {
        SessionResult.Expired
      }
      else if (!dr.signatureMatches) {
        SessionResult.Corrupt(new RuntimeException("Corrupt signature"))
      }
      else if (dr.isLegacy) {
        SessionResult.DecodedLegacy(dr.t)
      }
      else {
        SessionResult.Decoded(dr.t)
      }
    }.recover { case t: Exception => SessionResult.Corrupt(t) }.get
  }

  def sessionMissingRejection = AuthorizationFailedRejection
}

trait CsrfManager[T] {
  def config: SessionConfig

  def tokenInvalidRejection = AuthorizationFailedRejection

  def createCookie() = HttpCookie(
    name = config.csrfCookieConfig.name,
    value = createToken(),
    expires = None,
    domain = config.csrfCookieConfig.domain,
    path = config.csrfCookieConfig.path,
    secure = config.csrfCookieConfig.secure,
    httpOnly = config.csrfCookieConfig.httpOnly)

  def createToken(): String = SessionUtil.randomString(64)
}

trait RefreshTokenManager[T] {
  def config: SessionConfig
  def nowMillis: Long
  def storage: RefreshTokenStorage[T]

  /**
   * Creates and stores a new token, removing the old one after a configured period of time, if it exists.
   */
  def rotateToken(session: T, existing: Option[String])(implicit ec: ExecutionContext): Future[String] = {

    val selector = createSelector()
    val token = createToken()

    val storeFuture = storage.store(new RefreshTokenData[T](
      forSession = session,
      selector = selector,
      tokenHash = Crypto.hash_SHA256(token),
      expires = nowMillis + config.refreshTokenMaxAgeSeconds * 1000L)).map(_ => encodeSelectorAndToken(selector, token))

    existing.flatMap(decodeSelectorAndToken).foreach {
      case (s, _) =>
        storage.schedule(Duration(config.removeUsedRefreshTokenAfter, TimeUnit.SECONDS)) {
          storage.remove(s)
        }
    }

    storeFuture
  }

  def createSelector(): String = SessionUtil.randomString(16)

  def createToken(): String = SessionUtil.randomString(64)

  def encodeSelectorAndToken(selector: String, token: String): String = s"$selector:$token"

  def createCookie(value: String) = HttpCookie(
    name = config.refreshTokenCookieConfig.name,
    value = value,
    expires = None,
    maxAge = Some(config.refreshTokenMaxAgeSeconds),
    domain = config.refreshTokenCookieConfig.domain,
    path = config.refreshTokenCookieConfig.path,
    secure = config.refreshTokenCookieConfig.secure,
    httpOnly = config.refreshTokenCookieConfig.httpOnly)

  def createHeader(value: String) = RawHeader(
    name = config.refreshTokenHeaderConfig.sendToClientHeaderName,
    value = value)

  def sessionFromValue(value: String)(implicit ec: ExecutionContext): Future[SessionResult[T]] = {
    decodeSelectorAndToken(value) match {
      case Some((selector, token)) =>
        storage.lookup(selector).flatMap {
          case Some(lookupResult) =>
            if (lookupResult.expires < nowMillis) storage.remove(selector).map(_ => SessionResult.Expired)
            else if (!SessionUtil.constantTimeEquals(Crypto.hash_SHA256(token), lookupResult.tokenHash))
              storage.remove(selector).map(_ => SessionResult.Corrupt(new RuntimeException("Corrupt token hash")))
            else Future.successful(SessionResult.CreatedFromToken(lookupResult.createSession()))

          case None => Future.successful(SessionResult.TokenNotFound)
        }
      case None => Future.successful(SessionResult.Corrupt(new RuntimeException("Cannot decode selector/token")))
    }
  }

  def decodeSelectorAndToken(value: String): Option[(String, String)] = {
    val s = value.split(":", 2)
    if (s.length == 2) Some((s(0), s(1))) else None
  }

  def removeToken(value: String)(implicit ec: ExecutionContext): Future[Unit] = {
    decodeSelectorAndToken(value) match {
      case Some((s, _)) => storage.remove(s)
      case None => Future.successful(())
    }
  }
}

sealed trait SessionResult[+T] {
  def toOption: Option[T]
}

object SessionResult {
  trait SessionValue[T] extends SessionResult[T] {
    def session: T
    def toOption: Option[T] = Some(session)
  }
  trait NoSessionValue[T] extends SessionResult[T] {
    def toOption: Option[T] = None
  }

  case class Decoded[T](session: T) extends SessionResult[T] with SessionValue[T]
  case class DecodedLegacy[T](session: T) extends SessionResult[T] with SessionValue[T]
  case class CreatedFromToken[T](session: T) extends SessionResult[T] with SessionValue[T]
  case class Corrupt(e: Exception) extends SessionResult[Nothing] with NoSessionValue[Nothing]

  case object NoSession extends SessionResult[Nothing] with NoSessionValue[Nothing]
  case object TokenNotFound extends SessionResult[Nothing] with NoSessionValue[Nothing]
  case object Expired extends SessionResult[Nothing] with NoSessionValue[Nothing]
}