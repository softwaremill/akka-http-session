package com.softwaremill.example

import akka.http.scaladsl.model.headers.{Cookie, `Set-Cookie`}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.model.StatusCodes
import com.softwaremill.session.TestData.sessionConfig
import org.scalatest.{Matchers, WordSpec}

class ScalaExampleSpec extends WordSpec with Matchers with ScalatestRouteTest {
  val cookieName = sessionConfig.csrfCookieConfig.name
  "ScalaExample" should {
    "login do_login current_login do_logout" in {
      Get("/site/index.html") ~> Example.routes ~> check {
        status shouldBe StatusCodes.OK
        val Some(csrfCookie) = header[`Set-Cookie`]

        Post("/api/do_login", "user") ~> addHeader(Cookie(cookieName, csrfCookie.cookie.value)) ~>
          addHeader(sessionConfig.csrfSubmittedName, csrfCookie.cookie.value) ~> Example.routes ~> check {
          status shouldBe StatusCodes.OK
          responseAs[String] shouldBe "ok"

          val Some(sessionData) = header[`Set-Cookie`]

          Get("/api/current_login") ~> addHeader(Cookie(cookieName, csrfCookie.cookie.value)) ~>
            addHeader(sessionConfig.csrfSubmittedName, csrfCookie.cookie.value) ~>
            addHeader(Cookie(sessionConfig.sessionCookieConfig.name, sessionData.cookie.value)) ~> Example.routes ~> check {
            status shouldBe StatusCodes.OK
            responseAs[String] shouldBe "user"
          }

          Post("/api/do_logout") ~> addHeader(Cookie(cookieName, csrfCookie.cookie.value)) ~>
            addHeader(sessionConfig.csrfSubmittedName, csrfCookie.cookie.value) ~>
            addHeader(Cookie(sessionConfig.sessionCookieConfig.name, sessionData.cookie.value)) ~> Example.routes ~> check {
            status shouldBe StatusCodes.OK
            responseAs[String] shouldBe "ok"
          }

          Get("/api/current_login") ~> Route.seal(Example.routes) ~> check {
            status shouldBe StatusCodes.Forbidden
          }

          Get("/api/current_login") ~> addHeader(Cookie(cookieName, csrfCookie.cookie.value)) ~>
            addHeader(sessionConfig.csrfSubmittedName, csrfCookie.cookie.value) ~>
            addHeader(Cookie(sessionConfig.sessionCookieConfig.name, sessionData.cookie.value))~> Route.seal(Example.routes) ~> check {
            status shouldBe StatusCodes.Forbidden
          }
        }
      }
    }
  }
}
