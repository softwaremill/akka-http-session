package com.softwaremill.session.javadsl;

import com.softwaremill.session.SessionSerializer;
import com.softwaremill.session.SessionSerializer$;
import scala.collection.immutable.Map;

/**
 * Wrapper for session serializers in com.softwaremill.session.SessionSerializer
 */
public final class SessionSerializers {

    public static final SessionSerializer<String, String> StringToStringSessionSerializer = SessionSerializer$.MODULE$.stringToStringSessionSerializer();
    public static final SessionSerializer IntToStringSessionSerializer = SessionSerializer$.MODULE$.intToStringSessionSerializer();
    public static final SessionSerializer LongToStringSessionSerializer = SessionSerializer$.MODULE$.longToStringSessionSerializer();
    public static final SessionSerializer FloatToStringSessionSerializer = SessionSerializer$.MODULE$.floatToStringSessionSerializer();
    public static final SessionSerializer DoubleToStringSessionSerializer = SessionSerializer$.MODULE$.doubleToStringSessionSerializer();
    public static final SessionSerializer<Map<String, String>, String> MapToStringSessionSerializer = SessionSerializer$.MODULE$.mapToStringSessionSerializer();

    private SessionSerializers() {
    }

}
