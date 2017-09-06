package com.softwaremill.session

import org.scalacheck.{Gen, Prop, Properties}

import scala.util.{Success, Try}

object SessionManagerBasicEncoderTest extends Properties("SessionManagerBasicEncoder") {

  import Prop._

  val secretGen = Gen.choose(64, 256).flatMap(size => Gen.listOfN(size, Gen.alphaNumChar).map(_.mkString))

  property("encode+decode") = forAllNoShrink(secretGen) { (secret: String) =>
    forAll { (encrypt: Boolean, useMaxAgeSeconds: Boolean, data: Map[String, String]) =>
      val config = SessionConfig.default(secret)
        .copy(sessionEncryptData = encrypt)
        .copy(sessionMaxAgeSeconds = if (useMaxAgeSeconds) Some(3600L) else None)
      val manager = new SessionManager[Map[String, String]](config).clientSessionManager

      manager.decode(manager.encode(data)) == SessionResult.Decoded(data)
    }
  }

  property("doesn't decode expired session") = forAllNoShrink(secretGen) { (secret: String) =>
    forAll { (encrypt: Boolean, data: Map[String, String]) =>
      val config = SessionConfig.default(secret)
        .copy(sessionEncryptData = encrypt)
        .copy(sessionMaxAgeSeconds = Some(20L)) // expires after 20s
      val managerPast = new SessionManager[Map[String, String]](config) {
        override def nowMillis = 8172L * 1000L
      }.clientSessionManager
      val managerFuture = new SessionManager[Map[String, String]](config) {
        override def nowMillis = (8172L + 600L) * 1000L // 600s later
      }.clientSessionManager

      managerFuture.decode(managerPast.encode(data)) == SessionResult.Expired
    }
  }

  property("doesn't decode session with tampered expiry") = forAllNoShrink(secretGen) { (secret: String) =>
    forAll { (data: Map[String, String], now: Long, delta: Int) =>
      (delta >= 0) ==> {
        val config = SessionConfig.default(secret)
        val encoder = new BasicSessionEncoder[Map[String, String]]

        val enc = encoder.encode(data, System.currentTimeMillis(), config)
        val Array(sig, exp, payload) = enc.split("-", 3)
        val tampered = s"$sig-${exp.toLong + delta}-$payload"

        // the signature should only match if we didn't add anything to the expiry date
        encoder.decode(tampered, config).map(_.signatureMatches) == Success(delta == 0L)
      }
    }
  }
}
