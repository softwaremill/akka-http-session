package com.softwaremill.session

import scala.util.Try

trait SessionEncoder[T] {
  def encode(t: T, nowMillis: Long, config: SessionConfig): String
  def decode(s: String, config: SessionConfig): Try[DecodeResult[T]]
}

object SessionEncoder {
  /**
   * Default low-priority implicit encoder. If you wish to use another one, provide an implicit encoder in a
   * higher-priority implicit scope, e.g. as an implicit value declared next to `SessionManager`.
   */
  implicit def basic[T](implicit serializer: SessionSerializer[T, String]) = new BasicSessionEncoder[T]()
}

case class DecodeResult[T](t: T, expires: Option[Long], signatureMatches: Boolean, isLegacy: Boolean)

/**
 * @param serializer Must create cookie-safe strings (only with allowed characters).
 */
class BasicSessionEncoder[T](implicit serializer: SessionSerializer[T, String]) extends SessionEncoder[T] {

  override def encode(t: T, nowMillis: Long, config: SessionConfig) = {
    // adding an "x" so that the string is never empty, even if there's no data
    val serialized = "x" + serializer.serialize(t)

    val withExpiry = config.sessionMaxAgeSeconds.fold(serialized) { maxAge =>
      val expiry = nowMillis + maxAge * 1000L
      s"$expiry-$serialized"
    }

    val encrypted = if (config.sessionEncryptData) Crypto.encrypt_AES(withExpiry, config.serverSecret) else withExpiry

    s"${Crypto.sign_HmacSHA1_hex(withExpiry, config.serverSecret)}-$encrypted"
  }

  override def decode(s: String, config: SessionConfig): Try[DecodeResult[T]] = {
    def extractExpiry(data: String): (Option[Long], String) = {
      config.sessionMaxAgeSeconds.fold((Option.empty[Long], data)) { maxAge =>
        val splitted = data.split("-", 2)
        (Some(splitted(0).toLong), splitted(1))
      }
    }

    def verifySignature(tokenSignature: String, expectedValue: String) = {
      SessionUtil.constantTimeEquals(
        tokenSignature,
        Crypto.sign_HmacSHA1_hex(expectedValue, config.serverSecret))
    }

    Try {
      val splitted = s.split("-", 2)
      val decrypted = if (config.sessionEncryptData) Crypto.decrypt_AES(splitted(1), config.serverSecret) else splitted(1)
      val (expiry, serialized) = extractExpiry(decrypted)

      val (deserializedResult, deserializedLegacy) = {
        val deserializedResult = serializer.deserialize(serialized.substring(1))

        if (deserializedResult.isFailure && config.tokenMigrationV0_5_3Enabled)
          (serializer.deserializeV0_5_2(serialized.substring(1)), true) // Try deserializer assuming pre-v0.5.3.
        else (deserializedResult, false)
      }

      deserializedResult.map { deserialized =>
        val signatureMatches = verifySignature(splitted(0), decrypted)

        if (!signatureMatches && config.tokenMigrationV0_5_2Enabled) {
          // Try signature check assuming pre-v0.5.2.
          val signatureMatchesLegacy = verifySignature(splitted(0), serialized)
          val isLegacy = signatureMatchesLegacy || deserializedLegacy

          DecodeResult(deserialized, expiry, signatureMatchesLegacy, isLegacy)
        }
        else DecodeResult(deserialized, expiry, signatureMatches, isLegacy = deserializedLegacy)
      }
    }.flatten
  }
}