package com.softwaremill.example.serializers

import org.json4s.ext.JavaTypesSerializers
import org.json4s.jackson.Serialization
import org.json4s.{Formats, NoTypeHints}


class JWTSerializersScala {

  implicit lazy val formats: Formats = Serialization.formats(NoTypeHints) ++ JavaTypesSerializers.all

}
