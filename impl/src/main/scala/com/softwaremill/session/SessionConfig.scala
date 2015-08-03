package com.softwaremill.session

import com.typesafe.config.{ConfigValueFactory, ConfigFactory, Config}

import SessionUtil._

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
    rememberMeCookieConfig: CookieConfig,
    /**
     * When a remember me token is used to log in, a new one is generated. The old one should be deleted with a delay,
     * to properly serve concurrent requests using the old token.
     */
    rememberMeRemoveUsedTokenAfter: Long
) {

  def withServerSecret(serverSecret: String) = copy(serverSecret = serverSecret)

  def withClientSessionCookieConfig(config: CookieConfig) = copy(clientSessionCookieConfig = config)
  def withClientSessionCookieName(name: String) = copy(clientSessionCookieConfig = clientSessionCookieConfig.copy(name = name))
  def withClientSessionCookieDomain(domain: Option[String]) = copy(clientSessionCookieConfig = clientSessionCookieConfig.copy(domain = domain))
  def withClientSessionCookiePath(path: Option[String]) = copy(clientSessionCookieConfig = clientSessionCookieConfig.copy(path = path))
  def withClientSessionCookieMaxAge(maxAge: Option[Long]) = copy(clientSessionCookieConfig = clientSessionCookieConfig.copy(maxAge = maxAge))
  def withClientSessionCookieSecure(secure: Boolean) = copy(clientSessionCookieConfig = clientSessionCookieConfig.copy(secure = secure))
  def withClientSessionCookieHttpOnly(httpOnly: Boolean) = copy(clientSessionCookieConfig = clientSessionCookieConfig.copy(httpOnly = httpOnly))

  def withClientSessionMaxAgeSeconds(maxAgeSeconds: Option[Long]) = copy(clientSessionMaxAgeSeconds = maxAgeSeconds)
  def withClientSessionEncryptData(encryptData: Boolean) = copy(clientSessionEncryptData = encryptData)

  def withCsrfCookieConfig(config: CookieConfig) = copy(csrfCookieConfig = config)
  def withCsrfCookieName(name: String) = copy(csrfCookieConfig = csrfCookieConfig.copy(name = name))
  def withCsrfCookieDomain(domain: Option[String]) = copy(csrfCookieConfig = csrfCookieConfig.copy(domain = domain))
  def withCsrfCookiePath(path: Option[String]) = copy(csrfCookieConfig = csrfCookieConfig.copy(path = path))
  def withCsrfCookieMaxAge(maxAge: Option[Long]) = copy(csrfCookieConfig = csrfCookieConfig.copy(maxAge = maxAge))
  def withCsrfCookieSecure(secure: Boolean) = copy(csrfCookieConfig = csrfCookieConfig.copy(secure = secure))
  def withCsrfCookieHttpOnly(httpOnly: Boolean) = copy(csrfCookieConfig = csrfCookieConfig.copy(httpOnly = httpOnly))

  def withCsrfSubmittedName(csrfSubmittedName: String) = copy(csrfSubmittedName = csrfSubmittedName)

  def withRememberMeCookieConfig(config: CookieConfig) = copy(rememberMeCookieConfig = config)
  def withRememberMeCookieName(name: String) = copy(rememberMeCookieConfig = rememberMeCookieConfig.copy(name = name))
  def withRememberMeCookieDomain(domain: Option[String]) = copy(rememberMeCookieConfig = rememberMeCookieConfig.copy(domain = domain))
  def withRememberMeCookiePath(path: Option[String]) = copy(rememberMeCookieConfig = rememberMeCookieConfig.copy(path = path))
  def withRememberMeCookieMaxAge(maxAge: Option[Long]) = copy(rememberMeCookieConfig = rememberMeCookieConfig.copy(maxAge = maxAge))
  def withRememberMeCookieSecure(secure: Boolean) = copy(rememberMeCookieConfig = rememberMeCookieConfig.copy(secure = secure))
  def withRememberMeCookieHttpOnly(httpOnly: Boolean) = copy(rememberMeCookieConfig = rememberMeCookieConfig.copy(httpOnly = httpOnly))
  def withRememberMeRemoveUsedTokenAfter(rememberMeRemoveUsedTokenAfter: Long) = copy(rememberMeRemoveUsedTokenAfter = rememberMeRemoveUsedTokenAfter)
}

