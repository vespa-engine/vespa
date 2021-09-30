// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.application;

import org.junit.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Simon Thoresen Hult
 */
public class UriPatternTestCase {

    private static final List<String> NO_GROUPS = Collections.emptyList();

    @Test
    public void requireThatIllegalPatternsAreDetected() {
        assertIllegalPattern("scheme");
        assertIllegalPattern("scheme://");
        assertIllegalPattern("scheme://host");
        assertIllegalPattern("scheme://host:0");
        assertIllegalPattern("scheme://host:69");
        assertIllegalPattern("scheme://host:-69");
        assertIllegalPattern("scheme://host:6*/");
        assertIllegalPattern("scheme://host:6*9/");
        assertIllegalPattern("scheme://host:*9/");
    }

    @Test
    public void requireThatNoPortImpliesWildcard() {
        assertEquals(new UriPattern("scheme://host/path"),
                     new UriPattern("scheme://host:*/path"));
    }

    @Test
    public void requireThatPatternMatches() {
        // scheme matching
        UriPattern pattern = new UriPattern("bar://host:69/path");
        assertNotMatch(pattern, "foobar://host:69/path");
        assertMatch(pattern, "bar://host:69/path", NO_GROUPS);
        assertNotMatch(pattern, "barbaz://host:69/path");

        pattern = new UriPattern("*://host:69/path");
        assertMatch(pattern, "foobar://host:69/path", Arrays.asList("foobar"));
        assertMatch(pattern, "bar://host:69/path", Arrays.asList("bar"));
        assertMatch(pattern, "barbaz://host:69/path", Arrays.asList("barbaz"));

        pattern = new UriPattern("*bar://host:69/path");
        assertMatch(pattern, "foobar://host:69/path", Arrays.asList("foo"));
        assertMatch(pattern, "bar://host:69/path", Arrays.asList(""));
        assertNotMatch(pattern, "barbaz://host:69/path");

        pattern = new UriPattern("bar*://host:69/path");
        assertNotMatch(pattern, "foobar://host:69/path");
        assertMatch(pattern, "bar://host:69/path", Arrays.asList(""));
        assertMatch(pattern, "barbaz://host:69/path", Arrays.asList("baz"));

        // host matching
        pattern = new UriPattern("scheme://bar:69/path");
        assertNotMatch(pattern, "scheme://foobar:69/path");
        assertMatch(pattern, "scheme://bar:69/path", NO_GROUPS);
        assertNotMatch(pattern, "scheme://barbaz:69/path");

        pattern = new UriPattern("scheme://*:69/path");
        assertMatch(pattern, "scheme://foobar:69/path", Arrays.asList("foobar"));
        assertMatch(pattern, "scheme://bar:69/path", Arrays.asList("bar"));
        assertMatch(pattern, "scheme://barbaz:69/path", Arrays.asList("barbaz"));

        pattern = new UriPattern("scheme://*bar:69/path");
        assertMatch(pattern, "scheme://foobar:69/path", Arrays.asList("foo"));
        assertMatch(pattern, "scheme://bar:69/path", Arrays.asList(""));
        assertNotMatch(pattern, "scheme://barbaz:69/path");

        pattern = new UriPattern("scheme://bar*:69/path");
        assertNotMatch(pattern, "scheme://foobar:69/path");
        assertMatch(pattern, "scheme://bar:69/path", Arrays.asList(""));
        assertMatch(pattern, "scheme://barbaz:69/path", Arrays.asList("baz"));

        // port matching
        pattern = new UriPattern("scheme://host:69/path");
        assertNotMatch(pattern, "scheme://host:669/path");
        assertMatch(pattern, "scheme://host:69/path", NO_GROUPS);
        assertNotMatch(pattern, "scheme://host:699/path");

        pattern = new UriPattern("scheme://host:*/path");
        assertMatch(pattern, "scheme://host:669/path", Arrays.asList("669"));
        assertMatch(pattern, "scheme://host:69/path", Arrays.asList("69"));
        assertMatch(pattern, "scheme://host:699/path", Arrays.asList("699"));

        // path matching
        pattern = new UriPattern("scheme://host:69/");
        assertMatch(pattern, "scheme://host:69/", NO_GROUPS);
        assertNotMatch(pattern, "scheme://host:69/foo");

        pattern = new UriPattern("scheme://host:69/bar");
        assertNotMatch(pattern, "scheme://host:69/foobar");
        assertMatch(pattern, "scheme://host:69/bar", NO_GROUPS);
        assertNotMatch(pattern, "scheme://host:69/barbaz");

        pattern = new UriPattern("scheme://host:69/*");
        assertMatch(pattern, "scheme://host:69/", Arrays.asList(""));
        assertMatch(pattern, "scheme://host:69/foobar", Arrays.asList("foobar"));
        assertMatch(pattern, "scheme://host:69/bar", Arrays.asList("bar"));
        assertMatch(pattern, "scheme://host:69/barbaz", Arrays.asList("barbaz"));

        pattern = new UriPattern("scheme://host:69/*bar");
        assertMatch(pattern, "scheme://host:69/foobar", Arrays.asList("foo"));
        assertMatch(pattern, "scheme://host:69/bar", Arrays.asList(""));
        assertNotMatch(pattern, "scheme://host:69/barbaz");

        pattern = new UriPattern("scheme://host:69/bar*");
        assertNotMatch(pattern, "scheme://host:69/foobar");
        assertMatch(pattern, "scheme://host:69/bar", Arrays.asList(""));
        assertMatch(pattern, "scheme://host:69/barbaz", Arrays.asList("baz"));
    }

