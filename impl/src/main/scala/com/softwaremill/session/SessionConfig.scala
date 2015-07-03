package com.softwaremill.session

import java.math.BigInteger
import java.security.SecureRandom

import com.typesafe.config.{ConfigValueFactory, ConfigFactory, Config}

import SessionUtil._

case class CookieConfig(
  name: String,
  domain: Option[String],
  path: Option[String],
  maxAge: Option[Long],
  secure: Boolean,
  httpOnly: Boolean)

case class SessionConfig(
  /**
   * Should be different on each environment and **kept secret!**. It's used to sign and encrypt cookie data.
   * This should be a long random string.
   */
  serverSecret: String,
  sessionCookieConfig: CookieConfig,
  /**
   * If you'd like session cookies to expire as well after a period of inactivity, you can optionally include an
   * expiration date in the cookie data (expiration will be validated on the server). The expiration date will be
   * calculated by adding the given number of seconds to the time at which the session is last updated.
   *
   * For session cookies, **do not** set the [[CookieConfig.maxAge]], as this will turn it into a persistent cookie
   * (on the client).
   */
  sessionMaxAgeSeconds: Option[Long],
  /**
   * By default the session data won't be encrypted, only signed with a hash. Set this to true if you'd like the data
   * to be encrypted using a symmetrical key.
   */
  encryptSessionData: Boolean
) {

  def withServerSecret(serverSecret: String)              = copy(serverSecret = serverSecret)

  def withSessionCookieConfig(config: CookieConfig)       = copy(sessionCookieConfig = config)
  def withSessionCookieName(name: String)                 = copy(sessionCookieConfig = sessionCookieConfig.copy(name = name))
  def withSessionCookieDomain(domain: Option[String])     = copy(sessionCookieConfig = sessionCookieConfig.copy(domain = domain))
  def withSessionCookiePath(path: Option[String])         = copy(sessionCookieConfig = sessionCookieConfig.copy(path = path))
  def withSessionCookieMaxAge(maxAge: Option[Long])       = copy(sessionCookieConfig = sessionCookieConfig.copy(maxAge = maxAge))
  def withSessionCookieSecure(secure: Boolean)            = copy(sessionCookieConfig = sessionCookieConfig.copy(secure = secure))
  def withSessionCookieHttpOnly(httpOnly: Boolean)        = copy(sessionCookieConfig = sessionCookieConfig.copy(httpOnly = httpOnly))

  def withSessionMaxAgeSeconds(sessionMaxAgeSeconds: Option[Long]) = copy(sessionMaxAgeSeconds = sessionMaxAgeSeconds)
  def withEncryptSessionData(encryptSessionData: Boolean) = copy(encryptSessionData = encryptSessionData)
}

object SessionConfig {
  def fromConfig(config: Config): SessionConfig = {
    val scopedConfig = config.getConfig("akka.http.session")
    SessionConfig(
      serverSecret = scopedConfig.getString("serverSecret"),
      sessionCookieConfig = CookieConfig(
        name = scopedConfig.getStringOption("sessionCookie.name").getOrElse("_sessiondata"),
        domain = scopedConfig.getStringOption("sessionCookie.domain"),
        path = scopedConfig.getStringOption("sessionCookie.path"),
        maxAge = scopedConfig.getLongOption("sessionCookie.maxAge"),
        secure = scopedConfig.getBooleanOption("sessionCookie.secure").getOrElse(false),
        httpOnly = scopedConfig.getBooleanOption("sessionCookie.httpOnly").getOrElse(true)
      ),
      sessionMaxAgeSeconds = scopedConfig.getLongOption("sessionMaxAgeSeconds"),
      encryptSessionData = scopedConfig.getBooleanOption("encryptSessionData").getOrElse(false)
    )
  }

  /**
   * Creates a default configuration using the given secret. Default values:
   *
   * <pre>
   * sessionCookie.name = "_sessiondata"
   * sessionCookie.secure = false
   * sessionCookie.httpOnly = true
   * encryptSessionData = false
   * </pre>
   *
   * Other attributes are `None`.
   */
  def default(serverSecret: String) = fromConfig(ConfigFactory.empty()
    .withValue("akka.http.session.serverSecret", ConfigValueFactory.fromAnyRef(serverSecret)))

  /**
   * Utility method for generating a good server secret.
   */
  def randomServerSecret() = {
    // http://stackoverflow.com/questions/41107/how-to-generate-a-random-alpha-numeric-string
    val random = new SecureRandom()
    new BigInteger(640, random).toString(32)
  }
}
