// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.test;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;

/**
 * Checks if a multi-line string contains at least one line with the expected regex pattern.
 *
 * @author gjoranv
 * @since 5.1.7
 */
public class LinePatternMatcher extends BaseMatcher<String> {

    private final String pattern;

    public LinePatternMatcher(String pattern) {
        this.pattern = pattern;
    }

    @Override
    public void describeTo(Description description) {
        description.appendText("contains a line that matches expression '" + pattern + "'");
    }

    @Override
    public boolean matches(Object o) {
        String s = (String)o;
        String[] lines = s.split("\n");
        for (String line : lines)
            if (line.matches(pattern))
                return true;
        return false;
    }

    public static Matcher<String> containsLineWithPattern(String pattern) {
        return new LinePatternMatcher(pattern);
    }

}
