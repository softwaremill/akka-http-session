package com.softwaremill.pekkoexample.session

import com.softwaremill.pekkohttpsession.{SessionSerializer, SingleValueSessionSerializer}

import scala.util.Try

case class MyScalaSession(username: String)

object MyScalaSession {
  implicit def serializer: SessionSerializer[MyScalaSession, String] =
    new SingleValueSessionSerializer(_.username,
                                     (un: String) =>
                                       Try {
                                         MyScalaSession(un)
                                     })
}
