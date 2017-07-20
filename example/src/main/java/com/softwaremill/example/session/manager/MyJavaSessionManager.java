package com.softwaremill.example.session.manager;

import com.softwaremill.session.BasicSessionEncoder;
import com.softwaremill.session.SessionConfig;
import com.softwaremill.session.SessionEncoder;
import com.softwaremill.session.SessionManager;

import static com.softwaremill.session.javadsl.SessionSerializers.LongToStringSessionSerializer;

public class MyJavaSessionManager {

    static final SessionEncoder<Long> BASIC_ENCODER = new BasicSessionEncoder<>(LongToStringSessionSerializer);
    static final SessionConfig SESSION_CONFIG = SessionConfig.defaultConfig("some very long unusual string");
    static final SessionManager<Long> SESSION_MANAGER = new SessionManager<Long>(SESSION_CONFIG, BASIC_ENCODER);

}
