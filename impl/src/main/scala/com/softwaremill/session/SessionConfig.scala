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
  clientSessionCookieConfig: CookieConfig,
  /**
   * If you'd like session cookies to expire as well after a period of inactivity, you can optionally include an
   * expiration date in the cookie data (expiration will be validated on the server). The expiration date will be
   * calculated by adding the given number of seconds to the time at which the session is last updated.
   *
   * For session cookies, **do not** set the [[CookieConfig.maxAge]], as this will turn it into a persistent cookie
   * (on the client).
   */
  clientSessionMaxAgeSeconds: Option[Long],
  /**
   * By default the session data won't be encrypted, only signed with a hash. Set this to true if you'd like the data
   * to be encrypted using a symmetrical key.
   */
  encryptClientSessionData: Boolean,
  csrfCookieConfig: CookieConfig,
  /**
   * Name of the header or form field in which the CSRF token will be submitted.
   */
  csrfSubmittedName: String
) {

  def withServerSecret(serverSecret: String)                = copy(serverSecret = serverSecret)

  def withClientSessionCookieConfig(config: CookieConfig)   = copy(clientSessionCookieConfig = config)
  def withClientSessionCookieName(name: String)             = copy(clientSessionCookieConfig = clientSessionCookieConfig.copy(name = name))
  def withClientSessionCookieDomain(domain: Option[String]) = copy(clientSessionCookieConfig = clientSessionCookieConfig.copy(domain = domain))
  def withClientSessionCookiePath(path: Option[String])     = copy(clientSessionCookieConfig = clientSessionCookieConfig.copy(path = path))
  def withClientSessionCookieMaxAge(maxAge: Option[Long])   = copy(clientSessionCookieConfig = clientSessionCookieConfig.copy(maxAge = maxAge))
  def withClientSessionCookieSecure(secure: Boolean)        = copy(clientSessionCookieConfig = clientSessionCookieConfig.copy(secure = secure))
  def withClientSessionCookieHttpOnly(httpOnly: Boolean)    = copy(clientSessionCookieConfig = clientSessionCookieConfig.copy(httpOnly = httpOnly))

  def withClientSessionMaxAgeSeconds(maxAgeSeconds: Option[Long]) = copy(clientSessionMaxAgeSeconds = maxAgeSeconds)
  def withEncryptClientSessionData(encryptSessionData: Boolean) = copy(encryptClientSessionData = encryptSessionData)

  def withCsrfCookieConfig(config: CookieConfig)            = copy(csrfCookieConfig = config)
  def withCsrfCookieName(name: String)                      = copy(csrfCookieConfig = csrfCookieConfig.copy(name = name))
  def withCsrfCookieDomain(domain: Option[String])          = copy(csrfCookieConfig = csrfCookieConfig.copy(domain = domain))
  def withCsrfCookiePath(path: Option[String])              = copy(csrfCookieConfig = csrfCookieConfig.copy(path = path))
  def withCsrfCookieMaxAge(maxAge: Option[Long])            = copy(csrfCookieConfig = csrfCookieConfig.copy(maxAge = maxAge))
  def withCsrfCookieSecure(secure: Boolean)                 = copy(csrfCookieConfig = csrfCookieConfig.copy(secure = secure))
  def withCsrfCookieHttpOnly(httpOnly: Boolean)             = copy(csrfCookieConfig = csrfCookieConfig.copy(httpOnly = httpOnly))

  def withCsrfSubmittedName(csrfSubmittedName: String)      = copy(csrfSubmittedName = csrfSubmittedName)
}

object SessionConfig {
  def fromConfig(config: Config): SessionConfig = {
    val scopedConfig = config.getConfig("akka.http.session")
    SessionConfig(
      serverSecret = scopedConfig.getString("serverSecret"),
      clientSessionCookieConfig = CookieConfig(
        name = scopedConfig.getStringOption("clientSessionCookie.name").getOrElse("_sessiondata"),
        domain = scopedConfig.getStringOption("clientSessionCookie.domain"),
        path = scopedConfig.getStringOption("clientSessionCookie.path"),
        maxAge = scopedConfig.getLongOption("clientSessionCookie.maxAge"),
        secure = scopedConfig.getBooleanOption("clientSessionCookie.secure").getOrElse(false),
        httpOnly = scopedConfig.getBooleanOption("clientSessionCookie.httpOnly").getOrElse(true)
      ),
      clientSessionMaxAgeSeconds = scopedConfig.getLongOption("clientSessionMaxAgeSeconds"),
      encryptClientSessionData = scopedConfig.getBooleanOption("encryptClientSessionData").getOrElse(false),
      csrfCookieConfig = CookieConfig(
        name = scopedConfig.getStringOption("csrfCookie.name").getOrElse("XSRF-TOKEN"),
        domain = scopedConfig.getStringOption("csrfCookie.domain"),
        path = scopedConfig.getStringOption("csrfCookie.path").orElse(Some("/")),
        maxAge = scopedConfig.getLongOption("csrfCookie.maxAge"),
        secure = scopedConfig.getBooleanOption("csrfCookie.secure").getOrElse(false),
        httpOnly = scopedConfig.getBooleanOption("csrfCookie.httpOnly").getOrElse(false)
      ),
      csrfSubmittedName = "X-XSRF-TOKEN"
    )
  }

  /**
   * Creates a default configuration using the given secret. Default values:
   *
   * <pre>
   * clientSessionCookie.name = "_sessiondata"
   * clientSessionCookie.secure = false
   * clientSessionCookie.httpOnly = true
   * encryptClientSessionData = false
   *
   * csrfCookie.name = "XSRF-TOKEN"
   * csrfCookie.path = /
   * csrfCookie.secure = false
   * csrfCookie.httpOnly = false
   * csrfSubmittedName = "X-XSRF-TOKEN"
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
