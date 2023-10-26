// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Simon Thoresen Hult
 */
public class AsciiTest {

    @Test
    public void requireThatAdditionalCodePointsCanBeEscaped() {
        assertEquals("\\x66\\x6F\\x6F \\x62ar \\x62az",
                     Ascii.newEncoder(StandardCharsets.UTF_8, 'f', 'o', 'b').encode("foo bar baz"));
    }

    @Test
    public void requireThatReadableCharactersAreNotEscaped() {
        StringBuilder str = new StringBuilder();
        for (int i = 0x20; i < 0x7F; ++i) {
            if (i != '\\') {
                str.appendCodePoint(i);
            }
        }
        assertEncodeUtf8(str.toString(), str.toString());
    }

    @Test
    public void requireThatNonReadableCharactersAreEscapedAsUtf8() {
        for (int i = Character.MIN_CODE_POINT; i < 0x20; ++i) {
            String expected;
            switch (i) {
            case '\f':
                expected = "\\f";
                break;
            case '\n':
                expected = "\\n";
                break;
            case '\r':
                expected = "\\r";
                break;
            case '\t':
                expected = "\\t";
                break;
            default:
                expected = String.format("\\x%02X", i);
                break;
            }
            assertEncodeUtf8(expected, new StringBuilder().appendCodePoint(i).toString());
        }
        for (int i = 0x80; i < 0xC0; ++i) {
            String expected = String.format("\\xC2\\x%02X", i);
            assertEncodeUtf8(expected, new StringBuilder().appendCodePoint(i).toString());
        }
        for (int i = 0xC0; i < 0x0100; ++i) {
            String expected = String.format("\\xC3\\x%02X", i - 0x40);
            assertEncodeUtf8(expected, new StringBuilder().appendCodePoint(i).toString());
        }
        for (int i = 0x0100; i < 0x0140; ++i) {
            String expected = String.format("\\xC4\\x%02X", i - 0x80);
            assertEncodeUtf8(expected, new StringBuilder().appendCodePoint(i).toString());
        }
    }

    @Test
    public void requireThatBackslashIsEscaped() {
        assertEncodeUtf8("\\\\", "\\");
    }

    @Test
    public void requireThatQuoteIsEscaped() {
        assertEncodeUtf8("\\x62az", "baz", 'b');
        assertEncodeUtf8("b\\x61z", "baz", 'a');
        assertEncodeUtf8("ba\\x7A", "baz", 'z');
    }

    @Test
    public void requireThatAnyEscapedCharacterCanBeUnescaped() {
        assertDecodeUtf8("baz", "\\baz");
        assertDecodeUtf8("baz", "b\\az");
        assertDecodeUtf8("baz", "ba\\z");
    }

    @Test
    public void requireThatUtf8SequencesAreUnescaped() {
        for (int i = 0x80; i < 0xC0; ++i) {
            String str = String.format("\\xC2\\x%02X", i);
            assertDecodeUtf8(new StringBuilder().appendCodePoint(i).toString(), str);
        }
        for (int i = 0xC0; i < 0x0100; ++i) {
            String str = String.format("\\xC3\\x%02X", i - 0x40);
            assertDecodeUtf8(new StringBuilder().appendCodePoint(i).toString(), str);
        }
        for (int i = 0x0100; i < 0x0140; ++i) {
            String str = String.format("\\xC4\\x%02X", i - 0x80);
            assertDecodeUtf8(new StringBuilder().appendCodePoint(i).toString(), str);
        }
    }

