package com.softwaremill.example.serializers

import com.softwaremill.example.SomeScalaComplexObject
import com.softwaremill.session.MultiValueSessionSerializer

import scala.util.Try

class MyScalaMultiValueSessionSerializer extends MultiValueSessionSerializer[SomeScalaComplexObject](
  (sco: SomeScalaComplexObject) => Map("value" -> sco.value),
  (m: Map[String, String]) => Try(new SomeScalaComplexObject(m("value"))))
