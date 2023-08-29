package com.softwaremill.pekkoexample.serializers

import com.softwaremill.pekkoexample.SomeScalaComplexObject
import com.softwaremill.pekkohttpsession.SessionSerializer

import scala.util.Try

class MyScalaSingleSessionSerializer extends SessionSerializer[String, SomeScalaComplexObject] {

  override def serialize(value: String): SomeScalaComplexObject = new SomeScalaComplexObject(value)

  override def deserialize(sco: SomeScalaComplexObject): Try[String] = Try(sco.value)

}
