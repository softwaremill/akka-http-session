package com.softwaremill.pekkoexample.serializers

import com.softwaremill.pekkoexample.SomeScalaComplexObject
import com.softwaremill.pekkohttpsession.MultiValueSessionSerializer

import scala.util.Try

class MyScalaMultiValueSessionSerializer
    extends MultiValueSessionSerializer[SomeScalaComplexObject](
      (sco: SomeScalaComplexObject) => Map("value" -> sco.value),
      (m: Map[String, String]) => Try(new SomeScalaComplexObject(m("value"))))
