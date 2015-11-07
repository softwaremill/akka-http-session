package com.softwaremill.session

import java.util.concurrent.TimeUnit

import akka.http.scaladsl.server.AuthorizationFailedRejection

import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

import akka.http.scaladsl.model.headers.HttpCookie

class SessionManager[T](val config: SessionConfig, crypto: Crypto = DefaultCrypto)(implicit sessionSerializer: SessionSerializer[T]) { manager =>

  val clientSession: ClientSessionManager[T] = new ClientSessionManager[T] {
    override def config = manager.config
    override def sessionSerializer = manager.sessionSerializer
    override def nowMillis = manager.nowMillis
    override def crypto = manager.crypto
  }

  val csrf: CsrfManager[T] = new CsrfManager[T] {
    override def config = manager.config
  }

  def refreshToken(_storage: RefreshTokenStorage[T]): RefreshTokenManager[T] = new RefreshTokenManager[T] {
    override def config = manager.config
    override def nowMillis = manager.nowMillis
    override def crypto = manager.crypto
    override def storage = _storage
  }

  def nowMillis = System.currentTimeMillis()
}

// Partially based on the implementation from Play! [[https://github.com/playframework]]
// see https://github.com/playframework/playframework/blob/master/framework/src/play/src/main/scala/play/api/mvc/Http.scala
trait ClientSessionManager[T] {
  def config: SessionConfig
  def sessionSerializer: SessionSerializer[T]
  def crypto: Crypto
  def nowMillis: Long

  def createCookie(data: T) = createCookieWithValue(encode(data))

  def createCookieWithValue(value: String) = HttpCookie(
    name = config.clientSessionCookieConfig.name,
    value = value,
    expires = None,
    maxAge = config.clientSessionCookieConfig.maxAge,
    domain = config.clientSessionCookieConfig.domain,
    path = config.clientSessionCookieConfig.path,
    secure = config.clientSessionCookieConfig.secure,
    httpOnly = config.clientSessionCookieConfig.httpOnly
  )

  def encode(data: T): String = {
    // adding an "x" so that the string is never empty, even if there's no data
    val serialized = "x" + sessionSerializer.serialize(data)

    val withExpiry = config.clientSessionMaxAgeSeconds.fold(serialized) { maxAge =>
      val expiry = nowMillis + maxAge * 1000L
      s"$expiry-$serialized"
    }

    val encrypted = if (config.clientSessionEncryptData) crypto.encrypt(withExpiry, config.serverSecret) else withExpiry

    s"${crypto.sign(serialized, config.serverSecret)}-$encrypted"
  }

  def decode(data: String): SessionResult[T] = {
    def extractExpiry(data: String): (Long, String) = {
      config.clientSessionMaxAgeSeconds.fold((Long.MaxValue, data)) { maxAge =>
        val splitted = data.split("-", 2)
        (splitted(0).toLong, splitted(1))
      }
    }

    try {
      val splitted = data.split("-", 2)
      val decrypted = if (config.clientSessionEncryptData) crypto.decrypt(splitted(1), config.serverSecret) else splitted(1)

      val (expiry, serialized) = extractExpiry(decrypted)

      if (nowMillis > expiry) {
        SessionResult.Expired
      }
      else if (!SessionUtil.constantTimeEquals(splitted(0), crypto.sign(serialized, config.serverSecret))) {
        SessionResult.Corrupt
      }
      else {
        SessionResult.DecodedFromCookie(sessionSerializer.deserialize(serialized.substring(1))) // removing the x
      }
    }
    catch {
      // fail gracefully is the session cookie is corrupted
      case NonFatal(_) => SessionResult.Corrupt
    }
  }

  def cookieMissingRejection = AuthorizationFailedRejection
}

trait CsrfManager[T] {
  def config: SessionConfig

  def tokenInvalidRejection = AuthorizationFailedRejection
  def createToken(): String = SessionUtil.randomString(64)

