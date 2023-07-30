package com.softwaremill.session

import java.security.{KeyPairGenerator, PrivateKey}
import java.util.Base64

import com.softwaremill.session.JwsAlgorithm.HmacSHA256
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest._
import matchers.should._
import org.scalatest.flatspec.AnyFlatSpec

import scala.concurrent.duration._

class SessionConfigTest extends AnyFlatSpec with Matchers with OptionValues {

  val fakeServerSecret = s"f4k3S3rv3rS3cr37-${"x" * 64}"

  def referenceConfWithSecret(serverSecret: String): Config =
    ConfigFactory
      .load("reference")
      .withValue("pekko.http.session.server-secret", fromAnyRef(serverSecret))

  def configWith(stringValue: String): Config =
    ConfigFactory
      .parseString(stringValue)
      .withFallback(referenceConfWithSecret(fakeServerSecret))

  it should "load and parse default (HS256) JWS config" in {
    val fakeConfig = referenceConfWithSecret(fakeServerSecret)
    fakeConfig.getString("pekko.http.session.jws.alg") should equal("HS256")

    val config = SessionConfig.fromConfig(fakeConfig)
    config.jws.alg should equal(HmacSHA256(fakeServerSecret))
  }

  it should "load and parse HS256 JWS config" in {
    val fakeConfig = referenceConfWithSecret(fakeServerSecret)

    val config = SessionConfig.fromConfig(fakeConfig)
    config.jws.alg should equal(HmacSHA256(fakeServerSecret))
  }

  it should "load and parse RS256 JWS config" in {
    val privateKey: PrivateKey = {
      val keyPairGen = KeyPairGenerator.getInstance("RSA")
      keyPairGen.initialize(4096)
      val kp = keyPairGen.generateKeyPair()
      kp.getPrivate
    }
    val encodedPrivateKey: String = Base64.getEncoder.encodeToString(privateKey.getEncoded)
    val fakeConfig = configWith(s"""
        |pekko.http.session.jws {
        |  alg = "RS256"
        |  rsa-private-key = "$encodedPrivateKey"
        |}
      """.stripMargin)

    val config = SessionConfig.fromConfig(fakeConfig)
    config.jws.alg should equal(JwsAlgorithm.Rsa(privateKey))
  }

  it should "fail to load config due to missing RSA private key (alg = RS256)" in {
    val fakeConfig = configWith(s"""
         |pekko.http.session.jws {
         |  alg = "RS256"
         |}
      """.stripMargin)
    val ex = intercept[IllegalArgumentException] {
      SessionConfig.fromConfig(fakeConfig)
    }
    ex.getMessage should equal("pekko.http.session.jws.rsa-private-key must be defined in order to use alg = RS256")
  }

  it should "fail to load config due to empty RSA private key (alg = RS256)" in {
    val fakeConfig = configWith(s"""
         |pekko.http.session.jws {
         |  alg = "RS256"
         |  rsa-private-key = ""
         |}
      """.stripMargin)
    val ex = intercept[IllegalArgumentException] {
      SessionConfig.fromConfig(fakeConfig)
    }
    ex.getMessage should equal("pekko.http.session.jws.rsa-private-key must be defined in order to use alg = RS256")
  }

  it should "fail to load config due to invalid RSA private key (alg = RS256)" in {
    val fakeConfig = configWith(s"""
         |pekko.http.session.jws {
         |  alg = "RS256"
         |  rsa-private-key = "an invalid RSA key"
         |}
      """.stripMargin)
    val ex = intercept[IllegalArgumentException] {
      SessionConfig.fromConfig(fakeConfig)
    }
    ex.getMessage should equal("Invalid RSA private key")
  }

  it should "fail to load config due to unsupported JWS alg" in {
    val fakeConfig = configWith("""pekko.http.session.jws.alg = "UNSUPPORTED1" """)
    val ex = intercept[IllegalArgumentException] {
      SessionConfig.fromConfig(fakeConfig)
    }
    ex.getMessage should equal(s"Unsupported JWS alg 'UNSUPPORTED1'. Supported algorithms are: HS256, RS256")
  }

  it should "load JWT config" in {
    val fakeConfig = configWith(
      """pekko.http.session.jwt {
        |iss = "testIssuer"
        |sub = "testSubject"
        |aud = "testAudience"
        |exp-timeout = 12 hours
        |nbf-offset = 5 minutes
        |include-iat = true
        |include-jti = true
        |} """.stripMargin)
    val config = SessionConfig.fromConfig(fakeConfig)

    config.jwt.issuer.value should equal("testIssuer")
    config.jwt.subject.value should equal("testSubject")
    config.jwt.audience.value should equal("testAudience")
    config.jwt.expirationTimeout.value should equal(12.hours.toSeconds)
    config.jwt.notBeforeOffset.value should equal(5.minutes.toSeconds)
    config.jwt.includeIssuedAt shouldBe true
    config.jwt.includeRandomJwtId shouldBe true
  }

  it should "fallback to empty JWT config (with default exp-timeout) if absent" in {
    val config = SessionConfig.fromConfig(configWith("pekko.http.session.jwt = {}"))

    config.jwt.issuer should not be defined
    config.jwt.subject should not be defined
    config.jwt.audience should not be defined
    // fallback to the session-max-age
    config.jwt.expirationTimeout should equal(config.sessionMaxAgeSeconds)
    config.jwt.notBeforeOffset should not be defined
    config.jwt.includeIssuedAt shouldBe false
    config.jwt.includeRandomJwtId shouldBe false
  }

  it should "fallback to empty JWT config (without default exp-timeout) if absent" in {
    val config = SessionConfig.fromConfig(configWith(
      """pekko.http.session {
        |  jwt {}
        |  max-age = "none"
        |}""".stripMargin))

    config.jwt.issuer should not be defined
    config.jwt.subject should not be defined
    config.jwt.audience should not be defined
    // fallback to the session-max-age
    config.jwt.expirationTimeout should not be defined
    config.jwt.notBeforeOffset should not be defined
    config.jwt.includeIssuedAt shouldBe false
    config.jwt.includeRandomJwtId shouldBe false
  }

  it should "use max-age as a default value for jwt.expirationTimeout" in {
    val config = SessionConfig.fromConfig(configWith(
      """pekko.http.session {
        |  max-age = 10 seconds
        |}""".stripMargin))

    config.jwt.expirationTimeout.value should equal(10L)
  }

}
