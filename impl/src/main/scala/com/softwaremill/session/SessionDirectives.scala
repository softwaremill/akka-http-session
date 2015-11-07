package com.softwaremill.session

trait SessionDirectives
  extends ClientSessionDirectives
  with CsrfDirectives

object SessionDirectives extends SessionDirectives
