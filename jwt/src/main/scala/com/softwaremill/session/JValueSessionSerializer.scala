package com.softwaremill.session

import org.json4s._

import scala.util.{Failure, Success, Try}

object JValueSessionSerializer {
  implicit def stringToJValueSessionSerializer: SessionSerializer[String, JValue] = new SessionSerializer[String, JValue] {
    override def serialize(t: String) = JString(t)
    override def deserialize(s: JValue) = failIfNoMatch(s) { case JString(v) => v }
  }

  implicit def intToJValueSessionSerializer: SessionSerializer[Int, JValue] = new SessionSerializer[Int, JValue] {
    override def serialize(t: Int) = JInt(t)
    override def deserialize(s: JValue) = failIfNoMatch(s) { case JInt(v) => v.intValue() }
  }

  implicit def longToJValueSessionSerializer: SessionSerializer[Long, JValue] = new SessionSerializer[Long, JValue] {
    override def serialize(t: Long) = JInt(t)
    override def deserialize(s: JValue) = failIfNoMatch(s) { case JInt(v) => v.longValue() }
  }

  implicit def floatToJValueSessionSerializer: SessionSerializer[Float, JValue] = new SessionSerializer[Float, JValue] {
    override def serialize(t: Float) = JDouble(t)
    override def deserialize(s: JValue) = failIfNoMatch(s) { case JDouble(v) => v.toFloat }
  }

  implicit def doubleToJValueSessionSerializer: SessionSerializer[Double, JValue] = new SessionSerializer[Double, JValue] {
    override def serialize(t: Double) = JDouble(t)
    override def deserialize(s: JValue) = failIfNoMatch(s) { case JDouble(v) => v }
  }

  def caseClass[T <: Product: Manifest](implicit formats: Formats = DefaultFormats): SessionSerializer[T, JValue] =
    new SessionSerializer[T, JValue] {
      override def serialize(t: T) = Extraction.decompose(t)
      override def deserialize(r: JValue) = Try { Extraction.extract[T](r) }
    }

  private def failIfNoMatch[T](s: JValue)(pf: PartialFunction[JValue, T]): Try[T] = {
    pf.lift(s).fold[Try[T]](Failure(new RuntimeException(s"Cannot deserialize $s")))(Success(_))
  }
}
