// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.classanalysis;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

import java.io.File;

/**
 * @author Tony Vaagenes
 * @author ollivir
 */
public class TestUtilities {
    public static ClassFileMetaData analyzeClass(Class<?> clazz) {
        return Analyze.analyzeClass(classFile(name(clazz)));
    }

    public static File classFile(String className) {
        return new File("target/test-classes/" + className.replace('.', '/') + ".class");
    }

    public static String name(Class<?> clazz) {
        return clazz.getName();
    }

    public static TypeSafeMatcher<Throwable> throwableMessage(final Matcher<String> matcher) {
        return new TypeSafeMatcher<Throwable>() {
            @Override
            protected boolean matchesSafely(Throwable throwable) {
                return matcher.matches(throwable.getMessage());
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("expects Throwable and a message matching ").appendDescriptionOf(matcher);
            }
        };
    }
}
