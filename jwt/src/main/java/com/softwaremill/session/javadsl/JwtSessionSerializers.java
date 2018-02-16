package com.softwaremill.session.javadsl;

import com.softwaremill.session.JValueSessionSerializer$;
import com.softwaremill.session.SessionSerializer;
import org.json4s.DefaultFormats$;
import org.json4s.JsonAST;

/**
 * Wrapper for session transports in com.softwaremill.session.JValueSessionSerializer
 */
public final class JwtSessionSerializers {

    public static final DefaultFormats$ DefaultUtcDateFormat = DefaultFormats$.MODULE$;

    public static final SessionSerializer<String, JsonAST.JValue> StringToJValueSessionSerializer =
            JValueSessionSerializer$.MODULE$.stringToJValueSessionSerializer();
    public static final SessionSerializer<Integer, JsonAST.JValue> IntToJValueSessionSerializer =
            (SessionSerializer<Integer, JsonAST.JValue>) (SessionSerializer) JValueSessionSerializer$.MODULE$.intToJValueSessionSerializer();
    public static final SessionSerializer<Long, JsonAST.JValue> LongToJValueSessionSerializer =
            (SessionSerializer<Long, JsonAST.JValue>) (SessionSerializer) JValueSessionSerializer$.MODULE$.longToJValueSessionSerializer();
    public static final SessionSerializer<Float, JsonAST.JValue> FloatToJValueSessionSerializer =
            (SessionSerializer<Float, JsonAST.JValue>) (SessionSerializer) JValueSessionSerializer$.MODULE$.floatToJValueSessionSerializer();
    public static final SessionSerializer<Double, JsonAST.JValue> DoubleToJValueSessionSerializer =
            (SessionSerializer<Double, JsonAST.JValue>) (SessionSerializer) JValueSessionSerializer$.MODULE$.doubleToJValueSessionSerializer();

    private JwtSessionSerializers() {
    }

}