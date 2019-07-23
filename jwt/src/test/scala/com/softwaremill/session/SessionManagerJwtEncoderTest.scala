package com.softwaremill.session

import java.security.{KeyPairGenerator, PrivateKey}
import java.util.Base64

import com.softwaremill.session.JwsAlgorithm.HmacSHA256
import com.softwaremill.session.SessionConfig.{JwsConfig, JwtConfig}
import org.json4s.JsonAST.{JObject, JString}
import org.json4s.{JValue, _}
import org.json4s.jackson.JsonMethods.parse
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.duration._

class SessionManagerJwtEncoderTest extends FlatSpec with Matchers {
  val defaultConfig = SessionConfig.default("1234567890123456789012345678901234567890123456789012345678901234567890")
  val configMaxAge = defaultConfig.copy(jwt = defaultConfig.jwt.copy(expirationTimeout = Some(3600)))
  val configEncrypted = defaultConfig.copy(sessionEncryptData = true)
  val configEncryptedMaxAge = configMaxAge.copy(sessionEncryptData = true)

  def rsaSigConfig() = {
    val privateKey: PrivateKey = {
      val keyPairGen = KeyPairGenerator.getInstance("RSA")
      keyPairGen.initialize(4096)
      val kp = keyPairGen.generateKeyPair()
      kp.getPrivate
    }

    defaultConfig.copy(jws = JwsConfig(alg = JwsAlgorithm.Rsa(privateKey)))
  }
  val hmacSha256Config = defaultConfig.copy(jws = JwsConfig(alg = HmacSHA256(defaultConfig.serverSecret)))

  case class TestData[T](name: String, data: T, config: SessionConfig, sessionSerializer: SessionSerializer[T, JValue])

  import JValueSessionSerializer._
  val tests = List(
    TestData("string, default config", "username", defaultConfig, implicitly[SessionSerializer[String, JValue]]),
    TestData("string, with max age", "username", configMaxAge, implicitly[SessionSerializer[String, JValue]]),
    TestData("string, with encryption", "username", configEncrypted, implicitly[SessionSerializer[String, JValue]]),
    TestData("string, with max age and encryption",
             "username",
             configEncryptedMaxAge,
             implicitly[SessionSerializer[String, JValue]]),
    TestData("integer, default config", 12345, defaultConfig, implicitly[SessionSerializer[Int, JValue]]),
    TestData("case class, default config",
             SessionData("john", 10),
             defaultConfig,
             JValueSessionSerializer.caseClass[SessionData]),
    TestData("case class, with max age and encryption",
             SessionData("john", 20),
             configEncryptedMaxAge,
             JValueSessionSerializer.caseClass[SessionData]),
    TestData("string, RSA signature", "username", rsaSigConfig(), implicitly[SessionSerializer[String, JValue]]),
    TestData("string, HMAC SHA256 signature",
             "username",
             hmacSha256Config,
             implicitly[SessionSerializer[String, JValue]])
  )

  tests.foreach { td =>
    it should s"encode+decode for ${td.name}" in {
      runTest(td)
    }
  }

  def runTest[T](td: TestData[T]): Unit = {
    implicit val ss = td.sessionSerializer
    implicit val encoder = new JwtSessionEncoder[T]
    val manager = new SessionManager(td.config).clientSessionManager

    manager.decode(manager.encode(td.data)) should be(SessionResult.Decoded(td.data))
  }

  it should "encode correctly in the JWT format" in {
    implicit val ss = JValueSessionSerializer.caseClass[SessionData]
    implicit val encoder = new JwtSessionEncoder[SessionData]

    val encoded = encoder.encode(SessionData("john", 30), 1447416197071L, defaultConfig)
    println(s"Test on: http://jwt.io/#debugger:\n$encoded")

    encoded.count(_ == '.') should be(2)
  }

  it should "encode JWT claims" in {
    val encoder = new JwtSessionEncoder[String]
    val nowMillis = 1447416197071L

    val fullClaimsConfig = defaultConfig.copy(jwt = JwtConfig(
      issuer = Some("testIssuer"),
      subject = Some("testSubject"),
      audience = Some("testAudience"),
      expirationTimeout = Some(3.hours.toSeconds),
      notBeforeTimeout = Some(1.minute.toSeconds),
      includeIssuedAt = true,
      includeRandomJwtId = true
    ))
    val encoded = encoder.encode("testPayload", nowMillis, fullClaimsConfig)

    val List(_, payload, _) = encoded.split("\\.").toList
    val payloadJson = parse(new String(Base64.getUrlDecoder.decode(payload), "utf-8"))

    payloadJson \\ "iss" should equal(JString("testIssuer"))
    payloadJson \\ "sub" should equal(JString("testSubject"))
    payloadJson \\ "aud" should equal(JString("testAudience"))
    payloadJson \\ "exp" should equal(JInt(nowMillis / 1000L + 3.hours.toSeconds))
    payloadJson \\ "nbf" should equal(JInt(nowMillis / 1000L + 1.minute.toSeconds))
    payloadJson \\ "iat" should equal(JInt(nowMillis / 1000L))

    payloadJson \\ "jti" match {
      case JString(iat) => iat should startWith regex "testIssuer-.*"
      case oth => fail(s"Invalid 'jti' claim format. Expected JString(...), got  $oth")
    }

    payloadJson \\ "data" should equal(JString("testPayload"))
  }

