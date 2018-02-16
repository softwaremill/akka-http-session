package com.softwaremill.session

import org.scalacheck.{Gen, Prop, Properties}

import scala.util.Success

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

  property("decodes v0.5.1 tokens with migration config") = forAllNoShrink(secretGen) { (secret: String) =>
    forAll { (data: Map[String, String], now: Long, delta: Int, tokenMigrationV0_5_2Enabled: Boolean, tokenMigrationV0_5_3Enabled: Boolean) =>
      (data.nonEmpty) ==> {
        val config = SessionConfig.default(secret).copy(
          tokenMigrationV0_5_2Enabled = tokenMigrationV0_5_2Enabled,
          tokenMigrationV0_5_3Enabled = tokenMigrationV0_5_3Enabled)

        val encoder = new BasicSessionEncoder[Map[String, String]]
        val encodedLegacy = Legacy.encodeV0_5_1(data, System.currentTimeMillis(), config)
        val decodedResult = encoder.decode(encodedLegacy, config)

        // Decode should only work if the migrations between encoded version 0.5.1 and the current version are enabled.
        if (tokenMigrationV0_5_2Enabled && tokenMigrationV0_5_3Enabled) {
          decodedResult.map(_.signatureMatches) == Success(true)
        }
        else {
          decodedResult.isFailure || decodedResult.map(_.signatureMatches) == Success(false)
        }
      }
    }
  }

  property("decodes v0.5.2 tokens with migration config") = forAllNoShrink(secretGen) { (secret: String) =>
    forAll { (data: Map[String, String], now: Long, delta: Int, tokenMigrationV0_5_3Enabled: Boolean) =>
      (data.nonEmpty) ==> {
        val config = SessionConfig.default(secret).copy(
          tokenMigrationV0_5_3Enabled = tokenMigrationV0_5_3Enabled)

        val encoder = new BasicSessionEncoder[Map[String, String]]
        val encodedLegacy = Legacy.encodeV0_5_2(data, System.currentTimeMillis(), config)
        val decodedResult = encoder.decode(encodedLegacy, config)

        // Decode should only work if the migrations between encoded version 0.5.2 and the current version are enabled.
        if (tokenMigrationV0_5_3Enabled) {
          decodedResult.map(_.signatureMatches) == Success(true)
        }
        else {
          decodedResult.isFailure || decodedResult.map(_.signatureMatches) == Success(false)
        }
      }
    }
  }
}