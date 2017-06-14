// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.test;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Factory;
import org.hamcrest.Matcher;

/**
 * Matches a string against an expected regex pattern.
 *
 * @author gjoranv
 * @since 5.1.7
 */
public class PatternMatcher extends BaseMatcher<String> {

    private final String pattern;

    public PatternMatcher(String pattern) {
        this.pattern = pattern;
    }

    @Override
    public void describeTo(Description description) {
        description.appendText("matches expression '" + pattern + "'");
    }

    @Override
    public boolean matches(Object o) {
        return ((String)o).matches(pattern);
    }

    @Factory
    public static <T> Matcher<String> matchesPattern(String pattern) {
        return new PatternMatcher(pattern);
    }

}