object SessionConfig {
  def fromConfig(config: Config): SessionConfig = {
    val scopedConfig = config.getConfig("akka.http.session")
    val clientSessionConfig = scopedConfig.getConfigOption("clientSession").getOrElse(ConfigFactory.empty())
    val csrfConfig = scopedConfig.getConfigOption("csrf").getOrElse(ConfigFactory.empty())
    val rememberMeConfig = scopedConfig.getConfigOption("rememberMe").getOrElse(ConfigFactory.empty())

    SessionConfig(
      serverSecret = scopedConfig.getString("serverSecret"),
      clientSessionCookieConfig = CookieConfig(
        name = clientSessionConfig.getStringOption("cookie.name").getOrElse("_sessiondata"),
        domain = clientSessionConfig.getStringOption("cookie.domain"),
        path = clientSessionConfig.getStringOption("cookie.path").orElse(Some("/")),
        maxAge = clientSessionConfig.getDurationSecondsOption("cookie.maxAge"),
        secure = clientSessionConfig.getBooleanOption("cookie.secure").getOrElse(false),
        httpOnly = clientSessionConfig.getBooleanOption("cookie.httpOnly").getOrElse(true)
      ),
      clientSessionMaxAgeSeconds = clientSessionConfig.getLongOption("maxAgeSeconds"),
      clientSessionEncryptData = clientSessionConfig.getBooleanOption("encryptData").getOrElse(false),
      csrfCookieConfig = CookieConfig(
        name = csrfConfig.getStringOption("cookie.name").getOrElse("XSRF-TOKEN"),
        domain = csrfConfig.getStringOption("cookie.domain"),
        path = csrfConfig.getStringOption("cookie.path").orElse(Some("/")),
        maxAge = csrfConfig.getDurationSecondsOption("cookie.maxAge"),
        secure = csrfConfig.getBooleanOption("cookie.secure").getOrElse(false),
        httpOnly = csrfConfig.getBooleanOption("cookie.httpOnly").getOrElse(false)
      ),
      csrfSubmittedName = csrfConfig.getStringOption("submittedName").getOrElse("X-XSRF-TOKEN"),
      rememberMeCookieConfig = CookieConfig(
        name = rememberMeConfig.getStringOption("cookie.name").getOrElse("_rememberme"),
        domain = rememberMeConfig.getStringOption("cookie.domain"),
        path = rememberMeConfig.getStringOption("cookie.path").orElse(Some("/")),
        maxAge = rememberMeConfig.getDurationSecondsOption("cookie.maxAge").orElse(Some(60L * 60L * 24L * 30L)),
        secure = rememberMeConfig.getBooleanOption("cookie.secure").getOrElse(false),
        httpOnly = rememberMeConfig.getBooleanOption("cookie.httpOnly").getOrElse(true)
      ),
      rememberMeRemoveUsedTokenAfter = rememberMeConfig.getDurationSecondsOption("removeUsedTokenAfter").getOrElse(5L)
    )
  }

  /**
   * Creates a default configuration using the given secret. Default values:
   *
   * <pre>
   * clientSession {
   *   cookie {
   *     name = "_sessiondata"
   *     path = /
   *     secure = false
   *     httpOnly = true
   *   }
   *   encryptData = false
   * }
   *
   * csrf {
   *   cookie {
   *     name = "XSRF-TOKEN"
   *     path = /
   *     secure = false
   *     httpOnly = false
   *   }
   *   submittedName = "X-XSRF-TOKEN"
   * }
   *
   * rememberMe {
   *   cookie {
   *     name = "_rememberme"
   *     path = /
   *     maxAge = 30 days
   *     secure = false
   *     httpOnly = true
   *   }
   *   removeUsedTokenAfter = 5 seconds
   * }
   * </pre>
   *
   * Other attributes are `None`.
   */
  def default(serverSecret: String) = fromConfig(ConfigFactory.empty()
    .withValue("akka.http.session.serverSecret", ConfigValueFactory.fromAnyRef(serverSecret)))
}
