package com.softwaremill.example;

import com.softwaremill.session.SessionSerializer;
import com.softwaremill.session.SessionSerializer$;
import com.softwaremill.session.SingleValueSessionSerializer;
import scala.compat.java8.JFunction0;
import scala.compat.java8.JFunction1;
import scala.util.Try;

public class MySession {

    /**
     * This session serializer converts a session type into a value (always a String type). The first two arguments are just conversion functions.
     * The third argument is a serializer responsible for preparing the data to be sent/received over the wire. There are some ready-to-use serializers available
     * in the com.softwaremill.session.SessionSerializer companion object, like stringToString and mapToString, just to name a few.
     */
    private static final SessionSerializer<MySession, String> serializer = new SingleValueSessionSerializer<>(
        (JFunction1<MySession, String>) (session) -> (session.getUsername())
        ,
        (JFunction1<String, Try<MySession>>) (login) -> Try.apply((JFunction0<MySession>) (() -> new MySession(login)))
        ,
        SessionSerializer$.MODULE$.stringToStringSessionSerializer()
    );

    private final String username;

    public MySession(String username) {
        this.username = username;
    }

    public static SessionSerializer<MySession, String> getSerializer() {
        return serializer;
    }

    public String getUsername() {
        return username;
    }

}
