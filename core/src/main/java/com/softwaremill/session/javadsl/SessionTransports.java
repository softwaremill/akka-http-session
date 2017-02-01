package com.softwaremill.session.javadsl;

import com.softwaremill.session.CookieST$;
import com.softwaremill.session.HeaderST$;
import com.softwaremill.session.SetSessionTransport;

/**
 * Wrapper for session transports in com.softwaremill.session.SetSessionTransport
 */
public final class SessionTransports {

    public static final SetSessionTransport CookieST = CookieST$.MODULE$;
    public static final SetSessionTransport HeaderST = HeaderST$.MODULE$;

    private SessionTransports() {
    }

}
