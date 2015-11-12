package com.softwaremill.session

import java.net.{URLDecoder, URLEncoder}

import scala.util.Try

trait SessionSerializer[T, R] {
  def serialize(t: T): R
  def deserialize(r: R): Try[T]
}

trait SessionEncoder[T] {
  def encode(t: T, nowMillis: Long, config: SessionConfig): String
  def decode(s: String, config: SessionConfig): Try[DecodeResult[T]]
}

object SessionEncoder {
  implicit def basic[T](implicit serializer: SessionSerializer[T, String]) = new BasicSessionEncoder[T]()
}

case class DecodeResult[T](t: T, expires: Option[Long], signatureMatches: Boolean)

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

    val encrypted = if (config.sessionEncryptData) Crypto.encryptAES(withExpiry, config.serverSecret) else withExpiry

    s"${Crypto.signHmacSHA1(serialized, config.serverSecret)}-$encrypted"
  }

  override def decode(s: String, config: SessionConfig) = {
    def extractExpiry(data: String): (Option[Long], String) = {
      config.sessionMaxAgeSeconds.fold((Option.empty[Long], data)) { maxAge =>
        val splitted = data.split("-", 2)
        (Some(splitted(0).toLong), splitted(1))
      }
    }

    Try {
      val splitted = s.split("-", 2)
      val decrypted = if (config.sessionEncryptData) Crypto.decryptAES(splitted(1), config.serverSecret) else splitted(1)

      val (expiry, serialized) = extractExpiry(decrypted)

      val signatureMatches = SessionUtil.constantTimeEquals(
        splitted(0),
        Crypto.signHmacSHA1(serialized, config.serverSecret)
      )

      serializer.deserialize(serialized.substring(1)).map {
        DecodeResult(_, expiry, signatureMatches)
      }
    }.flatten
  }
}

class ViaMapSessionSerializer[T](toMap: T => Map[String, String], fromMap: Map[String, String] => Try[T])
    extends SessionSerializer[T, String] {

  import SessionSerializer._

  override def serialize(t: T) = toMap(t)
    .map { case (k, v) => urlEncode(k) + "=" + urlEncode(v) }
    .mkString("&")

  override def deserialize(s: String) = {
    Try {
      if (s == "") Map.empty[String, String]
      else {
        s
          .split("&")
          .map(_.split("=", 2))
          .map(p => urlDecode(p(0)) -> urlDecode(p(1)))
          .toMap
      }
    }.flatMap(fromMap)
  }
}

object SessionSerializer {
  implicit def stringToStringSessionSerializer = new SessionSerializer[String, String] {
    override def serialize(t: String) = urlEncode(t)
    override def deserialize(s: String) = Try(urlDecode(s))
  }

  implicit def intToStringSessionSerializer = new SessionSerializer[Int, String] {
    override def serialize(t: Int) = urlEncode(t.toString)
    override def deserialize(s: String) = Try(urlDecode(s).toInt)
  }

  implicit def longToStringSessionSerializer = new SessionSerializer[Long, String] {
    override def serialize(t: Long) = urlEncode(t.toString)
    override def deserialize(s: String) = Try(urlDecode(s).toLong)
  }

  implicit def floatToStringSessionSerializer = new SessionSerializer[Float, String] {
    override def serialize(t: Float) = urlEncode(t.toString)
    override def deserialize(s: String) = Try(urlDecode(s).toFloat)
  }

  implicit def doubleToStringSessionSerializer = new SessionSerializer[Double, String] {
    override def serialize(t: Double) = urlEncode(t.toString)
    override def deserialize(s: String) = Try(urlDecode(s).toDouble)
  }

  implicit def mapToStringSessionSerializer = new ViaMapSessionSerializer[Map[String, String]](identity, Try(_))

  private[session] def urlEncode(s: String): String = URLEncoder.encode(s, "UTF-8")
  private[session] def urlDecode(s: String): String = URLDecoder.decode(s, "UTF-8")
}
