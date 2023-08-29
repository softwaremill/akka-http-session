package com.softwaremill.pekkoexample.serializers;

import com.softwaremill.pekkoexample.SomeJavaComplexObject;
import com.softwaremill.pekkohttpsession.MultiValueSessionSerializer;
import com.softwaremill.pekkohttpsession.converters.MapConverters;
import scala.Function1;
import scala.collection.immutable.Map;
import scala.compat.java8.JFunction0;
import scala.compat.java8.JFunction1;
import scala.util.Try;

import java.util.HashMap;

public class MyJavaMultiValueSessionSerializer extends MultiValueSessionSerializer<SomeJavaComplexObject> {

    public MyJavaMultiValueSessionSerializer(
        Function1<SomeJavaComplexObject, scala.collection.immutable.Map<String, String>> toMap,
        Function1<scala.collection.immutable.Map<String, String>, Try<SomeJavaComplexObject>> fromMap
    ) {
        super(toMap, fromMap);
    }

    public static void main(String[] args) {
        new MyJavaMultiValueSessionSerializer(
            (JFunction1<SomeJavaComplexObject, Map<String, String>>) sjco -> {
                final java.util.Map<String, String> m = new HashMap<>();
                m.put("value", sjco.getValue());
                return MapConverters.toImmutableMap(m);
            },
            (JFunction1<Map<String, String>, Try<SomeJavaComplexObject>>) value ->
                Try.apply((JFunction0<SomeJavaComplexObject>) () -> new SomeJavaComplexObject(value.get("value").get()))
        );
    }
}
