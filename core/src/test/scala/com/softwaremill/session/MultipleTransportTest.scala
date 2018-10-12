package com.softwaremill.session

import akka.http.scaladsl.model.{DateTime, HttpHeader}
import akka.http.scaladsl.model.headers.{RawHeader, HttpCookie, Cookie, `Set-Cookie`}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.softwaremill.session.SessionOptions._
import com.softwaremill.session.TestData._

trait MultipleTransportTest { this: ScalatestRouteTest =>

  trait TestUsingTransport {
    def transportName: String

    def getSession: Option[String]
    def countSessionHeaders: Int
    def setSessionHeader(s: String): HttpHeader
    def isSessionExpired: Boolean

    def getRefreshToken: Option[String]
    def countRefreshTokenHeaders: Int
    def setRefreshTokenHeader(s: String): HttpHeader
    def isRefreshTokenExpired: Boolean

    def getSessionTransport: GetSessionTransport
    def setSessionTransport: SetSessionTransport
  }

  object TestUsingCookies extends TestUsingTransport {
    val sessionCookieName = sessionConfig.sessionCookieConfig.name
    val refreshTokenCookieName = sessionConfig.refreshTokenCookieConfig.name

    val transportName = "cookies"

    def cookiesMap: Map[String, HttpCookie] =
      headers.collect { case `Set-Cookie`(cookie) => cookie.name -> cookie }.toMap
    private def countCookies(name: String): Int = headers.count {
      case `Set-Cookie`(cookie) => cookie.name == name
      case _                    => false
    }

    def getSession = cookiesMap.get(sessionCookieName).map(_.value)
    def countSessionHeaders = countCookies(sessionCookieName)
    def setSessionHeader(s: String) = Cookie(sessionCookieName, s)
    def isSessionExpired = cookiesMap.get(sessionCookieName).flatMap(_.expires).contains(DateTime.MinValue)

    def getRefreshToken = cookiesMap.get(refreshTokenCookieName).map(_.value)
    def countRefreshTokenHeaders = countCookies(refreshTokenCookieName)
    def setRefreshTokenHeader(s: String) = Cookie(refreshTokenCookieName, s)
    def isRefreshTokenExpired = cookiesMap.get(refreshTokenCookieName).flatMap(_.expires).contains(DateTime.MinValue)

    def getSessionTransport = usingCookies
    def setSessionTransport = usingCookies
  }

  object TestUsingHeaders extends TestUsingTransport {
    val setSessionHeaderName = sessionConfig.sessionHeaderConfig.sendToClientHeaderName
    val sessionHeaderName = sessionConfig.sessionHeaderConfig.getFromClientHeaderName
    val setRefreshTokenHeaderName = sessionConfig.refreshTokenHeaderConfig.sendToClientHeaderName
    val refreshTokenHeaderName = sessionConfig.refreshTokenHeaderConfig.getFromClientHeaderName

    val transportName = "headers"

    private def countHeaders(name: String): Int = headers.count(_.is(name.toLowerCase))

    def getSession = header(setSessionHeaderName).map(_.value)
    def countSessionHeaders = countHeaders(setSessionHeaderName)
    def setSessionHeader(s: String) = RawHeader(sessionHeaderName, s)
    def isSessionExpired = getSession.contains("")

    def getRefreshToken = header(setRefreshTokenHeaderName).map(_.value)
    def countRefreshTokenHeaders = countHeaders(setRefreshTokenHeaderName)
    def setRefreshTokenHeader(s: String) = RawHeader(refreshTokenHeaderName, s)
    def isRefreshTokenExpired = getRefreshToken.contains("")

    def getSessionTransport = usingHeaders
    def setSessionTransport = usingHeaders
  }
}
