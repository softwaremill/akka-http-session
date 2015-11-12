package com.softwaremill.session

import java.net.{URLDecoder, URLEncoder}

trait SessionSerializer[T] {
  def serialize(t: T): String
  def deserialize(s: String): T
}

trait ToMapSessionSerializer[T] extends SessionSerializer[T] {
  import SessionSerializer._

  def serializeToMap(t: T): Map[String, String]
  def deserializeFromMap(m: Map[String, String]): T

  override def serialize(t: T) = serializeToMap(t)
    .map { case (k, v) => urlEncode(k) + "=" + urlEncode(v) }
    .mkString("&")

  override def deserialize(s: String) = deserializeFromMap(if (s == "") Map.empty[String, String] else {
    s
      .split("&")
      .map(_.split("=", 2))
      .map(p => urlDecode(p(0)) -> urlDecode(p(1)))
      .toMap
  })
}

object SessionSerializer {
  implicit def stringSessionSerializer = new SessionSerializer[String] {
    override def serialize(t: String) = urlEncode(t)
    override def deserialize(s: String) = urlDecode(s)
  }

  implicit def intSessionSerializer = new SessionSerializer[Int] {
    override def serialize(t: Int) = urlEncode(t.toString)
    override def deserialize(s: String) = urlDecode(s).toInt
  }

  implicit def longSessionSerializer = new SessionSerializer[Long] {
    override def serialize(t: Long) = urlEncode(t.toString)
    override def deserialize(s: String) = urlDecode(s).toLong
  }

  implicit def floatSessionSerializer = new SessionSerializer[Float] {
    override def serialize(t: Float) = urlEncode(t.toString)
    override def deserialize(s: String) = urlDecode(s).toFloat
  }

  implicit def doubleSessionSerializer = new SessionSerializer[Double] {
    override def serialize(t: Double) = urlEncode(t.toString)
    override def deserialize(s: String) = urlDecode(s).toDouble
  }

  implicit def mapSessionSerializer = new ToMapSessionSerializer[Map[String, String]] {
    override def serializeToMap(t: Map[String, String]) = t
    override def deserializeFromMap(m: Map[String, String]) = m
  }

  private[session] def urlEncode(s: String): String = URLEncoder.encode(s, "UTF-8")
  private[session] def urlDecode(s: String): String = URLDecoder.decode(s, "UTF-8")
}
