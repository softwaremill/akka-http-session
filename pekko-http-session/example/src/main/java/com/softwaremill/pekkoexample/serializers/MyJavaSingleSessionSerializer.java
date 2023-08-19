package com.softwaremill.pekkoexample.serializers;

import com.softwaremill.pekkoexample.SomeJavaComplexObject;
import com.softwaremill.pekkohttpsession.SessionSerializer;
import com.softwaremill.pekkohttpsession.SingleValueSessionSerializer;
import com.softwaremill.pekkohttpsession.javadsl.SessionSerializers;
import scala.Function1;
import scala.compat.java8.JFunction0;
import scala.compat.java8.JFunction1;
import scala.util.Try;

public class MyJavaSingleSessionSerializer extends SingleValueSessionSerializer<SomeJavaComplexObject, String> {

    public MyJavaSingleSessionSerializer(
        Function1<SomeJavaComplexObject, String> toValue,
        Function1<String, Try<SomeJavaComplexObject>> fromValue,
        SessionSerializer<String, String> valueSerializer
    ) {
        super(toValue, fromValue, valueSerializer);
    }

    public static void main(String[] args) {
        new MyJavaSingleSessionSerializer(
            (JFunction1<SomeJavaComplexObject, String>) SomeJavaComplexObject::getValue,
            (JFunction1<String, Try<SomeJavaComplexObject>>) value -> Try.apply((JFunction0<SomeJavaComplexObject>) () -> new SomeJavaComplexObject(value)),
            SessionSerializers.StringToStringSessionSerializer
        );
    }
}
