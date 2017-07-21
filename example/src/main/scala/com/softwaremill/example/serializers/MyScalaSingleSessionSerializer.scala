package com.softwaremill.example.serializers

import com.softwaremill.example.SomeScalaComplexObject
import com.softwaremill.session.SessionSerializer

import scala.util.Try

class MyScalaSingleSessionSerializer extends SessionSerializer[String, SomeScalaComplexObject] {

  override def serialize(value: String): SomeScalaComplexObject = new SomeScalaComplexObject(value)

  override def deserialize(sco: SomeScalaComplexObject): Try[String] = Try(sco.value)

}
