// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.net;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Simon Thoresen Hult
 */
public class UrlTokenTestCase {

    @Test
    public void requireThatAccessorsWork() {
        UrlToken token = new UrlToken(UrlToken.Type.FRAGMENT, 69, "foo", "bar");
        assertEquals(UrlToken.Type.FRAGMENT, token.getType());
        assertEquals(69, token.getOffset());
        assertEquals(3, token.getLength());
        assertEquals("foo", token.getOrig());
        assertEquals("bar", token.getTerm());
    }

    @Test
    public void requireThatTypeCanNotBeNull() {
        try {
            new UrlToken(null, 0, "foo", "bar");
            fail();
        } catch (NullPointerException e) {

        }
    }

    @Test
    public void requireThatOrigAndTermCanBeNull() {
        UrlToken token = new UrlToken(UrlToken.Type.SCHEME, 0, null, "foo");
        assertNull(token.getOrig());
        assertEquals("foo", token.getTerm());

        token = new UrlToken(UrlToken.Type.SCHEME, 0, "foo", null);
        assertEquals("foo", token.getOrig());
        assertNull(token.getTerm());

        token = new UrlToken(UrlToken.Type.SCHEME, 0, null, null);
        assertNull(token.getOrig());
        assertNull(token.getTerm());
    }
}
