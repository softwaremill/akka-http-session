package com.softwaremill.session

import akka.http.scaladsl.model.DateTime
import akka.http.scaladsl.model.headers.{Cookie, `Set-Cookie`}
import akka.http.scaladsl.server.AuthorizationFailedRejection
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.softwaremill.session.ClientSessionDirectives._
import org.scalatest.{ShouldMatchers, FlatSpec}

class RememberMeDirectivesTest extends FlatSpec with ScalatestRouteTest with ShouldMatchers {

  import TestData._
  val sessionCookieName = sessionConfig.clientSessionCookieConfig.name
  val cookieName = sessionConfig.rememberMeCookieConfig.name

  implicit val storage = new InMemoryRememberMeStorage[Map[String, String]] {
    override def log(msg: String) = println(msg)
  }

  def routes(implicit manager: SessionManager[Map[String, String]]) = get {
    path("set") {
      setSession(refreshable, Map("k1" -> "v1")) {
        complete { "ok" }
      }
    } ~
      path("getOpt") {
        optionalSession(refreshable) { session =>
          complete { session.toString }
        }
      } ~
      path("getReq") {
        requiredSession(refreshable) { session =>
          complete { session.toString }
        }
      } ~
      path("touchReq") {
        touchRequiredSession(refreshable) { session =>
          complete { session.toString }
        }
      } ~
      path("invalidate") {
        invalidateSession(refreshable) {
          complete { "ok" }
        }
      }
  }

  def cookiesMap = headers.collect { case `Set-Cookie`(cookie) => cookie.name -> cookie.value }.toMap

