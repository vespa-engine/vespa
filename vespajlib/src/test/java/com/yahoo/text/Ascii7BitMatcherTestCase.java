// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text;

import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.*;

public class Ascii7BitMatcherTestCase {
    @Test
    public void testThatListedCharsAreLegal() {
        assertTrue(new Ascii7BitMatcher("a").matches("aaaa"));
        assertTrue(new Ascii7BitMatcher("ab").matches("abbbbbbbbb"));
        assertTrue(new Ascii7BitMatcher("ab").matches("bbbbbbbbbba"));
        assertTrue(new Ascii7BitMatcher("1").matches("1"));
    }
    @Test
    public void requireThatNotListedCharsFail() {
        assertFalse(new Ascii7BitMatcher("a").matches("b"));
    }

    @Test
    public void testThatLegalFirstAndRestPass() {
        assertTrue(new Ascii7BitMatcher("a", "").matches("a"));
        assertTrue(new Ascii7BitMatcher("a", "b").matches("abbbbbbbbb"));
        assertTrue(new Ascii7BitMatcher("abc", "0123").matches("a123120"));
    }
    @Test
    public void requireThatIllegalFirstOrSecondFail() {
        assertFalse(new Ascii7BitMatcher("a", "").matches("aa"));
        assertFalse(new Ascii7BitMatcher("a", "b").matches("aa"));
        assertFalse(new Ascii7BitMatcher("", "a").matches("a"));
        assertFalse(new Ascii7BitMatcher("a", "b").matches("bb"));
        assertFalse(new Ascii7BitMatcher("a", "b").matches("abbbbbx"));
    }
    @Test
    public void requireThatNonAsciiFailConstruction() {
        try {
            new Ascii7BitMatcher("aæb");
            Assert.fail("'æ' should not be allowed");
        } catch (IllegalArgumentException e) {
            assertEquals("Char 'æ' at position 1 is not valid ascii 7 bit char", e.getMessage());
        }
    }
}
