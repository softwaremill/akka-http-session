package com.softwaremill.example.session.manager;

import com.softwaremill.session.JwtSessionEncoder;
import com.softwaremill.session.SessionConfig;
import com.softwaremill.session.SessionEncoder;
import com.softwaremill.session.SessionManager;
import com.softwaremill.session.SessionSerializer;
import com.softwaremill.session.javadsl.JwtSessionSerializers;
import org.json4s.JsonAST;

public class JWTSessionManagerJava {

    private static final SessionSerializer<String, JsonAST.JValue> SERIALIZER = JwtSessionSerializers.StringToJValueSessionSerializer;
    private static final SessionEncoder<String> ENCODER = new JwtSessionEncoder<>(SERIALIZER, JwtSessionSerializers.DefaultUtcDateFormat);
    private static final SessionManager<String> MANAGER = new SessionManager<>(SessionConfig.defaultConfig("some secret"), ENCODER);

}
