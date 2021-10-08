// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.core;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Functional tests for encoding Encoder, i.e. encoding scheme only producing
 * ASCII and never containing white space or control characters.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class EncoderTestCase {
    private final String basic = "abc123";
    private final String basic2 = "abc{20}123";
    private final String quotedIsLast = "abc{20}";
    private final String quotedIsLastDecoded = "abc ";
    private final String basic2Decoded = "abc 123";
    private final String unterminated = "abc{33";
    private final String unterminated2 = "abc{";
    private final String emptyQuoted = "abc{}123";
    private final String outsideUnicode = "abc{7fffffff}";
    private final String noise = "abc{7fff{||\\ffff}";
    private final String fullAsciiEncoded = "{0}{1}{2}{3}{4}{5}{6}{7}{8}{9}"
            + "{a}{b}{c}{d}{e}{f}{10}{11}{12}{13}{14}{15}{16}{17}"
            + "{18}{19}{1a}{1b}{1c}{1d}{1e}{1f}{20}"
            + "!\"#$%&'()*+,-./0123456789:;<=>?@"
            + "ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`"
            + "abcdefghijklmnopqrstuvwxyz{7b}|{7d}~{7f}";
    private final int[] testCodepoints = { 0x0, '\n', ' ', 'a', '{', '}', 0x7f, 0x80,
            0x7ff, 0x800, 0xd7ff, 0xe000, 0xffff, 0x10000, 0x10ffff, 0x34,
            0x355, 0x2567, 0xfff, 0xe987, 0x100abc };
    private final String semiNastyEncoded = "{0}{a}{20}a{7b}{7d}{7f}{80}"
            + "{7ff}{800}{d7ff}{e000}{ffff}{10000}{10ffff}4"
            + "{355}{2567}{fff}{e987}{100abc}";
    private final String invalidUnicode = "abc\ud812";
    private final String invalidUnicodeEncoded = "abc{d812}";

    StringBuilder s;

    @Before
    public void setUp() {
        s = new StringBuilder();
    }

    @After
    public void tearDown() {
        s = null;
    }

    @Test
    public final void testBasic() {
        Encoder.encode(basic, s);
        assertEquals(basic, s.toString());
    }

    @Test
    public final void testBasic2() {
        Encoder.encode(basic2Decoded, s);
        assertEquals(basic2, s.toString());
    }

    @Test
    public final void testEncodeAscii() {
        Encoder.encode(fullAscii(), s);
        assertEquals(fullAsciiEncoded, s.toString());
    }

    @Test
    public final void testEncodeMixed() {
        Encoder.encode(semiNasty(), s);
        assertEquals(semiNastyEncoded, s.toString());
    }

    @Test
    public final void testEncodeQuotedIsLast() {
        Encoder.encode(quotedIsLastDecoded, s);
        assertEquals(quotedIsLast, s.toString());
    }

    @Test
    public final void testInvalidUnicode() {
        Encoder.encode(invalidUnicode, s);
        assertEquals(invalidUnicodeEncoded, s.toString());
    }


    @Test
    public final void testDecodeBasic() {
        Encoder.decode(basic, s);
        assertEquals(basic, s.toString());
    }

    @Test
    public final void testDecodeBasic2() {
        Encoder.decode(basic2, s);
        assertEquals(basic2Decoded, s.toString());
    }

    @Test
    public final void testDecodeAscii() {
        Encoder.decode(fullAsciiEncoded, s);
        assertEquals(fullAscii(), s.toString());
    }

    @Test
    public final void testDecodeMixed() {
        Encoder.decode(semiNastyEncoded, s);
        assertEquals(semiNasty(), s.toString());
    }



    @Test
    public final void testDecodeQuotedIsLast() {
        Encoder.decode(quotedIsLast, s);
        assertEquals(quotedIsLastDecoded, s.toString());
    }


    @Test
    public final void testDecodeUnterminated() {
        try {
            Encoder.decode(unterminated, s);
        } catch (IllegalArgumentException e) {
            return;
        }
        fail("Expected IllegalArgumentException");
    }

    @Test
    public final void testDecodeUnterminated2() {
        try {
            Encoder.decode(unterminated2, s);
        } catch (IllegalArgumentException e) {
            return;
        }
        fail("Expected IllegalArgumentException");

    }

    @Test
    public final void testEmptyQuoted() {
        try {
            Encoder.decode(emptyQuoted, s);
        } catch (IllegalArgumentException e) {
            return;
        }
        fail("Expected IllegalArgumentException");
    }

    @Test
    public final void testOutsideUnicode() {
        try {
            Encoder.decode(outsideUnicode, s);
        } catch (IllegalArgumentException e) {
            return;
        }
        fail("Expected IllegalArgumentException");
    }


    @Test
    public final void testNoise() {
        try {
            Encoder.decode(noise, s);
        } catch (IllegalArgumentException e) {
            return;
        }
        fail("Expected IllegalArgumentException");
    }

    @Test
    public final void testIllegalInputCharacter() {
        try {
            Encoder.decode("abc\u00e5", s);
        } catch (IllegalArgumentException e) {
            return;
        }
        fail("Expected IllegalArgumentException");
    }


    @Test
    public final void testNoIllegalCharactersInOutputForAscii() {
        Encoder.encode(fullAscii(), s);
        checkNoNonAscii(s.toString());
    }

    @Test
    public final void testNoIllegalCharactersInOutputForMixedInput() {
        Encoder.encode(semiNasty(), s);
        checkNoNonAscii(s.toString());
    }

    @Test
    public final void testSymmetryAscii() {
        StringBuilder forDecoding = new StringBuilder();
        Encoder.encode(fullAscii(), s);
        Encoder.decode(s.toString(), forDecoding);
        assertEquals(fullAscii(), forDecoding.toString());
    }

    @Test
    public final void testSymmetryMixed() {
        StringBuilder forDecoding = new StringBuilder();
        Encoder.encode(semiNasty(), s);
        Encoder.decode(s.toString(), forDecoding);
        assertEquals(semiNasty(), forDecoding.toString());
    }


    private void checkNoNonAscii(String input) {
        for (int i = 0; i < input.length(); ++i) {
            char c = input.charAt(i);
            if (c > '~' || c <= ' ') {
                fail("Encoded data contained character ordinal " + Integer.toHexString(c));
            }
        }
    }

    private String fullAscii() {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i <= 0x7f; ++i) {
            s.append((char) i);
        }
        return s.toString();
    }

    private String semiNasty() {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < testCodepoints.length; ++i) {
            s.append(Character.toChars(testCodepoints[i]));
        }
        return s.toString();
    }
}