  it should "set both the session and remember me cookies" in {
    Get("/set") ~> routes ~> check {
      responseAs[String] should be ("ok")

      cookiesMap.get(sessionCookieName) should be ('defined)
      cookiesMap.get(cookieName) should be ('defined)
    }
  }

  it should "set the remember me cookie to expire" in {
    Get("/set") ~> routes ~> check {
      responseAs[String] should be ("ok")

      headers.collect { case `Set-Cookie`(cookie) if cookie.name == cookieName => cookie.maxAge }.headOption.flatten
        .getOrElse(0L) should be > (60L * 60L * 24L * 29)
    }
  }

  it should "set a new remember me cookie when the session is set again" in {
    Get("/set") ~> routes ~> check {
      val cookies1 = cookiesMap

      Get("/set") ~>
        routes ~>
        check {
          val cookies2 = cookiesMap
          cookies1(cookieName) should not be (cookies2(cookieName))
        }
    }
  }

  it should "read an optional session when both the session and remember me cookies are set" in {
    Get("/set") ~> routes ~> check {
      val cookies = cookiesMap

      Get("/getOpt") ~>
        addHeader(Cookie(sessionCookieName, cookies(sessionCookieName))) ~>
        addHeader(Cookie(cookieName, cookies(cookieName))) ~>
        routes ~>
        check {
          responseAs[String] should be ("Some(Map(k1 -> v1))")
        }
    }
  }

  it should "read an optional session when only the session cookies is set" in {
    Get("/set") ~> routes ~> check {
      val cookies = cookiesMap

      Get("/getOpt") ~>
        addHeader(Cookie(sessionCookieName, cookies(sessionCookieName))) ~>
        routes ~>
        check {
          responseAs[String] should be ("Some(Map(k1 -> v1))")
        }
    }
  }

  it should "read an optional session when no cookie is set" in {
    Get("/getOpt") ~> routes ~> check {
      responseAs[String] should be ("None")
    }
  }

  it should "read an optional session when only the remember me cookie is set (re-create the session)" in {
    Get("/set") ~> routes ~> check {
      val cookies = cookiesMap

      Get("/getOpt") ~>
        addHeader(Cookie(cookieName, cookies(cookieName))) ~>
        routes ~>
        check {
          responseAs[String] should be ("Some(Map(k1 -> v1))")
        }
    }
  }

  it should "set a new remember me cookie after the session is re-created" in {
    Get("/set") ~> routes ~> check {
      val cookies1 = cookiesMap

      Get("/getOpt") ~>
        addHeader(Cookie(cookieName, cookies1(cookieName))) ~>
        routes ~>
        check {
          val cookies2 = cookiesMap
          cookies1(cookieName) should not be (cookies2)
        }
    }
  }

  it should "read a required session when both the session and remember me cookies are set" in {
    Get("/set") ~> routes ~> check {
      val cookies = cookiesMap

      Get("/getReq") ~>
        addHeader(Cookie(sessionCookieName, cookies(sessionCookieName))) ~>
        addHeader(Cookie(cookieName, cookies(cookieName))) ~>
        routes ~>
        check {
          responseAs[String] should be ("Map(k1 -> v1)")
        }
    }
  }

  it should "invalidate a session" in {
    Get("/set") ~> routes ~> check {
      val cookies1 = cookiesMap

      Get("/invalidate") ~>
        addHeader(Cookie(sessionCookieName, cookies1(sessionCookieName))) ~>
        addHeader(Cookie(cookieName, cookies1(cookieName))) ~>
        routes ~>
        check {
          val cookiesExpiry = headers.collect { case `Set-Cookie`(cookie) => cookie.name -> cookie.expires }.toMap
          cookiesExpiry(cookieName) should be (Some(DateTime.MinValue))
          cookiesExpiry(sessionCookieName) should be (Some(DateTime.MinValue))
        }
    }
  }

  it should "reject the request if the session cookie is not set" in {
    Get("/getReq") ~> routes ~> check {
      rejection should be (AuthorizationFailedRejection)
    }
  }

  it should "reject the request if the session cookie is invalid" in {
    Get("/getReq") ~> addHeader(Cookie(sessionCookieName, "invalid")) ~> routes ~> check {
      rejection should be (AuthorizationFailedRejection)
    }
  }

  it should "reject the request if the remember me cookie is invalid" in {
    Get("/getReq") ~> addHeader(Cookie(cookieName, "invalid")) ~> routes ~> check {
      rejection should be (AuthorizationFailedRejection)
    }
  }

  it should "touch the session, keeping the remember me token intact" in {
    Get("/set") ~> routes(manager_expires60_fixedTime) ~> check {
      val cookies1 = cookiesMap

      Get("/touchReq") ~>
        addHeader(Cookie(sessionCookieName, cookies1(sessionCookieName))) ~>
        addHeader(Cookie(cookieName, cookies1(cookieName))) ~>
        routes(manager_expires60_fixedTime_plus30s) ~>
        check {
          responseAs[String] should be ("Map(k1 -> v1)")

          val cookies2 = cookiesMap

          // The session cookie should be modified with a new expiry date
          cookies1(sessionCookieName) should not be (cookies2(sessionCookieName))

          // But the remember me token should remain the same; no new cookie should be set
          cookies2.get(cookieName) should be (None)

          // 70 seconds from the initial cookie, only the touched one should work
          Get("/touchReq") ~>
            addHeader(Cookie(sessionCookieName, cookies2(sessionCookieName))) ~>
            addHeader(Cookie(cookieName, cookies1(cookieName))) ~>
            routes(manager_expires60_fixedTime_plus70s) ~>
            check {
              responseAs[String] should be ("Map(k1 -> v1)")
            }
          Get("/touchReq") ~>
            addHeader(Cookie(sessionCookieName, cookies1(sessionCookieName))) ~>
            routes(manager_expires60_fixedTime_plus70s) ~>
            check {
              rejection should be (AuthorizationFailedRejection)
            }
          // When sending the expired cookie and remember me token, a new session should start
          Get("/touchReq") ~>
            addHeader(Cookie(sessionCookieName, cookies2(sessionCookieName))) ~>
            addHeader(Cookie(cookieName, cookies1(cookieName))) ~>
            routes(manager_expires60_fixedTime_plus70s) ~>
            check {
              responseAs[String] should be ("Map(k1 -> v1)")

              val cookies3 = cookiesMap
              // new token should be generated
              cookies1(sessionCookieName) should not be (cookies3(sessionCookieName))
            }
        }
    }
  }
}