    @Test
    public void requireThatUriWithoutPathDoesNotThrowException() {
        UriPattern pattern = new UriPattern("scheme://host/path");
        assertNotMatch(pattern, "scheme://host");

        pattern = new UriPattern("scheme://host/*");
        assertMatch(pattern, "scheme://host", Arrays.asList(""));
    }

    @Test
    public void requireThatOnlySchemeHostPortAndPathIsMatched() {
        UriPattern pattern = new UriPattern("scheme://host:69/path");
        assertMatch(pattern, "scheme://host:69/path?foo", NO_GROUPS);
        assertMatch(pattern, "scheme://host:69/path?foo#bar", NO_GROUPS);
    }

    @Test
    public void requireThatHostSupportsWildcard() {
        UriPattern pattern = new UriPattern("scheme://*.host/path");
        assertMatch(pattern, "scheme://a.host/path", Arrays.asList("a"));
        assertMatch(pattern, "scheme://a.b.host/path", Arrays.asList("a.b"));
    }

    @Test
    @SuppressWarnings("removal")
    public void requireThatPrioritiesAreOrderedDescending() {
        assertCompareLt(new UriPattern("scheme://host:69/path", 1),
                        new UriPattern("scheme://host:69/path", 0));
    }

    @Test
    @SuppressWarnings("removal")
    public void requireThatPriorityOrdersBeforeScheme() {
        assertCompareLt(new UriPattern("*://host:69/path", 1),
                        new UriPattern("scheme://host:69/path", 0));
    }

    @Test
    public void requireThatSchemesAreOrdered() {
        assertCompareLt("b://host:69/path",
                        "a://host:69/path");
    }

    @Test
    public void requireThatSchemeOrdersBeforeHost() {
        assertCompareLt("b://*:69/path",
                        "a://host:69/path");
    }

    @Test
    public void requireThatHostsAreOrdered() {
        assertCompareLt("scheme://b:69/path",
                        "scheme://a:69/path");
    }

    @Test
    public void requireThatHostOrdersBeforePath() {
        assertCompareLt("scheme://b:69/*",
                        "scheme://a:69/path");
    }

    @Test
    public void requireThatPortsAreOrdered() {
        for (int i = 1; i < 69; ++i) {
            assertCompareEq("scheme://host:" + i + "/path",
                            "scheme://host:" + i + "/path");
            assertCompareLt("scheme://host:" + (i + 1) + "/path",
                            "scheme://host:" + i + "/path");
            assertCompareLt("scheme://host:" + i + "/path",
                            "scheme://host:*/path");
        }
    }

    @Test
    public void requireThatPathsAreOrdered() {
        assertCompareLt("scheme://host:69/b",
                        "scheme://host:69/a");
    }

    @Test
    public void requireThatPathOrdersBeforePort() {
        assertCompareLt("scheme://host:*/b",
                        "scheme://host:69/a");
    }

    @Test
    public void requireThatEqualPatternsOrderEqual() {
        assertCompareEq("scheme://host:69/path",
                        "scheme://host:69/path");
        assertCompareEq("*://host:69/path",
                        "*://host:69/path");
        assertCompareEq("scheme://*:69/path",
                        "scheme://*:69/path");
        assertCompareEq("scheme://host:*/path",
                        "scheme://host:*/path");
        assertCompareEq("scheme://host:69/*",
                        "scheme://host:69/*");
    }