    @Test
    public void requireThatUtf8CanBeEncoded() {
        // First possible sequence of a certain length
        assertEncodeUtf8("\\x00", "\u0000");
        assertEncodeUtf8("\\xC2\\x80", "\u0080");
        assertEncodeUtf8("\\xE0\\xA0\\x80", "\u0800");
        assertEncodeUtf8("\\x01\\x00", "\u0001\u0000");
        assertEncodeUtf8("\\x20\\x00", "\u0020\u0000", ' ');
        assertEncodeUtf8("\\xD0\\x80\\x00", "\u0400\u0000");

        // Last possible sequence of a certain length
        assertEncodeUtf8("\\x7F", "\u007F");
        assertEncodeUtf8("\\xDF\\xBF", "\u07FF");
        assertEncodeUtf8("\\xEF\\xBF\\xBF", "\uFFFF");
        assertEncodeUtf8("\\x1F\\xEF\\xBF\\xBF", "\u001F\uFFFF");
        assertEncodeUtf8("\\xCF\\xBF\\xEF\\xBF\\xBF", "\u03FF\uFFFF");
        assertEncodeUtf8("\\xE7\\xBF\\xBF\\xEF\\xBF\\xBF", "\u7FFF\uFFFF");

        // Other boundary conditions
        assertEncodeUtf8("\\xED\\x9F\\xBF", "\uD7FF");
        assertEncodeUtf8("\\xEE\\x80\\x80", "\uE000");
        assertEncodeUtf8("\\xEF\\xBF\\xBD", "\uFFFD");
        assertEncodeUtf8("\\x10\\xEF\\xBF\\xBF", "\u0010\uFFFF");
        assertEncodeUtf8("\\x11\\x00", "\u0011\u0000");
    }

    @Test
    public void requireThatUTf8CanBeDecoded() {
        // First possible sequence of a certain length
        assertDecodeUtf8("\u0000", "\\x00");
        assertDecodeUtf8("\u0080", "\\xC2\\x80");
        assertDecodeUtf8("\u0800", "\\xE0\\xA0\\x80");
        assertDecodeUtf8("\u0001\u0000", "\\x01\\x00");
        assertDecodeUtf8("\u0020\u0000", "\\x20\\x00");
        assertDecodeUtf8("\u0400\u0000", "\\xD0\\x80\\x00");

        // Last possible sequence of a certain length
        assertDecodeUtf8("\u007F", "\\x7F");
        assertDecodeUtf8("\u07FF", "\\xDF\\xBF");
        assertDecodeUtf8("\uFFFF", "\\xEF\\xBF\\xBF");
        assertDecodeUtf8("\u001F\uFFFF", "\\x1F\\xEF\\xBF\\xBF");
        assertDecodeUtf8("\u03FF\uFFFF", "\\xCF\\xBF\\xEF\\xBF\\xBF");
        assertDecodeUtf8("\u7FFF\uFFFF", "\\xE7\\xBF\\xBF\\xEF\\xBF\\xBF");

        // Other boundary conditions
        assertDecodeUtf8("\uD7FF", "\\xED\\x9F\\xBF");
        assertDecodeUtf8("\uE000", "\\xEE\\x80\\x80");
        assertDecodeUtf8("\uFFFD", "\\xEF\\xBF\\xBD");
        assertDecodeUtf8("\u0010\uFFFF", "\\x10\\xEF\\xBF\\xBF");
        assertDecodeUtf8("\u0011\u0000", "\\x11\\x00");
    }

    @Test
    public void requireThatUnicodeCanBeEncoded() {
        assertEncodeUtf8("\\xE4\\xB8\\x9C\\xE8\\xA5\\xBF\\xE8\\x87\\xAA\\xE8\\xA1\\x8C\\xE8\\xBD\\xA6",
                         "\u4E1C\u897F\u81EA\u884C\u8F66");
    }

    @Test
    public void requireThatUnicodeCanBeDecoded() {
        assertDecodeUtf8("\u4E1C\u897F\u81EA\u884C\u8F66",
                         "\\xE4\\xB8\\x9C\\xE8\\xA5\\xBF\\xE8\\x87\\xAA\\xE8\\xA1\\x8C\\xE8\\xBD\\xA6");
    }

    @Test
    public void requireThatUnicodeIsAllowedInInputString() {
        assertDecodeUtf8("\u4E1C\u897F\u81EA\u884C\u8F66",
                         "\u4E1C\u897F\u81EA\u884C\u8F66");
    }

    private static void assertEncodeUtf8(String expected, String str, int... requiresEscape) {
        String actual = Ascii.encode(str, StandardCharsets.UTF_8, requiresEscape);
        for (int i = 0; i < actual.length(); i += actual.offsetByCodePoints(i, 1)) {
            int c = actual.codePointAt(i);
            assertTrue(Integer.toHexString(c), c >= 0x20 && c <= 0x7F);
        }
        assertEquals(expected, actual);
    }

    private static void assertDecodeUtf8(String expected, String str) {
        assertEquals(expected, Ascii.decode(str, StandardCharsets.UTF_8));
    }
}
