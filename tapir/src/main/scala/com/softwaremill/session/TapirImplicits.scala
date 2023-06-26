package com.softwaremill.session

import akka.http.scaladsl.model.headers.{HttpCookie, RawHeader}
import akka.http.scaladsl.model.{headers => AkkaHeaders}
import sttp.model.Header
import sttp.model.headers.Cookie.SameSite
import sttp.model.headers.{CookieValueWithMeta, CookieWithMeta}

import java.time.Instant

object TapirImplicits {

  implicit def akkaSameSiteToTapirSameSite(
      maybeSameSite: Option[AkkaHeaders.SameSite]
  ): Option[SameSite] = {
    maybeSameSite match {
      case Some(value) => Some(value)
      case None        => None
    }
  }

  implicit def akkaSameSiteToTapirSameSite(sameSite: AkkaHeaders.SameSite): SameSite = {
    sameSite match {
      case AkkaHeaders.SameSite.Lax    => SameSite.Lax
      case AkkaHeaders.SameSite.Strict => SameSite.Strict
      case AkkaHeaders.SameSite.None   => SameSite.None
    }
  }

  implicit def httpCookieToTapirCookieWithMeta(cookie: HttpCookie): CookieWithMeta = {
    CookieWithMeta(
      name = cookie.name,
      valueWithMeta = CookieValueWithMeta.unsafeApply(
        value = cookie.value(),
        expires = cookie.expires.map(expires => Instant.ofEpochMilli(expires.clicks)),
        maxAge = cookie.maxAge,
        domain = cookie.domain,
        path = cookie.path,
        secure = cookie.secure,
        httpOnly = cookie.httpOnly,
        sameSite = cookie.sameSite
      )
    )
  }

  implicit def akkaRawHeaderToTapirHeader(header: RawHeader): Header = {
    Header.unsafeApply(header.name, header.value)
  }
}
