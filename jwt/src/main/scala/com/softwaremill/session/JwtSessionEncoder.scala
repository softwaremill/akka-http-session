package com.softwaremill.session

import javax.xml.bind.DatatypeConverter

import org.json4s._
import org.json4s.native.JsonMethods._

import scala.util.Try

class JwtSessionEncoder[T](implicit serializer: SessionSerializer[T, JValue], formats: Formats = DefaultFormats)
    extends SessionEncoder[T] {

  override def encode(t: T, nowMillis: Long, config: SessionConfig) = {
    val h = encode(createHeader)
    val p = encode(createPayload(t, nowMillis, config))
    val base = s"$h.$p"
    val signature = Crypto.sign_HmacSHA256_base64(base, config.serverSecret)

    s"$base.$signature"
  }

  override def decode(s: String, config: SessionConfig) = Try {
    val List(h, p, signature) = s.split("\\.").toList

    val signatureMatches = SessionUtil.constantTimeEquals(
      signature,
      Crypto.sign_HmacSHA256_base64(s"$h.$p", config.serverSecret)
    )

    for {
      jv <- decode(p)
      (t, exp) <- extractPayload(jv, config)
    } yield DecodeResult(t, exp, signatureMatches)
  }.flatten

  protected def createHeader: JValue = JObject(
    "alg" -> JString("HS256"),
    "typ" -> JString("JWT")
  )

  protected def createPayload(t: T, nowMillis: Long, config: SessionConfig): JValue = {
    val exp = config.sessionMaxAgeSeconds
      // exp must be a "NumericDate", see https://tools.ietf.org/html/draft-ietf-oauth-json-web-token-32
      .map { maxAge => nowMillis / 1000L + maxAge }
      .map { exp => "exp" -> JInt(exp) }
      .toList

    val serialized = serializer.serialize(t)
    val data = if (config.sessionEncryptData) {
      val serializedWrapped = JObject("v" -> serialized) // just in case `serialized` was a json value, not a json object
      JString(Crypto.encrypt_AES(compact(render(serializedWrapped)), config.serverSecret))
    }
    else serialized

    JObject(("data" -> data) :: exp)
  }

  protected def extractPayload(p: JValue, config: SessionConfig): Try[(T, Option[Long])] = {
    val exp = p \\ "exp" match {
      case JInt(e) => Some(e.longValue() * 1000L)
      case _ => None
    }

    val rawData = p \\ "data"
    val data = if (config.sessionEncryptData) {
      rawData match {
        case JString(s) => parse(Crypto.decrypt_AES(s, config.serverSecret)) \\ "v"
        case _ => rawData
      }
    }
    else rawData

    val t = serializer.deserialize(data)

    t.map((_, exp))
  }

  protected def encode(jv: JValue): String = DatatypeConverter.printBase64Binary(compact(render(jv)).getBytes("utf-8"))
  protected def decode(s: String): Try[JValue] = Try {
    parse(new String(DatatypeConverter.parseBase64Binary(s), "utf-8"))
  }
}
