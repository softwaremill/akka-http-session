package com.softwaremill.session

import org.json4s.JValue
import org.scalatest.{FlatSpec, Matchers}

class SessionManagerJwtEncoderTest extends FlatSpec with Matchers {
  val defaultConfig = SessionConfig.default("1234567890123456789012345678901234567890123456789012345678901234567890")
  val configMaxAge = defaultConfig.copy(sessionMaxAgeSeconds = Some(3600))
  val configEncrypted = defaultConfig.copy(sessionEncryptData = true)
  val configEncryptedMaxAge = configMaxAge.copy(sessionEncryptData = true)

  case class TestData[T](
    name: String,
    data: T,
    config: SessionConfig,
    sessionSerializer: SessionSerializer[T, JValue])

  import JValueSessionSerializer._
  val tests = List(
    TestData("string, default config", "username", defaultConfig, implicitly[SessionSerializer[String, JValue]]),
    TestData("string, with max age", "username", configMaxAge, implicitly[SessionSerializer[String, JValue]]),
    TestData("string, with encryption", "username", configEncrypted, implicitly[SessionSerializer[String, JValue]]),
    TestData("string, with max age and encryption", "username", configEncryptedMaxAge, implicitly[SessionSerializer[String, JValue]]),
    TestData("integer, default config", 12345, defaultConfig, implicitly[SessionSerializer[Int, JValue]]),
    TestData("case class, default config", SessionData("john", 10), defaultConfig, JValueSessionSerializer.caseClass[SessionData]),
    TestData("case class, with max age and encryption", SessionData("john", 20), configEncryptedMaxAge, JValueSessionSerializer.caseClass[SessionData]))

  tests.foreach { td =>
    it should s"encode+decode for ${td.name}" in {
      runTest(td)
    }
  }

  def runTest[T](td: TestData[T]): Unit = {
    implicit val ss = td.sessionSerializer
    implicit val encoder = new JwtSessionEncoder[T]
    val manager = new SessionManager(td.config).clientSessionManager

    manager.decode(manager.encode(td.data)) should be (SessionResult.Decoded(td.data))
  }

  it should "encode correctly in the JWT format" in {
    implicit val ss = JValueSessionSerializer.caseClass[SessionData]
    implicit val encoder = new JwtSessionEncoder[SessionData]

    val encoded = encoder.encode(SessionData("john", 30), 1447416197071L, defaultConfig)
    println(s"Test on: http://jwt.io/#debugger:\n$encoded")

    encoded.count(_ == '.') should be (2)
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

    managerHour3.decode(managerHour1.encode(SessionData("john", 40))) should be (SessionResult.Expired)
  }

  it should "decode a token with 'Bearer' prefix" in {
    implicit val ss = JValueSessionSerializer.caseClass[SessionData]
    implicit val encoder = new JwtSessionEncoder[SessionData]
    val manager = new SessionManager(defaultConfig).clientSessionManager

    val data = SessionData("john", 50)

    manager.decode("Bearer " + manager.encode(data)) should be (SessionResult.Decoded(data))
  }

  it should "not decode v0.5.2 tokens without config" in {
    implicit val ss = JValueSessionSerializer.caseClass[SessionData]
    implicit val encoder = new JwtSessionEncoder[SessionData]
    val manager = new SessionManager(defaultConfig).clientSessionManager

    val data = SessionData("john", 50)

    manager.decode("Bearer " + encoder.encodeV0_5_2(data, manager.nowMillis, manager.config)) shouldBe a[SessionResult.Corrupt]
  }

  it should "decode v0.5.2 tokens with config" in {
    implicit val ss = JValueSessionSerializer.caseClass[SessionData]
    implicit val encoder = new JwtSessionEncoder[SessionData]
    val manager = new SessionManager(defaultConfig.copy(tokenMigrationV0_5_3Enabled = true)).clientSessionManager

    val data = SessionData("john", 50)

    manager.decode("Bearer " + encoder.encodeV0_5_2(data, manager.nowMillis, manager.config)) should be (SessionResult.DecodedLegacy(data))
  }
}

case class SessionData(userName: String, userId: Int)