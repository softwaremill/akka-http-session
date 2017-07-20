package com.softwaremill.example.session;

import com.softwaremill.session.SessionSerializer;
import scala.util.Try;

public class MyJavaSingleSessionSerializer implements SessionSerializer<SomeJavaComplexObject, String> {

    @Override
    public String serialize(SomeJavaComplexObject jco) {
        return jco.getValue();
    }

    @Override
    public Try<SomeJavaComplexObject> deserialize(String input) {
        return Try.apply(() -> new SomeJavaComplexObject(input));
    }

}