  it should "encode JWT with default exp claim" in {
    val encoder = new JwtSessionEncoder[String]
    val nowMillis = 1447416197071L
    val expirationTimeoutSeconds = 3.hours.toSeconds
    val encoded = encoder.encode("testPayload", nowMillis, defaultConfig.copy(jwt = JwtConfig(None, None, None, Some(expirationTimeoutSeconds), None, includeIssuedAt = false, includeRandomJwtId = false)))

    val List(_, payload, _) = encoded.split("\\.").toList
    val payloadJson = parse(new String(Base64.getUrlDecoder.decode(payload), "utf-8"))

    payloadJson should equal {
      JObject(
        "exp" -> JInt(nowMillis / 1000L + expirationTimeoutSeconds),
        "data" -> JString("testPayload")
      )
    }
  }

  it should "encode JWT without any registered claims" in {
    val encoder = new JwtSessionEncoder[String]
    val nowMillis = 1447416197071L
    val encoded = encoder.encode("testPayload", nowMillis, defaultConfig.copy(jwt = JwtConfig(None, None, None, None, None, includeIssuedAt = false, includeRandomJwtId = false)))

    val List(_, payload, _) = encoded.split("\\.").toList
    val payloadJson = parse(new String(Base64.getUrlDecoder.decode(payload), "utf-8"))

    payloadJson should equal {
      JObject(
        "data" -> JString("testPayload")
      )
    }
  }

  for {
    (alg, config) <- List(("HS256", defaultConfig), ("RS256", rsaSigConfig()))
  } {
    it should s"encode a correct JWT header (alg = $alg)" in {
      val encoder = new JwtSessionEncoder[String]
      val encoded = encoder.encode("test", 1447416197071L, config)

      val header = encoded.split("\\.").head
      val headerJson = parse(new String(Base64.getUrlDecoder.decode(header), "utf-8"))
      headerJson should equal(JObject(
        "alg" -> JString(alg),
        "typ" -> JString("JWT")
      ))
    }
  }

  it should "not decode an expired session" in {
    implicit val ss = JValueSessionSerializer.caseClass[SessionData]
    implicit val encoder = new JwtSessionEncoder[SessionData]
    val managerHour1 = new SessionManager(configMaxAge) {
      override def nowMillis = 1447416197071L
    }.clientSessionManager
    val managerHour3 = new SessionManager(configMaxAge) {
      override def nowMillis = 1447416197071L + 1000L * 60 * 60 * 3
    }.clientSessionManager

    managerHour3.decode(managerHour1.encode(SessionData("john", 40))) should be(SessionResult.Expired)
  }

  it should "not decode a token with a corrupted signature [HMAC SHA256]" in {
    implicit val ss = JValueSessionSerializer.caseClass[SessionData]
    implicit val encoder = new JwtSessionEncoder[SessionData]

    val managerHmac1 = new SessionManager(hmacSha256Config).clientSessionManager
    val managerHmac2 = new SessionManager(
      hmacSha256Config
        .copy(jws = JwsConfig(HmacSHA256(serverSecret = hmacSha256Config.serverSecret.reverse)))).clientSessionManager

    managerHmac1.decode(managerHmac2.encode(SessionData("john", 40))) shouldBe a[SessionResult.Corrupt]
  }

  it should "not decode a token with a corrupted signature [RSA]" in {
    implicit val ss = JValueSessionSerializer.caseClass[SessionData]
    implicit val encoder = new JwtSessionEncoder[SessionData]

    val managerRsa1 = new SessionManager(rsaSigConfig()).clientSessionManager
    val managerRsa2 = new SessionManager(rsaSigConfig()).clientSessionManager

    managerRsa1.decode(managerRsa2.encode(SessionData("john", 40))) shouldBe a[SessionResult.Corrupt]
  }

  it should "not decode a token with a non compatible signatures [RSA vs HMAC SHA256]" in {
    implicit val ss = JValueSessionSerializer.caseClass[SessionData]
    implicit val encoder = new JwtSessionEncoder[SessionData]

    val managerHmac = new SessionManager(hmacSha256Config).clientSessionManager
    val managerRsa = new SessionManager(rsaSigConfig()).clientSessionManager

    managerHmac.decode(managerRsa.encode(SessionData("john", 40))) shouldBe a[SessionResult.Corrupt]
    managerRsa.decode(managerHmac.encode(SessionData("john", 40))) shouldBe a[SessionResult.Corrupt]
  }

  it should "decode a token with 'Bearer' prefix" in {
    implicit val ss = JValueSessionSerializer.caseClass[SessionData]
    implicit val encoder = new JwtSessionEncoder[SessionData]
    val manager = new SessionManager(defaultConfig).clientSessionManager

    val data = SessionData("john", 50)

    manager.decode("Bearer " + manager.encode(data)) should be(SessionResult.Decoded(data))
  }

  it should "not decode v0.5.2 tokens without config" in {
    implicit val ss = JValueSessionSerializer.caseClass[SessionData]
    implicit val encoder = new JwtSessionEncoder[SessionData]
    val manager = new SessionManager(defaultConfig).clientSessionManager

    val data = SessionData("john", 50)

    manager.decode("Bearer " + encoder.encodeV0_5_2(data, manager.nowMillis, manager.config)) shouldBe a[
      SessionResult.Corrupt]
  }

  it should "decode v0.5.2 tokens with config" in {
    implicit val ss = JValueSessionSerializer.caseClass[SessionData]
    implicit val encoder = new JwtSessionEncoder[SessionData]
    val manager = new SessionManager(defaultConfig.copy(tokenMigrationV0_5_3Enabled = true)).clientSessionManager

    val data = SessionData("john", 50)

    manager.decode("Bearer " + encoder.encodeV0_5_2(data, manager.nowMillis, manager.config)) should be(
      SessionResult.DecodedLegacy(data))
  }
}

case class SessionData(userName: String, userId: Int)
