package com.softwaremill.pekkohttpsession.converters

import scala.collection.JavaConverters._

object MapConverters {

  implicit def toImmutableMap[A, B](m: java.util.Map[A, B]): scala.collection.immutable.Map[A, B] = m.asScala.toMap

}
