// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text;

import org.junit.Test;

import java.util.OptionalInt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TextTestCase {

    @Test
    public void testValidateTextString() {
        assertFalse(Text.validateTextString("valid").isPresent());
        assertEquals(OptionalInt.of(1), Text.validateTextString("text\u0001text\u0003"));
        assertEquals(OptionalInt.of(0xDFFFF),
                     Text.validateTextString(new StringBuilder().appendCodePoint(0xDFFFF).toString()));
        assertEquals(OptionalInt.of(0xDFFFF),
                     Text.validateTextString(new StringBuilder("foo").appendCodePoint(0xDFFFF).toString()));
        assertEquals(OptionalInt.of(0xDFFFF),
                     Text.validateTextString(new StringBuilder().appendCodePoint(0xDFFFF).append("foo").toString()));
        assertEquals(OptionalInt.of(0xDFFFF),
                     Text.validateTextString(new StringBuilder("foo").appendCodePoint(0xDFFFF).append("foo").toString()));
    }

    @Test
    public void testStripTextString() {
        assertEquals("", Text.stripInvalidCharacters(""));
        assertEquals("valid", Text.stripInvalidCharacters("valid"));
        assertEquals("text text ", Text.stripInvalidCharacters("text\u0001text\u0003"));
        assertEquals(" ",
                     Text.stripInvalidCharacters(new StringBuilder().appendCodePoint(0xDFFFF).toString()));
        assertEquals("foo ",
                     Text.stripInvalidCharacters(new StringBuilder("foo").appendCodePoint(0xDFFFF).toString()));
        assertEquals(" foo",
                     Text.stripInvalidCharacters(new StringBuilder().appendCodePoint(0xDFFFF).append("foo").toString()));
        assertEquals("foo foo",
                     Text.stripInvalidCharacters(new StringBuilder("foo").appendCodePoint(0xDFFFF).append("foo").toString()));
        assertEquals("foo foo",
                Text.stripInvalidCharacters(new StringBuilder("foo").appendCodePoint(0xD800).append("foo").toString()));
    }

    @Test
    public void testThatHighSurrogateRequireLowSurrogate() {
        assertEquals(OptionalInt.of(0xD800), Text.validateTextString(new StringBuilder().appendCodePoint(0xD800).toString()));
        assertEquals(OptionalInt.of(0xD800), Text.validateTextString(new StringBuilder().appendCodePoint(0xD800).append(0x0000).toString()));
    }

    @Test
    public void testIsDisplayable() {
        assertTrue(Text.isDisplayable('A'));
        assertTrue(Text.isDisplayable('a'));
        assertTrue(Text.isDisplayable('5'));
        assertTrue(Text.isDisplayable(','));
        assertTrue(Text.isDisplayable('\"'));
        assertTrue(Text.isDisplayable('}'));
        assertTrue(Text.isDisplayable('-'));
        assertFalse(Text.isDisplayable(' '));
        assertFalse(Text.isDisplayable(0));
    }

    @Test
    public void testTruncate() {
        assertEquals("ab", Text.truncate("ab", 5));
        assertEquals("ab", Text.truncate("ab", 6));
        assertEquals("ab", Text.truncate("ab", 2));
        assertEquals("a",  Text.truncate("ab", 1));
        assertEquals("",   Text.truncate("ab", 0));
        assertEquals("ab c",  Text.truncate("ab cde", 4));
        assertEquals("a ...", Text.truncate("ab cde", 5));
    }

}
