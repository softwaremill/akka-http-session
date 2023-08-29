package com.softwaremill.pekkohttpsession.javadsl;

import com.softwaremill.pekkohttpsession.CookieST$;
import com.softwaremill.pekkohttpsession.HeaderST$;
import com.softwaremill.pekkohttpsession.SetSessionTransport;

/**
 * Wrapper for session transports in com.softwaremill.pekkohttpsession.SetSessionTransport
 */
public final class SessionTransports {

    public static final SetSessionTransport CookieST = CookieST$.MODULE$;
    public static final SetSessionTransport HeaderST = HeaderST$.MODULE$;

    private SessionTransports() {
    }

}
