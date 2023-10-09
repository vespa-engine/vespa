// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt.slobrok.api;

import org.junit.Test;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class MirrorTest {

    static void mustMatch(String name, String pattern) {
        assertTrue(Mirror.match(name.toCharArray(), pattern.toCharArray()));
    }

    static void mustNotMatch(String name, String pattern) {
        assertFalse(Mirror.match(name.toCharArray(), pattern.toCharArray()));
    }

    @Test public void requireThatPatternMatchesSameString() {
        String pattern = "foo/bar*zot/qux?foo**bar*/*nop*";
        mustMatch(pattern, pattern);
    }

    @Test public void requireThatStarIsPrefixMatch() {
        String pattern = "foo/bar.*/qux.*/bar*/nop*";
        String matches = "foo/bar.foo/qux.bar/bar123/nop000";
        mustMatch(matches, pattern);

        matches = "foo/bar.bar/qux.qux/bar.bar/nop.nop";
        mustMatch(matches, pattern);

        matches = "foo/bar.1/qux.3/bar.4/nop.5";
        mustMatch(matches, pattern);
   }

    @Test public void requireThatStarMatchesEmptyString() {
        String pattern = "foo/bar.*/qux.*/bar*/nop*";
        String matches = "foo/bar./qux./bar/nop";
        mustMatch(matches, pattern);
    }

    @Test public void requireThatExtraBeforeSlashIsNotMatch() {
        String pattern = "foo/*";
        String nomatch = "foo1/bar";
        mustNotMatch(nomatch, pattern);
    }

    @Test public void requireThatStarDoesNotMatchMultipleLevels() {
        String pattern = "foo/*/qux";
        String matches = "foo/bar/qux";
        String nomatch = "foo/bar/bar/qux";
        mustMatch(matches, pattern);
        mustNotMatch(nomatch, pattern);

        pattern = "*";
        nomatch = "foo/bar.foo/qux.bar/bar123/nop000";
        mustNotMatch(nomatch, pattern);
    }

    @Test public void requireThatDoubleStarMatchesMultipleLevels() {
        String pattern = "**";
        String matches = "foo/bar.foo/qux.bar/bar123/nop000";
        mustMatch(matches, pattern);

        pattern = "foo/**";
        matches = "foo/bar.foo/qux.bar/bar123/nop000";
        mustMatch(matches, pattern);

        pattern = "foo**";
        matches = "foo/bar.foo/qux.bar/bar123/nop000";
        mustMatch(matches, pattern);

        pattern = "f**";
        matches = "foo/bar.foo/qux.bar/bar123/nop000";
        mustMatch(matches, pattern);
    }

    @Test public void requireThatDoubleStarMatchesNothing() {
        String pattern = "A**";
        String matches = "A";
        mustMatch(matches, pattern);
    }

    @Test public void requireThatDoubleStarEatsRestOfName() {
        String pattern = "foo/**/suffix";
        String nomatch = "foo/bar/baz/suffix";
        mustNotMatch(nomatch, pattern);
    }

}
