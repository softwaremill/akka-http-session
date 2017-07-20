package com.softwaremill.example.session;

import com.softwaremill.session.MultiValueSessionSerializer;
import com.softwaremill.session.converters.MapConverters;
import scala.Function1;
import scala.util.Try;

public class MyJavaMultiValueSessionSerializer extends MultiValueSessionSerializer<SomeJavaComplexObject> {

    public MyJavaMultiValueSessionSerializer(
        Function1<SomeJavaComplexObject, scala.collection.immutable.Map<String, String>> toMap,
        Function1<scala.collection.immutable.Map<String, String>, Try<SomeJavaComplexObject>> fromMap
    ) {
        super(toMap, fromMap);
    }

    public static void main(String[] args) {
        new MyJavaMultiValueSessionSerializer(
            jco -> {
                final java.util.Map<String, String> m = new java.util.HashMap<>();
                m.put("value", jco.getValue());
                return MapConverters.toImmutableMap(m);
            },
            value -> Try.apply(() -> new SomeJavaComplexObject(value.get("value").get()))
        );
    }

}
