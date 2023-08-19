package com.softwaremill.pekkohttpsession.javadsl

import com.softwaremill.pekkohttpsession
import com.softwaremill.pekkohttpsession.CsrfCheckMode

import java.util.function.Supplier
import org.apache.pekko.http.javadsl.server.Route
import org.apache.pekko.http.javadsl.server.directives.RouteAdapter

/**
 * Java alternative for com.softwaremill.pekkohttpsession.CsrfDirectives
 */
trait CsrfDirectives {

  def hmacTokenCsrfProtection[T](checkMode: CsrfCheckMode[T], inner: Supplier[Route]): Route = RouteAdapter {
    pekkohttpsession.CsrfDirectives.hmacTokenCsrfProtection(checkMode) {
      inner.get.asInstanceOf[RouteAdapter].delegate
    }
  }

  /**
    * @deprecated as of release 0.6.1, replaced by {@link #hmacTokensCsrfProtection()}
    */
  def randomTokenCsrfProtection[T](checkMode: CsrfCheckMode[T], inner: Supplier[Route]): Route =
    hmacTokenCsrfProtection(checkMode, inner)

  def setNewCsrfToken[T](checkMode: CsrfCheckMode[T], inner: Supplier[Route]): Route = RouteAdapter {
    pekkohttpsession.CsrfDirectives.setNewCsrfToken(checkMode) {
      inner.get.asInstanceOf[RouteAdapter].delegate
    }
  }

}

object CsrfDirectives extends CsrfDirectives
