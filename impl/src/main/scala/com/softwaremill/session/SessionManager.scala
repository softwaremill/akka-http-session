package com.softwaremill.session

import akka.http.scaladsl.server.AuthorizationFailedRejection

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

import akka.http.scaladsl.model.headers.HttpCookie

class SessionManager[T](val config: SessionConfig, crypto: Crypto = DefaultCrypto)(implicit val sessionSerializer: SessionSerializer[T]) { manager =>

  val clientSession: ClientSessionManager[T] = new ClientSessionManager[T] {
    override def config = manager.config
    override def sessionSerializer = manager.sessionSerializer
    override def nowMillis = manager.nowMillis
    override def crypto = manager.crypto
  }

  val csrf: CsrfManager[T] = new CsrfManager[T] {
    override def config = manager.config
  }

  def rememberMe(_storage: RememberMeStorage[T]): RememberMeManager[T] = new RememberMeManager[T] {
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

  def decode(data: String): Option[T] = {
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

      if (nowMillis < expiry && SessionUtil.constantTimeEquals(splitted(0), crypto.sign(serialized, config.serverSecret))) {
        Some(sessionSerializer.deserialize(serialized.substring(1))) // removing the x
      }
      else None
    }
    catch {
      // fail gracefully is the session cookie is corrupted
      case NonFatal(_) => None
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

trait RememberMeManager[T] {
  def config: SessionConfig
  def crypto: Crypto
  def nowMillis: Long
  def storage: RememberMeStorage[T]

  def createSelector(): String = SessionUtil.randomString(16)
  def createToken(): String = SessionUtil.randomString(64)

  def decodeSelectorAndToken(cookieValue: String): Option[(String, String)] = {
    val s = cookieValue.split(":", 2)
    if (s.length == 2) Some((s(0), s(1))) else None
  }

  def encodeSelectorAndToken(selector: String, token: String): String = s"$selector:$token"

  /**
   * Creates and stores a new token, removing the old one, if it exists.
   */
  def rotateToken(session: T, existing: Option[String])(implicit ec: ExecutionContext): Future[String] = {

    val selector = createSelector()
    val token = createToken()

    val storeFuture = storage.store(new RememberMeData[T](
      forSession = session,
      selector = selector,
      tokenHash = crypto.hash(token),
      expires = nowMillis + config.rememberMeCookieConfig.maxAge.getOrElse(0L) * 1000L
    )).map(_ => encodeSelectorAndToken(selector, token))

    existing match {
      case None => storeFuture
      case Some(cookieValue) =>
        decodeSelectorAndToken(cookieValue) match {
          case Some((s, _)) => storeFuture.flatMap(v => storage.remove(s).map(_ => v))
          case None => storeFuture
        }
    }
  }

  def createCookie(value: String) = HttpCookie(
    name = config.rememberMeCookieConfig.name,
    value = value,
    expires = None,
    maxAge = config.rememberMeCookieConfig.maxAge,
    domain = config.rememberMeCookieConfig.domain,
    path = config.rememberMeCookieConfig.path,
    secure = config.rememberMeCookieConfig.secure,
    httpOnly = config.rememberMeCookieConfig.httpOnly
  )

  def sessionFromCookie(cookieValue: String)(implicit ec: ExecutionContext): Future[Option[T]] = {
    decodeSelectorAndToken(cookieValue) match {
      case Some((selector, token)) =>
        storage.lookup(selector).map(_.flatMap { lookupResult =>
          if (SessionUtil.constantTimeEquals(crypto.hash(token), lookupResult.tokenHash) &&
            lookupResult.expires > nowMillis) {
            Some(lookupResult.createSession())
          }
          else {
            None
          }
        })
      case None => Future.successful(None)
    }
  }

  def removeToken(cookieValue: String)(implicit ec: ExecutionContext): Future[Unit] = {
    decodeSelectorAndToken(cookieValue) match {
      case Some((s, _)) => storage.remove(s)
      case None => Future.successful(())
    }
  }
}