// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.tls.policy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author bjorncs
 */
class UriGlobPatternTest {

    @Test
    void matches_correctly() {
        assertMatches("scheme://hostname/*", "scheme://hostname/mypath");
        assertMatches("scheme://hostname/*/segment2", "scheme://hostname/segment1/segment2");
        assertMatches("scheme://hostname/segment1/*", "scheme://hostname/segment1/segment2");
        assertNotMatches("scheme://hostname/*", "scheme://hostname/segment1/segment2");
        assertMatches("scheme://*/segment1/segment2", "scheme://hostname/segment1/segment2");
        assertMatches("scheme://*.name/", "scheme://host.name/");
        assertNotMatches("scheme://*", "scheme://hostname/");
    }

    private void assertMatches(String pattern, String value) {
        assertTrue(new UriGlobPattern(pattern).matches(value),
                () -> String.format("Expected '%s' to match '%s'", pattern, value));
    }

    private void assertNotMatches(String pattern, String value) {
        assertFalse(new UriGlobPattern(pattern).matches(value),
                () -> String.format("Expected '%s' to not match '%s'", pattern, value));
    }

}
