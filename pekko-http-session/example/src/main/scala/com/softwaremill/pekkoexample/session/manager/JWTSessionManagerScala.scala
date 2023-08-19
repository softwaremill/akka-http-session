package com.softwaremill.pekkoexample.session.manager

import com.softwaremill.pekkohttpsession.{SessionConfig, SessionManager, SessionSerializer}
import com.softwaremill.pekkohttpsession.{JValueSessionSerializer, JwtSessionEncoder}
import org.json4s.JValue

class JWTSessionManagerScala {

  case class SessionData(value: String)

  implicit val serializer: SessionSerializer[SessionData, JValue] = JValueSessionSerializer.caseClass[SessionData]
  implicit val encoder: JwtSessionEncoder[SessionData] = new JwtSessionEncoder[SessionData]
  implicit val manager: SessionManager[SessionData] = new SessionManager(SessionConfig.fromConfig())

}
