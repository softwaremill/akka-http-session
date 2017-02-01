package com.softwaremill.session.javadsl;

import com.softwaremill.session.SessionSerializer;
import com.softwaremill.session.SessionSerializer$;
import scala.collection.immutable.Map;

/**
 * Wrapper for session serializers in com.softwaremill.session.SessionSerializer
 */
public final class SessionSerializers {

    public static final SessionSerializer<String, String> StringToStringSessionSerializer = SessionSerializer$.MODULE$.stringToStringSessionSerializer();
    public static final SessionSerializer<Integer, String> IntToStringSessionSerializer = (SessionSerializer<Integer, String>) (SessionSerializer) SessionSerializer$.MODULE$.intToStringSessionSerializer();
    public static final SessionSerializer<Long, String> LongToStringSessionSerializer = (SessionSerializer<Long, String>) (SessionSerializer) SessionSerializer$.MODULE$.longToStringSessionSerializer();
    public static final SessionSerializer<Float, String> FloatToStringSessionSerializer = (SessionSerializer<Float, String>) (SessionSerializer) SessionSerializer$.MODULE$.floatToStringSessionSerializer();
    public static final SessionSerializer<Double, String> DoubleToStringSessionSerializer = (SessionSerializer<Double, String>) (SessionSerializer) SessionSerializer$.MODULE$.doubleToStringSessionSerializer();
    public static final SessionSerializer<Map<String, String>, String> MapToStringSessionSerializer = SessionSerializer$.MODULE$.mapToStringSessionSerializer();

    private SessionSerializers() {
    }

}
