package com.softwaremill.session

import scala.util.Try

object Legacy {
  class MultiValueSessionSerializerV0_5_2[T](toMap: T => Map[String, String], fromMap: Map[String, String] => Try[T])
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

  def encodeV0_5_1(data: Map[String, String], nowMillis: Long, config: SessionConfig): String = {
    val serializer = new MultiValueSessionSerializerV0_5_2[Map[String, String]](identity, Try(_))
    val serialized = "x" + serializer.serialize(data)

    val withExpiry = config.sessionMaxAgeSeconds.fold(serialized) { maxAge =>
      val expiry = nowMillis + maxAge * 1000L
      s"$expiry-$serialized"
    }

    val encrypted = if (config.sessionEncryptData) Crypto.encrypt_AES(withExpiry, config.serverSecret) else withExpiry

    s"${Crypto.sign_HmacSHA1_hex(serialized, config.serverSecret)}-$encrypted"
  }

  def encodeV0_5_2(data: Map[String, String], nowMillis: Long, config: SessionConfig): String = {
    val serializer = new MultiValueSessionSerializerV0_5_2[Map[String, String]](identity, Try(_))
    val encoder = new BasicSessionEncoder[Map[String, String]]()(serializer)
    encoder.encode(data, nowMillis, config)
  }
}
