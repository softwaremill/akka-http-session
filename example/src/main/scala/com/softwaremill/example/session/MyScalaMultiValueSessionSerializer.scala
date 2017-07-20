package com.softwaremill.example.session

import com.softwaremill.session.MultiValueSessionSerializer

import scala.util.Try

class MyScalaMultiValueSessionSerializer extends MultiValueSessionSerializer[SomeScalaComplexObject](
  (sco: SomeScalaComplexObject) => Map("value" -> sco.value),
  (m: Map[String, String]) => Try(new SomeScalaComplexObject(m("value")))
) {

}
