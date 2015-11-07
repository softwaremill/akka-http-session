package com.softwaremill.session

import java.util.concurrent.TimeUnit

import com.typesafe.config.{ConfigValueFactory, ConfigFactory, Config}

case class CookieConfig(
  name: String,
  domain: Option[String],
  path: Option[String],
  maxAge: Option[Long],
  secure: Boolean,
  httpOnly: Boolean
)

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
  clientSessionEncryptData: Boolean,
  csrfCookieConfig: CookieConfig,
  /**
   * Name of the header or form field in which the CSRF token will be submitted.
   */
  csrfSubmittedName: String,
  refreshTokenCookieConfig: CookieConfig,
  /**
   * When a refresh token is used to log in, a new one is generated. The old one should be deleted with a delay,
   * to properly serve concurrent requests using the old token.
   */
  removeUsedRefreshTokenAfter: Long
)

object SessionConfig {
  private implicit class PimpedConfig(config: Config) {
    val noneValue = "none"

    def getOptionalString(path: String) = if (config.getAnyRef(path) == noneValue) None else
      Some(config.getString(path))
    def getOptionalLong(path: String) = if (config.getAnyRef(path) == noneValue) None else
      Some(config.getLong(path))
    def getOptionalDurationSeconds(path: String) = if (config.getAnyRef(path) == noneValue) None else
      Some(config.getDuration(path, TimeUnit.SECONDS))
  }

  def fromConfig(config: Config = ConfigFactory.load()): SessionConfig = {
    val scopedConfig = config.getConfig("akka.http.session")
    val clientSessionConfig = scopedConfig.getConfig("client-session")
    val csrfConfig = scopedConfig.getConfig("csrf")
    val refreshTokenConfig = scopedConfig.getConfig("refresh-token")

    SessionConfig(
      serverSecret = scopedConfig.getString("server-secret"),
      clientSessionCookieConfig = CookieConfig(
        name = clientSessionConfig.getString("cookie.name"),
        domain = clientSessionConfig.getOptionalString("cookie.domain"),
        path = clientSessionConfig.getOptionalString("cookie.path"),
        maxAge = clientSessionConfig.getOptionalDurationSeconds("cookie.max-age"),
        secure = clientSessionConfig.getBoolean("cookie.secure"),
        httpOnly = clientSessionConfig.getBoolean("cookie.http-only")
      ),
      clientSessionMaxAgeSeconds = clientSessionConfig.getOptionalLong("max-age-seconds"),
      clientSessionEncryptData = clientSessionConfig.getBoolean("encrypt-data"),
      csrfCookieConfig = CookieConfig(
        name = csrfConfig.getString("cookie.name"),
        domain = csrfConfig.getOptionalString("cookie.domain"),
        path = csrfConfig.getOptionalString("cookie.path"),
        maxAge = csrfConfig.getOptionalDurationSeconds("cookie.max-age"),
        secure = csrfConfig.getBoolean("cookie.secure"),
        httpOnly = csrfConfig.getBoolean("cookie.http-only")
      ),
      csrfSubmittedName = csrfConfig.getString("submitted-name"),
      refreshTokenCookieConfig = CookieConfig(
        name = refreshTokenConfig.getString("cookie.name"),
        domain = refreshTokenConfig.getOptionalString("cookie.domain"),
        path = refreshTokenConfig.getOptionalString("cookie.path"),
        maxAge = refreshTokenConfig.getOptionalDurationSeconds("cookie.max-age"),
        secure = refreshTokenConfig.getBoolean("cookie.secure"),
        httpOnly = refreshTokenConfig.getBoolean("cookie.http-only")
      ),
      removeUsedRefreshTokenAfter = refreshTokenConfig.getDuration("remove-used-token-after", TimeUnit.SECONDS)
    )
  }

  /**
   * Creates a default configuration using the given secret.
   */
  def default(serverSecret: String) = fromConfig(ConfigFactory.load()
    .withValue("akka.http.session.server-secret", ConfigValueFactory.fromAnyRef(serverSecret)))
}
