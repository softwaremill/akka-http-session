package com.softwaremill.example.session

import com.softwaremill.session.{SessionSerializer, SingleValueSessionSerializer}

import scala.util.Try

case class MyScalaSession(username: String)

object MyScalaSession {
  implicit def serializer: SessionSerializer[MyScalaSession, String] = new SingleValueSessionSerializer(
    _.username,
    (un: String) => Try(MyScalaSession(un)))
}