    @Test
    public void requireThatStrictPatternsOrderBeforeWildcards() {
        assertCompareLt("scheme://host:69/path",
                        "*://host:69/path");
        assertCompareLt("scheme://a:69/path",
                        "scheme://*:69/path");
        assertCompareLt("scheme://a:69/path",
                        "scheme://*a:69/path");
        assertCompareLt("scheme://*aa:69/path",
                        "scheme://*a:69/path");
        assertCompareLt("scheme://host:69/path",
                        "scheme://host:*/path");
        assertCompareLt("scheme://host:69/a",
                        "scheme://host:69/*");
        assertCompareLt("scheme://host:69/a",
                        "scheme://host:69/a*");
        assertCompareLt("scheme://host:69/aa*",
                        "scheme://host:69/a*");
        assertCompareLt("scheme://*:69/path",
                        "*://host:69/path");
        assertCompareLt("scheme://host:*/path",
                        "scheme://*:69/path");
        assertCompareLt("scheme://host:*/path",
                        "scheme://host:69/*");
        assertCompareLt("scheme://host:69/foo",
                        "scheme://host:69/*");
        assertCompareLt("scheme://host:69/foo/bar",
                        "scheme://host:69/foo/*");
        assertCompareLt("scheme://host:69/foo/bar/baz",
                        "scheme://host:69/foo/bar/*");
    }

    @Test
    public void requireThatLongPatternsOrderBeforeShort() {
        assertCompareLt("scheme://host:69/foo/bar",
                        "scheme://host:69/foo");
        assertCompareLt("scheme://host:69/foo/bar/baz",
                        "scheme://host:69/foo/bar");
    }

    @Test
    public void requireThatHttpsSchemeIsHandledAsHttp() {
        UriPattern httpPattern = new UriPattern("http://host:80/path");
        assertMatch(httpPattern, "https://host:80/path", NO_GROUPS);

        UriPattern httpsPattern = new UriPattern("https://host:443/path");
        assertMatch(httpsPattern, "http://host:443/path", NO_GROUPS);
    }

    @Test
    public void requireThatUrlEncodingIsNotDoneForPath() {
        UriPattern encodedSlashPattern = new UriPattern("http://host:80/one%2Fpath");
        assertMatch(encodedSlashPattern, "http://host:80/one%2Fpath", NO_GROUPS);
        assertNotMatch(encodedSlashPattern, "http://host:80/one/path");

        UriPattern actualSlashPattern = new UriPattern("http://host:80/two/paths");
        assertNotMatch(actualSlashPattern, "http://host:80/two%2Fpaths");
        assertMatch(actualSlashPattern, "http://host:80/two/paths", NO_GROUPS);
    }

    private static void assertIllegalPattern(String uri) {
        try {
            new UriPattern(uri);
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    private static void assertCompareLt(String lhs, String rhs) {
        assertCompareLt(new UriPattern(lhs), new UriPattern(rhs));
    }

    private static void assertCompareLt(UriPattern lhs, UriPattern rhs) {
        assertEquals(-1, compare(lhs, rhs));
    }

    private static void assertCompareEq(String lhs, String rhs) {
        assertCompareEq(new UriPattern(lhs), new UriPattern(rhs));
    }

    private static void assertCompareEq(UriPattern lhs, UriPattern rhs) {
        assertEquals(0, compare(lhs, rhs));
    }

    private static int compare(UriPattern lhs, UriPattern rhs) {
        int lhsCmp = lhs.compareTo(rhs);
        int rhsCmp = rhs.compareTo(lhs);
        if (lhsCmp < 0) {
            assertTrue(rhsCmp > 0);
            return -1;
        }
        if (lhsCmp > 0) {
            assertTrue(rhsCmp < 0);
            return 1;
        }
        assertTrue(rhsCmp == 0);
        return 0;
    }

    private static void assertMatch(UriPattern pattern, String uri, List<String> expected) {
        UriPattern.Match match = pattern.match(URI.create(uri));
        assertNotNull(match);
        List<String> actual = new ArrayList<>(match.groupCount());
        for (int i = 0, len = match.groupCount(); i < len; ++i) {
            actual.add(match.group(i));
        }
        assertEquals(expected, actual);
    }

    private static void assertNotMatch(UriPattern pattern, String uri) {
        assertNull(pattern.match(URI.create(uri)));
    }
}
