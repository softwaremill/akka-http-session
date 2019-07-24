package com.softwaremill.session

import java.util.{Base64, UUID}
import org.json4s._
import org.json4s.jackson.JsonMethods._
import scala.util.Try

class JwtSessionEncoder[T](implicit serializer: SessionSerializer[T, JValue], formats: Formats = DefaultFormats)
    extends SessionEncoder[T] {

  override def encode(t: T, nowMillis: Long, config: SessionConfig) = {
    val h = encode(createHeader(config))
    val p = encode(createPayload(t, nowMillis, config))
    val base = s"$h.$p"
    val signature = config.jws.alg.sign(base)

    s"$base.$signature"
  }

  // Legacy encoder function for testing migrations.
  def encodeV0_5_2(t: T, nowMillis: Long, config: SessionConfig) = {
    val h = encode(createHeader(config))
    val p = encode(createPayload(t, nowMillis, config))
    val base = s"$h.$p"
    val signature = Crypto.sign_HmacSHA256_base64_v0_5_2(base, config.serverSecret)

    s"$base.$signature"
  }

  override def decode(s: String, config: SessionConfig) =
    Try {
      val sCleaned = if (s.startsWith("Bearer")) s.substring(7).trim else s
      val List(h, p, signature) = sCleaned.split("\\.").toList

      val (decodedValue, decodedLegacy) = {
        val decodedValue = decode(p)

        if (decodedValue.isFailure && config.tokenMigrationV0_5_3Enabled) {
          // Try decoding assuming pre-v0.5.3.
          (decodeV0_5_2(p), true)
        } else {
          (decodedValue, false)
        }
      }

      for {
        jv <- decodedValue
        (t, exp) <- extractPayload(jv, config)
      } yield {
        val signatureMatches =
          SessionUtil.constantTimeEquals(signature, config.jws.alg.sign(s"$h.$p"))

        if (!signatureMatches && config.tokenMigrationV0_5_3Enabled) {
          // Try signature check assuming pre-v0.5.3.
          val signatureMatchesLegacy =
            SessionUtil.constantTimeEquals(signature,
                                           Crypto.sign_HmacSHA256_base64_v0_5_2(s"$h.$p", config.serverSecret))

          val isLegacy = signatureMatchesLegacy || decodedLegacy
          DecodeResult(t, exp, signatureMatchesLegacy, isLegacy = isLegacy)
        } else {
          DecodeResult(t, exp, signatureMatches, isLegacy = decodedLegacy)
        }
      }
    }.flatten

  protected def createHeader(config: SessionConfig): JValue = JObject("alg" -> JString(config.jws.alg.value), "typ" -> JString("JWT"))

  protected def createPayload(t: T, nowMillis: Long, config: SessionConfig): JValue = {
    val registeredClaims: List[(String, JValue)] = {
      def numericDateClaimFromTimeout(key: String, numericDate: Option[Long]): Option[(String, JInt)] =
        numericDate.map(timeout => key -> JInt((nowMillis / 1000L) + timeout))

      def stringClaim(key: String, value: Option[String]): Option[(String, JString)] =
        value.map(key -> JString(_))

      import config.jwt._
      val iss = stringClaim("iss", issuer).toList
      val sub = stringClaim("sub", subject).toList
      val aud = stringClaim("aud", audience).toList

      // 'exp', 'nbf' and 'iat' must be a "NumericDate",
      // see https://tools.ietf.org/html/rfc7519#page-9
      // and https://tools.ietf.org/html/rfc7519#page-6
      val exp = numericDateClaimFromTimeout("exp", expirationTimeout).toList
      val nbf = numericDateClaimFromTimeout("nbf", notBeforeOffset).toList
      val iat = if (includeIssuedAt) numericDateClaimFromTimeout("iat", Some(0L)).toList else Nil

      // 'jti' must be unique per token,
      // collisions MUST be prevented even among values produced by different issuers,
      // see https://tools.ietf.org/html/rfc7519#page-10
      val jti = if (includeRandomJwtId) {
        stringClaim("jti", Some(issuer.map(_ + "-").getOrElse("") + UUID.randomUUID().toString)).toList
      } else Nil

      iss ++ sub ++ aud ++ exp ++ nbf ++ iat ++ jti
    }

    val serialized = serializer.serialize(t)
    val data = if (config.sessionEncryptData) {
      val serializedWrapped = JObject("v" -> serialized) // just in case `serialized` was a json value, not a json object
      JString(Crypto.encrypt_AES(compact(render(serializedWrapped)), config.serverSecret))
    } else serialized

    JObject(("data" -> data) :: registeredClaims)
  }

  protected def extractPayload(p: JValue, config: SessionConfig): Try[(T, Option[Long])] = {
    val exp = p \\ "exp" match {
      case JInt(e) => Some(e.longValue * 1000L)
      case _       => None
    }

    val rawData = p \\ "data"
    val data = if (config.sessionEncryptData) {
      rawData match {
        case JString(s) => parse(Crypto.decrypt_AES(s, config.serverSecret)) \\ "v"
        case _          => rawData
      }
    } else rawData

    val t = serializer.deserialize(data)

    t.map((_, exp))
  }

  protected def encode(jv: JValue): String =
    Base64.getUrlEncoder.withoutPadding().encodeToString(compact(render(jv)).getBytes("utf-8"))
  protected def decode(s: String): Try[JValue] = Try {
    parse(new String(Base64.getUrlDecoder.decode(s), "utf-8"))
  }
  protected def decodeV0_5_2(s: String): Try[JValue] = Try {
    parse(new String(SessionUtil.parseBase64_v0_5_2(s), "utf-8"))
  }
}
