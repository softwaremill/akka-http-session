package com.softwaremill.session.converters

import scala.collection.JavaConverters._
import scala.language.implicitConversions

object MapConverters {

  implicit def toImmutableMap[A, B](m: java.util.Map[A, B]): Map[A, B] = m.asScala.toMap

}