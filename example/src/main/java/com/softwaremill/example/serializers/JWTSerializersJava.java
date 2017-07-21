package com.softwaremill.example.serializers;

import com.softwaremill.session.JValueSessionSerializer$;
import com.softwaremill.session.SessionSerializer;
import org.json4s.DefaultFormats$;
import org.json4s.JsonAST;

public class JWTSerializersJava {

    public static final DefaultFormats$ DefaultUtcDateFormat = DefaultFormats$.MODULE$;

    public static final SessionSerializer<String, JsonAST.JValue> StringToJValueSessionSerializer = JValueSessionSerializer$.MODULE$.stringToJValueSessionSerializer();
    public static final SessionSerializer IntToJValueSessionSerializer = JValueSessionSerializer$.MODULE$.intToJValueSessionSerializer();
    public static final SessionSerializer LongToJValueSessionSerializer = JValueSessionSerializer$.MODULE$.longToJValueSessionSerializer();
    public static final SessionSerializer FloatToJValueSessionSerializer = JValueSessionSerializer$.MODULE$.floatToJValueSessionSerializer();
    public static final SessionSerializer DoubleToJValueSessionSerializer = JValueSessionSerializer$.MODULE$.doubleToJValueSessionSerializer();

    private JWTSerializersJava() {
    }
    
}
