package com.softwaremill.session

import java.net.{URLDecoder, URLEncoder}

import scala.util.Try

trait SessionSerializer[T, R] {
  def serialize(t: T): R
  def deserialize(r: R): Try[T]
}

class SingleValueSessionSerializer[T, V](toValue: T => V, fromValue: V => Try[T])(
  implicit valueSerializer: SessionSerializer[V, String]) extends SessionSerializer[T, String] {

  override def serialize(t: T) = valueSerializer.serialize(toValue(t))

  override def deserialize(r: String) = valueSerializer.deserialize(r).flatMap(fromValue)
}

class MultiValueSessionSerializer[T](toMap: T => Map[String, String], fromMap: Map[String, String] => Try[T])
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

  implicit def mapToStringSessionSerializer = new MultiValueSessionSerializer[Map[String, String]](identity, Try(_))

  private[session] def urlEncode(s: String): String = URLEncoder.encode(s, "UTF-8")
  private[session] def urlDecode(s: String): String = URLDecoder.decode(s, "UTF-8")
}