  def createCookie() = HttpCookie(
    name = config.csrfCookieConfig.name,
    value = createToken(),
    expires = None,
    maxAge = config.csrfCookieConfig.maxAge,
    domain = config.csrfCookieConfig.domain,
    path = config.csrfCookieConfig.path,
    secure = config.csrfCookieConfig.secure,
    httpOnly = config.csrfCookieConfig.httpOnly
  )
}

trait RefreshTokenManager[T] {
  def config: SessionConfig
  def crypto: Crypto
  def nowMillis: Long
  def storage: RefreshTokenStorage[T]

  def createSelector(): String = SessionUtil.randomString(16)
  def createToken(): String = SessionUtil.randomString(64)

  def decodeSelectorAndToken(cookieValue: String): Option[(String, String)] = {
    val s = cookieValue.split(":", 2)
    if (s.length == 2) Some((s(0), s(1))) else None
  }

  def encodeSelectorAndToken(selector: String, token: String): String = s"$selector:$token"

  /**
   * Creates and stores a new token, removing the old one after a configured period of time, if it exists.
   */
  def rotateToken(session: T, existing: Option[String])(implicit ec: ExecutionContext): Future[String] = {

    val selector = createSelector()
    val token = createToken()

    val storeFuture = storage.store(new RefreshTokenData[T](
      forSession = session,
      selector = selector,
      tokenHash = crypto.hash(token),
      expires = nowMillis + config.refreshTokenCookieConfig.maxAge.getOrElse(0L) * 1000L
    )).map(_ => encodeSelectorAndToken(selector, token))

    existing.flatMap(decodeSelectorAndToken).foreach {
      case (s, _) =>
        storage.schedule(Duration(config.removeUsedRefreshTokenAfter, TimeUnit.SECONDS)) {
          storage.remove(s)
        }
    }

    storeFuture
  }

  def createCookie(value: String) = HttpCookie(
    name = config.refreshTokenCookieConfig.name,
    value = value,
    expires = None,
    maxAge = config.refreshTokenCookieConfig.maxAge,
    domain = config.refreshTokenCookieConfig.domain,
    path = config.refreshTokenCookieConfig.path,
    secure = config.refreshTokenCookieConfig.secure,
    httpOnly = config.refreshTokenCookieConfig.httpOnly
  )

  def sessionFromCookie(cookieValue: String)(implicit ec: ExecutionContext): Future[SessionResult[T]] = {
    decodeSelectorAndToken(cookieValue) match {
      case Some((selector, token)) =>
        storage.lookup(selector).flatMap {
          case Some(lookupResult) =>
            if (lookupResult.expires < nowMillis) {
              storage.remove(selector).map(_ => SessionResult.Expired)
            }
            else if (!SessionUtil.constantTimeEquals(crypto.hash(token), lookupResult.tokenHash)) {
              storage.remove(selector).map(_ => SessionResult.Corrupt)
            }
            else {
              Future.successful(SessionResult.CreatedFromToken(lookupResult.createSession()))
            }

          case None =>
            Future.successful(SessionResult.TokenNotFound)
        }
      case None => Future.successful(SessionResult.Corrupt)
    }
  }

  def removeToken(cookieValue: String)(implicit ec: ExecutionContext): Future[Unit] = {
    decodeSelectorAndToken(cookieValue) match {
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

  case class DecodedFromCookie[T](session: T) extends SessionResult[T] with SessionValue[T]
  case class CreatedFromToken[T](session: T) extends SessionResult[T] with SessionValue[T]

  case object NoSession extends SessionResult[Nothing] with NoSessionValue[Nothing]
  case object TokenNotFound extends SessionResult[Nothing] with NoSessionValue[Nothing]
  case object Expired extends SessionResult[Nothing] with NoSessionValue[Nothing]
  case object Corrupt extends SessionResult[Nothing] with NoSessionValue[Nothing]
}
