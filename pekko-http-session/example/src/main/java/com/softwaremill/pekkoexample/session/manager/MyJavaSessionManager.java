package com.softwaremill.pekkoexample.session.manager;

import com.softwaremill.pekkohttpsession.BasicSessionEncoder;
import com.softwaremill.pekkohttpsession.SessionConfig;
import com.softwaremill.pekkohttpsession.SessionEncoder;
import com.softwaremill.pekkohttpsession.SessionManager;

import static com.softwaremill.pekkohttpsession.javadsl.SessionSerializers.LongToStringSessionSerializer;

public class MyJavaSessionManager {

    static final SessionEncoder<Long> BASIC_ENCODER = new BasicSessionEncoder<>(LongToStringSessionSerializer);
    static final SessionConfig SESSION_CONFIG = SessionConfig.defaultConfig("some very long unusual string");
    static final SessionManager<Long> SESSION_MANAGER = new SessionManager<>(SESSION_CONFIG, BASIC_ENCODER);

}
