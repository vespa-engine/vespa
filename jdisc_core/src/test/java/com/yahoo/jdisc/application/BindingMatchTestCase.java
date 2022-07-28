// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.application;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;


/**
 * @author Simon Thoresen Hult
 */
public class BindingMatchTestCase {

    @Test
    void requireThatAccessorsWork() {
        Object obj = new Object();
        UriPattern pattern = new UriPattern("http://*/*");
        BindingMatch<Object> match = new BindingMatch<>(
                pattern.match(URI.create("http://localhost:69/status.html")),
                obj, pattern);
        assertSame(obj, match.target());
        assertEquals(3, match.groupCount());
        assertEquals("localhost", match.group(0));
        assertEquals("69", match.group(1));
        assertEquals("status.html", match.group(2));
        assertEquals(pattern, match.matched());
    }

    @Test
    void requireThatConstructorArgumentsCanNotBeNull() {
        try {
            new BindingMatch<>(null, null, null);
            fail();
        } catch (NullPointerException e) {

        }
        try {
            UriPattern pattern = new UriPattern("http://*/*");
            new BindingMatch<>(pattern.match(URI.create("http://localhost/")), null, pattern);
            fail();
        } catch (NullPointerException e) {

        }
        try {
            UriPattern pattern = new UriPattern("http://*/*");
            new BindingMatch<>(null, new Object(), pattern);
            fail();
        } catch (NullPointerException e) {

        }
    }
}
