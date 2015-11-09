package com.softwaremill.session

import akka.http.scaladsl.model.{DateTime, HttpHeader}
import akka.http.scaladsl.model.headers.{Cookie, `Set-Cookie`}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.softwaremill.session.SessionDirectives._
import com.softwaremill.session.TestData._

trait MultipleTransportTest { this: ScalatestRouteTest =>

  trait TestUsingTransport {
    def transportName: String
    def getSession: Option[String]
    def setSessionHeader(s: String): HttpHeader
    def isSessionExpired: Boolean

    def getSessionTransport: GetSessionTransport
    def setSessionTransport: SetSessionTransport
  }

  object TestUsingCookies extends TestUsingTransport {
    val cookieName = sessionConfig.sessionCookieConfig.name

    val transportName = "cookies"
    def getSession = header[`Set-Cookie`].map(_.cookie.value)
    def setSessionHeader(s: String) = Cookie(cookieName, s)
    def isSessionExpired = header[`Set-Cookie`].flatMap(_.cookie.expires).contains(DateTime.MinValue)

    def getSessionTransport = usingCookies
    def setSessionTransport = usingCookies
  }
}
