// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.tls;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * @author bjorncs
 */
public class HostGlobPatternTest {

    @Test
    void glob_without_wildcards_matches_entire_string() {
        assertTrue(globMatches("foo", "foo"));
        assertFalse(globMatches("foo", "fooo"));
        assertFalse(globMatches("foo", "ffoo"));
    }

    @Test
    void wildcard_glob_can_match_prefix() {
        assertTrue(globMatches("foo*", "foo"));
        assertTrue(globMatches("foo*", "foobar"));
        assertFalse(globMatches("foo*", "ffoo"));
    }

    @Test
    void wildcard_glob_can_match_suffix() {
        assertTrue(globMatches("*foo", "foo"));
        assertTrue(globMatches("*foo", "ffoo"));
        assertFalse(globMatches("*foo", "fooo"));
    }

    @Test
    void wildcard_glob_can_match_substring() {
        assertTrue(globMatches("f*o", "fo"));
        assertTrue(globMatches("f*o", "foo"));
        assertTrue(globMatches("f*o", "ffoo"));
        assertFalse(globMatches("f*o", "boo"));
    }

    @Test
    void wildcard_glob_does_not_cross_multiple_dot_delimiter_boundaries() {
        assertTrue(globMatches("*.bar.baz", "foo.bar.baz"));
        assertTrue(globMatches("*.bar.baz", ".bar.baz"));
        assertFalse(globMatches("*.bar.baz", "zoid.foo.bar.baz"));
        assertTrue(globMatches("foo.*.baz", "foo.bar.baz"));
        assertFalse(globMatches("foo.*.baz", "foo.bar.zoid.baz"));
    }

    @Test
    void single_char_glob_matches_non_dot_characters() {
        assertTrue(globMatches("f?o", "foo"));
        assertFalse(globMatches("f?o", "fooo"));
        assertFalse(globMatches("f?o", "ffoo"));
        assertFalse(globMatches("f?o", "f.o"));
    }

    @Test
    void other_regex_meta_characters_are_matched_as_literal_characters() {
        assertTrue(globMatches("<([{\\^-=$!|]})+.>", "<([{\\^-=$!|]})+.>"));
    }

    private static boolean globMatches(String pattern, String value) {
        return new HostGlobPattern(pattern).matches(value);
    }
}
