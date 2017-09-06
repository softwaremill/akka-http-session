package com.softwaremill.session.javadsl

import java.util.function.Supplier

import akka.http.javadsl.server.Route
import akka.http.javadsl.server.directives.RouteAdapter
import com.softwaremill.session.CsrfCheckMode

/**
 * Java alternative for com.softwaremill.session.CsrfDirectives
 */
trait CsrfDirectives {

  def randomTokenCsrfProtection[T](checkMode: CsrfCheckMode[T], inner: Supplier[Route]): Route = RouteAdapter {
    com.softwaremill.session.CsrfDirectives.randomTokenCsrfProtection(checkMode) {
      inner.get.asInstanceOf[RouteAdapter].delegate
    }
  }

  def setNewCsrfToken[T](checkMode: CsrfCheckMode[T], inner: Supplier[Route]): Route = RouteAdapter {
    com.softwaremill.session.CsrfDirectives.setNewCsrfToken(checkMode) {
      inner.get.asInstanceOf[RouteAdapter].delegate
    }
  }

}

object CsrfDirectives extends CsrfDirectives
