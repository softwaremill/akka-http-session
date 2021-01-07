package com.softwaremill.session.javadsl

import java.util.function.Supplier

import akka.http.javadsl.server.Route
import akka.http.javadsl.server.directives.RouteAdapter
import com.softwaremill.session.CsrfCheckMode

/**
 * Java alternative for com.softwaremill.session.CsrfDirectives
 */
trait CsrfDirectives {

  def hmacTokenCsrfProtection[T](checkMode: CsrfCheckMode[T], inner: Supplier[Route]): Route = RouteAdapter {
    com.softwaremill.session.CsrfDirectives.hmacTokenCsrfProtection(checkMode) {
      inner.get.asInstanceOf[RouteAdapter].delegate
    }
  }

  /**
    * @deprecated as of release 0.6.1, replaced by {@link #hmacTokensCsrfProtection()}
    */
  def randomTokenCsrfProtection[T](checkMode: CsrfCheckMode[T], inner: Supplier[Route]): Route =
    hmacTokenCsrfProtection(checkMode, inner)

  def setNewCsrfToken[T](checkMode: CsrfCheckMode[T], inner: Supplier[Route]): Route = RouteAdapter {
    com.softwaremill.session.CsrfDirectives.setNewCsrfToken(checkMode) {
      inner.get.asInstanceOf[RouteAdapter].delegate
    }
  }

}

object CsrfDirectives extends CsrfDirectives
