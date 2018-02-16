package com.softwaremill.session.javadsl

import java.util.function.Supplier

import akka.http.javadsl.server.Route
import akka.http.javadsl.server.directives.RouteAdapter
import com.softwaremill.session.{CsrfCheckMode, CsrfDirectives => _CsrfDirectives}

/**
 * Java alternative for com.softwaremill.session.CsrfDirectives
 */
trait CsrfDirectives {

  def randomTokenCsrfProtection[T](checkMode: CsrfCheckMode[T], inner: Supplier[Route]): Route = RouteAdapter {
    _CsrfDirectives.randomTokenCsrfProtection(checkMode) {
      inner.get.asInstanceOf[RouteAdapter].delegate
    }
  }

  def setNewCsrfToken[T](checkMode: CsrfCheckMode[T], inner: Supplier[Route]): Route = RouteAdapter {
    _CsrfDirectives.setNewCsrfToken(checkMode) {
      inner.get.asInstanceOf[RouteAdapter].delegate
    }
  }

}

object CsrfDirectives extends CsrfDirectives