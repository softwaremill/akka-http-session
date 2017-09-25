package com.softwaremill.session.javadsl;

import com.softwaremill.session.MultiValueSessionSerializer;
import com.softwaremill.session.SessionSerializer;
import com.softwaremill.session.SessionSerializer$;
import com.softwaremill.session.converters.MapConverters;
import scala.collection.JavaConverters;
import scala.compat.java8.JFunction0;
import scala.compat.java8.JFunction1;
import scala.util.Try;

import java.util.Map;

/**
 * Wrapper for session serializers in com.softwaremill.session.SessionSerializer
 */
public final class SessionSerializers {

    public static final SessionSerializer<String, String> StringToStringSessionSerializer = SessionSerializer$.MODULE$.stringToStringSessionSerializer();
    public static final SessionSerializer<Integer, String> IntToStringSessionSerializer = (SessionSerializer<Integer, String>) (SessionSerializer) SessionSerializer$.MODULE$.intToStringSessionSerializer();
    public static final SessionSerializer<Long, String> LongToStringSessionSerializer = (SessionSerializer<Long, String>) (SessionSerializer) SessionSerializer$.MODULE$.longToStringSessionSerializer();
    public static final SessionSerializer<Float, String> FloatToStringSessionSerializer = (SessionSerializer<Float, String>) (SessionSerializer) SessionSerializer$.MODULE$.floatToStringSessionSerializer();
    public static final SessionSerializer<Double, String> DoubleToStringSessionSerializer = (SessionSerializer<Double, String>) (SessionSerializer) SessionSerializer$.MODULE$.doubleToStringSessionSerializer();

    public static final SessionSerializer<Map<String, String>, String> MapToStringSessionSerializer = new MultiValueSessionSerializer<>(
        (JFunction1<Map<String, String>, scala.collection.immutable.Map<String, String>>) m -> MapConverters.toImmutableMap(m),
        (JFunction1<scala.collection.immutable.Map<String, String>, Try<Map<String, String>>>) v1 ->
            Try.apply((JFunction0<Map<String, String>>) () -> JavaConverters.mapAsJavaMapConverter(v1).asJava())
    );

    private SessionSerializers() {
    }

}
