package com.softwaremill.pekkohttpsession.javadsl

import com.softwaremill.pekkohttpsession
import com.softwaremill.pekkohttpsession.{GetSessionTransport, OneOffSessionDirectives, RefreshableSessionDirectives, SessionContinuity, SessionResult, SetSessionTransport}

import java.util.Optional
import java.util.function.Supplier
import org.apache.pekko.http.javadsl.server.Route
import org.apache.pekko.http.javadsl.server.directives.RouteAdapter

import scala.compat.java8.OptionConverters._

/**
 * Java alternative for com.softwaremill.pekkohttpsession.SessionDirectives
 */
trait SessionDirectives extends OneOffSessionDirectives with RefreshableSessionDirectives {

  def session[T](sc: SessionContinuity[T], st: GetSessionTransport, inner: java.util.function.Function[SessionResult[T], Route]): Route = RouteAdapter {
    pekkohttpsession.SessionDirectives.session(sc, st) { sessionResult =>
      inner.apply(sessionResult).asInstanceOf[RouteAdapter].delegate
    }
  }

  def setSession[T](sc: SessionContinuity[T], st: SetSessionTransport, v: T, inner: Supplier[Route]): Route = RouteAdapter {
    pekkohttpsession.SessionDirectives.setSession(sc, st, v) {
      inner.get.asInstanceOf[RouteAdapter].delegate
    }
  }

  def invalidateSession[T](sc: SessionContinuity[T], st: GetSessionTransport, inner: Supplier[Route]): Route = RouteAdapter {
    pekkohttpsession.SessionDirectives.invalidateSession(sc, st) {
      inner.get.asInstanceOf[RouteAdapter].delegate
    }
  }

  def optionalSession[T](sc: SessionContinuity[T], st: GetSessionTransport, inner: java.util.function.Function[Optional[T], Route]): Route = RouteAdapter {
    pekkohttpsession.SessionDirectives.optionalSession(sc, st) { session =>
      inner.apply(session.asJava).asInstanceOf[RouteAdapter].delegate
    }
  }

  def requiredSession[T](sc: SessionContinuity[T], st: GetSessionTransport, inner: java.util.function.Function[T, Route]): Route = RouteAdapter {
    pekkohttpsession.SessionDirectives.requiredSession(sc, st) { session =>
      inner.apply(session).asInstanceOf[RouteAdapter].delegate
    }
  }

  def touchRequiredSession[T](sc: SessionContinuity[T], st: GetSessionTransport, inner: java.util.function.Function[T, Route]): Route = RouteAdapter {
    pekkohttpsession.SessionDirectives.touchRequiredSession(sc, st) { session =>
      inner.apply(session).asInstanceOf[RouteAdapter].delegate
    }
  }

}

object SessionDirectives extends SessionDirectives

