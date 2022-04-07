// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text;

import org.junit.Ignore;
import org.junit.Test;

import java.util.OptionalInt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TextTestCase {

    private static void validateText(OptionalInt expect, String text) {
        assertEquals(expect, Text.validateTextString(text));
        assertEquals(expect.isEmpty(), Text.isValidTextString(text));
    }
    @Test
    public void testValidateTextString() {
        validateText(OptionalInt.empty(), "valid");
        validateText(OptionalInt.of(1), "text\u0001text\u0003");
        validateText(OptionalInt.of(0xDFFFF), new StringBuilder().appendCodePoint(0xDFFFF).toString());
        validateText(OptionalInt.of(0xDFFFF), new StringBuilder("foo").appendCodePoint(0xDFFFF).toString());
        validateText(OptionalInt.of(0xDFFFF), new StringBuilder().appendCodePoint(0xDFFFF).append("foo").toString());
        validateText(OptionalInt.of(0xDFFFF), new StringBuilder("foo").appendCodePoint(0xDFFFF).append("foo").toString());
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
        validateText(OptionalInt.of(0xD800), new StringBuilder().appendCodePoint(0xD800).toString());
        validateText(OptionalInt.of(0xD800), new StringBuilder().appendCodePoint(0xD800).append(0x0000).toString());
        validateText(OptionalInt.empty(), new StringBuilder().appendCodePoint(0xD800).appendCodePoint(0xDC00).toString());
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

    @Test
    public void testFormat() {
	assertEquals("foo 3.14", Text.format("%s %.2f", "foo", 3.1415926536));
    }

    private static long benchmarkIsValid(String [] strings, int num) {
        long sum = 0;
        for (int i=0; i < num; i++) {
            if (Text.isValidTextString(strings[i%strings.length])) {
                sum++;
            }
        }
        return sum;
    }

    private static long benchmarkValidate(String [] strings, int num) {
        long sum = 0;
        for (int i=0; i < num; i++) {
            if (Text.validateTextString(strings[i%strings.length]).isEmpty()) {
                sum++;
            }
        }
        return sum;
    }

    @Ignore
    @Test
    public void benchmarkTextValidation() {
        String [] strings = new String[100];
        for (int i=0; i < strings.length; i++) {
            strings[i] = new StringBuilder("some text ").append(i).append("of mine.").appendCodePoint(0xDFFFC).append("foo").toString();
        }
        long sum = benchmarkValidate(strings, 1000000);
        System.out.println("Warmup num validate = " + sum);
        sum = benchmarkIsValid(strings, 1000000);
        System.out.println("Warmup num isValid = " + sum);

        long start = System.nanoTime();
        sum = benchmarkValidate(strings, 100000000);
        long diff = System.nanoTime() - start;
        System.out.println("Validation num validate = " + sum + ". Took " + diff + "ns");

        start = System.nanoTime();
        sum = benchmarkIsValid(strings, 100000000);
        diff = System.nanoTime() - start;
        System.out.println("Validation num isValid = " + sum + ". Took " + diff + "ns");

    }
}
