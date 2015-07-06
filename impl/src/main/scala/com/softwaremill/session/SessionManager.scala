package com.softwaremill.session

import java.net.{URLDecoder, URLEncoder}

import akka.http.scaladsl.server.AuthorizationFailedRejection

import scala.util.control.NonFatal

import akka.http.scaladsl.model.headers.HttpCookie

// Partly based on the implementation from Play! [[https://github.com/playframework]]
// see https://github.com/playframework/playframework/blob/master/framework/src/play/src/main/scala/play/api/mvc/Http.scala
class SessionManager(config: SessionConfig, crypto: Crypto = DefaultCrypto) {
  def sessionCookieName = config.sessionCookieConfig.name

  def createClientSessionCookie(data: SessionData) = createClientSessionCookieWithValue(encode(data))

  def createClientSessionCookieWithValue(value: String) = HttpCookie(
    name = config.sessionCookieConfig.name,
    value = value,
    expires = None,
    maxAge = config.sessionCookieConfig.maxAge,
    domain = config.sessionCookieConfig.domain,
    path = config.sessionCookieConfig.path,
    secure = config.sessionCookieConfig.secure,
    httpOnly = config.sessionCookieConfig.httpOnly)

  def encode(data: SessionData): String = {
    // adding an "x" so that the string is never emtpy, even if there's no data
    val serialized = "x" + data
      .map { case (k, v) => URLEncoder.encode(k, "UTF-8")+"="+URLEncoder.encode(v, "UTF-8") }
      .mkString("&")

    val withExpiry = config.sessionMaxAgeSeconds.fold(serialized) { maxAge =>
      val expiry = nowMillis + maxAge * 1000L
      s"$expiry-$serialized"
    }

    val encrypted = if (config.encryptSessionData) crypto.encrypt(withExpiry, config.serverSecret) else withExpiry

    s"${crypto.sign(serialized, config.serverSecret)}-$encrypted"
  }

  def decode(data: String): Option[SessionData] = {
    def urldecode(data: String) = {
      if (data == "") Map.empty[String, String] else {
        data
          .split("&")
          .map(_.split("=", 2))
          .map(p => URLDecoder.decode(p(0), "UTF-8") -> URLDecoder.decode(p(1), "UTF-8"))
          .toMap
      }
    }

    // Do not change this unless you understand the security issues behind timing attacks.
    // This method intentionally runs in constant time if the two strings have the same length.
    // If it didn't, it would be vulnerable to a timing attack.
    def safeEquals(a: String, b: String) = {
      if (a.length != b.length) {
        false
      } else {
        var equal = 0
        for (i <- Array.range(0, a.length)) {
          equal |= a(i) ^ b(i)
        }
        equal == 0
      }
    }

    def extractExpiry(data: String): (Long, String) = {
      config.sessionMaxAgeSeconds.fold((Long.MaxValue, data)) { maxAge =>
        val splitted = data.split("-", 2)
        (splitted(0).toLong, splitted(1))
      }
    }

    try {
      val splitted = data.split("-", 2)
      val decrypted = if (config.encryptSessionData) crypto.decrypt(splitted(1), config.serverSecret) else splitted(1)

      val (expiry, serialized) = extractExpiry(decrypted)

      if (nowMillis < expiry && safeEquals(splitted(0), crypto.sign(serialized, config.serverSecret))) {
        Some(urldecode(serialized.substring(1))) // removing the x
      } else None
    } catch {
      // fail gracefully is the session cookie is corrupted
      case NonFatal(_) => None
    }
  }

  def nowMillis = System.currentTimeMillis()

  def sessionCookieMissingRejection = AuthorizationFailedRejection

  def csrfCookieName = config.csrfCookieConfig.name
  def csrfSubmittedName = config.csrfSubmittedName
  def csrfTokenInvalidRejection = AuthorizationFailedRejection
  def createCsrfToken(): String = SessionUtil.randomString(64)

  def createCsrfCookie() = HttpCookie(
    name = config.csrfCookieConfig.name,
    value = createCsrfToken(),
    expires = None,
    maxAge = config.csrfCookieConfig.maxAge,
    domain = config.csrfCookieConfig.domain,
    path = config.csrfCookieConfig.path,
    secure = config.csrfCookieConfig.secure,
    httpOnly = config.csrfCookieConfig.httpOnly)
}
