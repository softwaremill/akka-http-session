package com.softwaremill.session

import java.util.concurrent.TimeUnit

import com.softwaremill.session.JwsAlgorithm.{HmacSHA256, Rsa}
import com.softwaremill.session.SessionConfig.{JwsConfig, JwtConfig}
import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}

case class CookieConfig(name: String, domain: Option[String], path: Option[String], secure: Boolean, httpOnly: Boolean)

case class HeaderConfig(sendToClientHeaderName: String, getFromClientHeaderName: String)

case class SessionConfig(
                         /**
                           * Should be different on each environment and **kept secret!**. It's used to sign and encrypt session data.
                           * This should be a long random string.
                           */
                         serverSecret: String,
                         jws: JwsConfig,
                         jwt: JwtConfig,
                         sessionCookieConfig: CookieConfig,
                         sessionHeaderConfig: HeaderConfig,
                         /**
                           * If you'd like session cookies to expire as well after a period of inactivity, you can optionally include an
                           * expiration date in the cookie data (expiration will be validated on the server). The expiration date will be
                           * calculated by adding the given number of seconds to the time at which the session is last updated.
                           */
                         sessionMaxAgeSeconds: Option[Long],
                         /**
                           * By default the session data won't be encrypted, only signed with a hash. Set this to true if you'd like the data
                           * to be encrypted using a symmetrical key.
                           */
                         sessionEncryptData: Boolean,
                         csrfCookieConfig: CookieConfig,
                         /**
                           * Name of the header or form field in which the CSRF token will be submitted.
                           */
                         csrfSubmittedName: String,
                         refreshTokenCookieConfig: CookieConfig,
                         refreshTokenHeaderConfig: HeaderConfig,
                         refreshTokenMaxAgeSeconds: Long,
                         /**
                           * When a refresh token is used to log in, a new one is generated. The old one should be deleted with a delay,
                           * to properly serve concurrent requests using the old token.
                           */
                         removeUsedRefreshTokenAfter: Long,
                         /**
                           * Allow migrating tokens created with prior versions of this library.
                           */
                         tokenMigrationV0_5_2Enabled: Boolean,
                         tokenMigrationV0_5_3Enabled: Boolean) {
  require(serverSecret.length >= 64, "Server secret must be at least 64 characters long!")
}

object SessionConfig {

  case class JwtConfig(issuer: Option[String], subject: Option[String], audience: Option[String], expirationTimeout: Option[Long], notBeforeOffset: Option[Long], includeIssuedAt: Boolean, includeRandomJwtId: Boolean)

  case class JwsConfig(alg: JwsAlgorithm)

  private implicit class PimpedConfig(config: Config) {
    val noneValue = "none"

    def getOptionalString(path: String) =
      if (config.getAnyRef(path) == noneValue) None
      else
        Some(config.getString(path))
    def getOptionalLong(path: String) =
      if (config.getAnyRef(path) == noneValue) None
      else
        Some(config.getLong(path))
    def getOptionalDurationSeconds(path: String) =
      if (config.getAnyRef(path) == noneValue) None
      else
        Some(config.getDuration(path, TimeUnit.SECONDS))
    def getStringIfExists(path: String): Option[String] = ifExists(path, config.getString)
    def getDurationSecondsIfExists(path: String): Option[Long] = ifExists(path, config.getDuration(_, TimeUnit.SECONDS))
    def getBooleanIfExists(path: String): Option[Boolean] = ifExists(path, config.getBoolean)
    def getConfigIfExists(path: String): Option[Config] = ifExists(path, config.getConfig)

    private def ifExists[T](key: String, getter: String => T): Option[T] =
      if (config.hasPath(key)) Some(getter(key))
      else None
  }

