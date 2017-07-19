package com.softwaremill.example;

import com.softwaremill.session.SessionSerializer;
import com.softwaremill.session.SingleValueSessionSerializer;
import com.softwaremill.session.javadsl.SessionSerializers;
import scala.compat.java8.JFunction0;
import scala.compat.java8.JFunction1;
import scala.util.Try;

public class JavaSession {

    /**
     * This session serializer converts a session type into a value (always a String type). The first two arguments are just conversion functions.
     * The third argument is a serializer responsible for preparing the data to be sent/received over the wire. There are some ready-to-use serializers available
     * in the com.softwaremill.session.SessionSerializer companion object, like stringToString and mapToString, just to name a few.
     */
    private static final SessionSerializer<JavaSession, String> serializer = new SingleValueSessionSerializer<>(
        (JFunction1<JavaSession, String>) (session) -> (session.getUsername())
        ,
        (JFunction1<String, Try<JavaSession>>) (login) -> Try.apply((JFunction0<JavaSession>) (() -> new JavaSession(login)))
        ,
        SessionSerializers.StringToStringSessionSerializer
    );

    private final String username;

    public JavaSession(String username) {
        this.username = username;
    }

    public static SessionSerializer<JavaSession, String> getSerializer() {
        return serializer;
    }

    public String getUsername() {
        return username;
    }

}
