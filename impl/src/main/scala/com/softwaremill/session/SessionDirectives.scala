package com.softwaremill.session

trait SessionDirectives
  extends ClientSessionDirectives
  with CsrfDirectives
  with RememberMeDirectives

object SessionDirectives extends SessionDirectives