  def fromConfig(config: Config = ConfigFactory.load()): SessionConfig = {
    val scopedConfig = config.getConfig("akka.http.session")
    val csrfConfig = scopedConfig.getConfig("csrf")
    val refreshTokenConfig = scopedConfig.getConfig("refresh-token")
    val tokenMigrationConfig = scopedConfig.getConfig("token-migration")
    val sessionMaxAgeSeconds = scopedConfig.getOptionalDurationSeconds("max-age")
    SessionConfig(
      serverSecret = scopedConfig.getString("server-secret"),
      jws = JwsConfig {
        val jwsConfig = scopedConfig.getConfig("jws")
        jwsConfig.getString("alg").toUpperCase match {
          case "HS256" =>
            HmacSHA256(scopedConfig.getString("server-secret"))
          case "RS256" =>
            Rsa.fromConfig(jwsConfig).get
          case oth =>
            throw new IllegalArgumentException(s"Unsupported JWS alg '$oth'. Supported algorithms are: HS256, RS256")
        }
      },
      jwt = {
        val claimsConfig = scopedConfig.getConfig("jwt")

        JwtConfig(
          issuer = claimsConfig.getStringIfExists("iss"),
          subject = claimsConfig.getStringIfExists("sub"),
          audience = claimsConfig.getStringIfExists("aud"),
          expirationTimeout = claimsConfig.getDurationSecondsIfExists("exp-timeout").orElse(sessionMaxAgeSeconds),
          notBeforeOffset = claimsConfig.getDurationSecondsIfExists("nbf-offset"),
          includeIssuedAt = claimsConfig.getBooleanIfExists("include-iat").getOrElse(false),
          includeRandomJwtId = claimsConfig.getBooleanIfExists("include-jti").getOrElse(false))
      },
      sessionCookieConfig = CookieConfig(
        name = scopedConfig.getString("cookie.name"),
        domain = scopedConfig.getOptionalString("cookie.domain"),
        path = scopedConfig.getOptionalString("cookie.path"),
        secure = scopedConfig.getBoolean("cookie.secure"),
        httpOnly = scopedConfig.getBoolean("cookie.http-only")
      ),
      sessionHeaderConfig = HeaderConfig(
        sendToClientHeaderName = scopedConfig.getString("header.send-to-client-name"),
        getFromClientHeaderName = scopedConfig.getString("header.get-from-client-name")
      ),
      sessionMaxAgeSeconds = sessionMaxAgeSeconds,
      sessionEncryptData = scopedConfig.getBoolean("encrypt-data"),
      csrfCookieConfig = CookieConfig(
        name = csrfConfig.getString("cookie.name"),
        domain = csrfConfig.getOptionalString("cookie.domain"),
        path = csrfConfig.getOptionalString("cookie.path"),
        secure = csrfConfig.getBoolean("cookie.secure"),
        httpOnly = csrfConfig.getBoolean("cookie.http-only")
      ),
      csrfSubmittedName = csrfConfig.getString("submitted-name"),
      refreshTokenCookieConfig = CookieConfig(
        name = refreshTokenConfig.getString("cookie.name"),
        domain = refreshTokenConfig.getOptionalString("cookie.domain"),
        path = refreshTokenConfig.getOptionalString("cookie.path"),
        secure = refreshTokenConfig.getBoolean("cookie.secure"),
        httpOnly = refreshTokenConfig.getBoolean("cookie.http-only")
      ),
      refreshTokenHeaderConfig = HeaderConfig(
        sendToClientHeaderName = refreshTokenConfig.getString("header.send-to-client-name"),
        getFromClientHeaderName = refreshTokenConfig.getString("header.get-from-client-name")
      ),
      refreshTokenMaxAgeSeconds = refreshTokenConfig.getDuration("max-age", TimeUnit.SECONDS),
      removeUsedRefreshTokenAfter = refreshTokenConfig.getDuration("remove-used-token-after", TimeUnit.SECONDS),
      tokenMigrationV0_5_2Enabled = tokenMigrationConfig.getBoolean("v0-5-2.enabled"),
      tokenMigrationV0_5_3Enabled = tokenMigrationConfig.getBoolean("v0-5-3.enabled")
    )
  }

  /**
    * Creates a default configuration using the given secret.
    */
  def default(serverSecret: String): SessionConfig =
    fromConfig(
      ConfigFactory
        .load()
        .withValue("akka.http.session.server-secret", ConfigValueFactory.fromAnyRef(serverSecret)))

  def defaultConfig(serverSecret: String): SessionConfig =
    default(serverSecret) // required for javadsl directives, because default is a keyword
}
