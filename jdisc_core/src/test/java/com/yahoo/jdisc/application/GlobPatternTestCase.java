// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.application;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Simon Thoresen Hult
 */
public class GlobPatternTestCase {

    @Test
    public void requireThatCompileCreatesExpectedParts() {
        assertToString("foo");
        assertToString("*foo");
        assertToString("*oo");
        assertToString("f*o");
        assertToString("fo*");
        assertToString("foo*");
        assertToString("**foo");
        assertToString("**oo");
        assertToString("**o");
        assertToString("f**");
        assertToString("fo**");
        assertToString("foo**");
        assertToString("");
        assertToString("*");
    }

    @Test
    public void requireThatGlobMatcherWorks() {
        assertMatch("foo", "foo", Collections.<String>emptyList());
        assertNotMatch("foo", "bar");

        assertMatch("*", "foo", Arrays.asList("foo"));
        assertMatch("*", "bar", Arrays.asList("bar"));

        assertMatch("*foo", "foo", Arrays.asList(""));
        assertMatch("*oo", "foo", Arrays.asList("f"));
        assertMatch("f*o", "foo", Arrays.asList("o"));
        assertMatch("fo*", "foo", Arrays.asList("o"));
        assertMatch("foo*", "foo", Arrays.asList(""));

        assertNotMatch("*foo", "bar");
        assertNotMatch("*oo", "bar");
        assertNotMatch("f*o", "bar");
        assertNotMatch("fo*", "bar");
        assertNotMatch("foo*", "bar");

        assertMatch("**foo", "foo", Arrays.asList("", ""));
        assertMatch("**oo", "foo", Arrays.asList("", "f"));
        assertMatch("f**o", "foo", Arrays.asList("", "o"));
        assertMatch("fo**", "foo", Arrays.asList("", "o"));
        assertMatch("foo**", "foo", Arrays.asList("", ""));

        assertNotMatch("**foo", "bar");
        assertNotMatch("**oo", "bar");
        assertNotMatch("f**o", "bar");
        assertNotMatch("fo**", "bar");
        assertNotMatch("foo**", "bar");

        assertMatch("foo bar", "foo bar", Collections.<String>emptyList());
        assertMatch("*foo *bar", "foo bar", Arrays.asList("", ""));
        assertMatch("foo* bar*", "foo bar", Arrays.asList("", ""));
        assertMatch("f* *r", "foo bar", Arrays.asList("oo", "ba"));

        assertNotMatch("foo bar", "baz cox");
        assertNotMatch("*foo *bar", "baz cox");
        assertNotMatch("foo* bar*", "baz cox");
        assertNotMatch("f* *r", "baz cox");
    }

    @Test
    public void requireThatGlobPatternOrdersMoreSpecificFirst() {
        assertCompareEq("foo", "foo");
        assertCompareLt("foo", "foo*");
        assertCompareLt("foo", "*foo");

        assertCompareEq("foo/bar", "foo/bar");
        assertCompareLt("foo/bar", "foo");
        assertCompareLt("foo/bar", "foo*");
        assertCompareLt("foo/bar", "*foo");

        assertCompareLt("foo/bar", "foo*bar");
        assertCompareLt("foo/bar", "foo*bar*");
        assertCompareLt("foo/bar", "*foo*bar");

        assertCompareLt("foo*bar", "foo");
        assertCompareLt("foo*bar", "foo*");
        assertCompareLt("foo*bar", "*foo");

        assertCompareLt("foo", "foo*bar*");
        assertCompareLt("foo*bar*", "foo*");
        assertCompareLt("*foo", "foo*bar*");

        assertCompareLt("foo", "*foo*bar");
        assertCompareLt("*foo*bar", "foo*");
        assertCompareLt("*foo*bar", "*foo");

        assertCompareLt("*/3/2", "*/1/2");
        assertCompareLt("*/1/2", "*/2/*");
    }

    @Test
    public void requireThatEqualsIsImplemented() {
        assertTrue(GlobPattern.compile("foo").equals(GlobPattern.compile("foo")));
        assertFalse(GlobPattern.compile("foo").equals(GlobPattern.compile("bar")));
    }

    @Test
    public void requireThatHashCodeIsImplemented() {
        assertTrue(GlobPattern.compile("foo").hashCode() == GlobPattern.compile("foo").hashCode());
        assertFalse(GlobPattern.compile("foo").hashCode() == GlobPattern.compile("bar").hashCode());
    }

    private static void assertCompareLt(String lhs, String rhs) {
        assertTrue(compare(lhs, rhs) < 0);
        assertTrue(compare(rhs, lhs) > 0);
    }

    private static void assertCompareEq(String lhs, String rhs) {
        assertEquals(0, compare(lhs, rhs));
        assertEquals(0, compare(rhs, lhs));
    }

    private static int compare(String lhs, String rhs) {
        return GlobPattern.compile(lhs).compareTo(GlobPattern.compile(rhs));
    }

    private static void assertMatch(String glob, String str, List<String> expected) {
        GlobPattern.Match match = GlobPattern.match(glob, str);
        assertNotNull(match);
        List<String> actual = new ArrayList<>(match.groupCount());
        for (int i = 0, len = match.groupCount(); i < len; ++i) {
            actual.add(match.group(i));
        }
        assertEquals(expected, actual);
    }

    private static void assertNotMatch(String glob, String str) {
        assertNull(GlobPattern.match(glob, str));
    }

    private static void assertToString(String pattern) {
        assertEquals(pattern, GlobPattern.compile(pattern).toString());
    }
}
