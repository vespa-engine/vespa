// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.test;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Factory;
import org.hamcrest.Matcher;

import java.util.Collection;

/**
 * Checks if a collection of strings contains at least one string with the expected regex pattern.
 *
 * @author gjoranv
 * @since 5.1.8
 */
public class CollectionPatternMatcher extends BaseMatcher<Collection<String>> {

    private final String pattern;

    public CollectionPatternMatcher(String pattern) {
        this.pattern = pattern;
    }

    @Override
    public void describeTo(Description description) {
        description.appendText("contains a string that matches expression '" + pattern + "'");
    }

    @Override
    public boolean matches(Object o) {
        @SuppressWarnings("unchecked")
        Collection<String> strings = (Collection<String>) o;
        for (String s : strings)
            if (s.matches(pattern))
                return true;
        return false;
    }

    @Factory
    public static <T> Matcher<Collection<String>> containsStringWithPattern(String pattern) {
        return new CollectionPatternMatcher(pattern);
    }

}
