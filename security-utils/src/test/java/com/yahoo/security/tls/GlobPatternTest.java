// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.tls;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author bjorncs
 */
class GlobPatternTest {

    @Test
    public void glob_without_wildcards_matches_entire_string() {
        assertMatches("foo", ".", "foo");
        assertNotMatches("foo", ".", "fooo");
        assertNotMatches("foo", ".", "ffoo");
        assertPatternHasRegex("foo", ".", "^\\Qfoo\\E$");
    }

    @Test
    public void wildcard_glob_can_match_prefix() {
        assertMatches("foo*", ".", "foo");
        assertMatches("foo*", ".", "foobar");
        assertNotMatches("foo*", ".", "ffoo");
    }

    @Test
    public void wildcard_glob_can_match_suffix() {
        assertMatches("*foo", ".", "foo");
        assertMatches("*foo", ".", "ffoo");
        assertNotMatches("*foo", ".", "fooo");
    }

    @Test
    public void wildcard_glob_can_match_substring() {
        assertMatches("f*o", ".", "fo");
        assertMatches("f*o", ".", "foo");
        assertMatches("f*o", ".", "ffoo");
        assertNotMatches("f*o", ".", "boo");
    }

    @Test
    public void wildcard_glob_does_not_cross_multiple_dot_delimiter_boundaries() {
        assertMatches("*.bar.baz", ".", "foo.bar.baz");
        assertMatches("*.bar.baz", ".", ".bar.baz");
        assertNotMatches("*.bar.baz", ".", "zoid.foo.bar.baz");
        assertMatches("foo.*.baz", ".", "foo.bar.baz");
        assertNotMatches("foo.*.baz", ".", "foo.bar.zoid.baz");

        assertPatternHasRegex("*.bar.baz", ".", "^[^\\Q.\\E]*\\Q.bar.baz\\E$");
    }

    @Test
    public void single_char_glob_matches_non_dot_characters() {
        assertMatches("f?o", ".", "foo");
        assertNotMatches("f?o", ".", "fooo");
        assertNotMatches("f?o", ".", "ffoo");
        assertNotMatches("f?o", ".", "f.o");
    }

    @Test
    public void other_regex_meta_characters_are_matched_as_literal_characters() {
        String literals = "<([{\\^-=$!|]})+.>";
        assertMatches(literals, ".", literals);
        assertPatternHasRegex(literals, ".", "^\\Q<([{\\^-=$!|]})+.>\\E$");
    }

    @Test
    public void handles_patterns_with_multiple_alternative_boundaries() {
        assertMatches("https://*.vespa.ai/", "./", "https://docs.vespa.ai/");
        assertMatches("https://vespa.ai/*.world", "./", "https://vespa.ai/hello.world");
        assertNotMatches("https://vespa.ai/*/", "./", "https://vespa.ai/hello.world/");
        assertMatches("https://vespa.ai/*/index.html", "./", "https://vespa.ai/path/index.html");
    }

    private void assertMatches(String pattern, String boundaries, String value) {
        GlobPattern p = globPattern(pattern, boundaries);
        assertTrue(
                p.matches(value),
                () -> String.format("Expected '%s' with boundaries '%s' to match '%s'",
                        pattern, Arrays.toString(p.boundaries()), value));
    }

    private void assertNotMatches(String pattern, String boundaries, String value) {
        GlobPattern p = globPattern(pattern, boundaries);
        assertFalse(
                p.matches(value),
                () -> String.format("Expected '%s' with boundaries '%s' to not match '%s'",
                        pattern, Arrays.toString(p.boundaries()), value));
    }

    private void assertPatternHasRegex(String pattern, String boundaries, String expectedPattern) {
        GlobPattern p = globPattern(pattern, boundaries);
        assertEquals(expectedPattern, p.regexPattern().pattern());
    }

    private static GlobPattern globPattern(String pattern, String boundaries) {
        return new GlobPattern(pattern, boundaries.toCharArray(), true);
    }

}
