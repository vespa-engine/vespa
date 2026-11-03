// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

    static private String fromCP(String prefix, int [] codePoints, String suffix) {
        StringBuilder sb = new StringBuilder(prefix);
        for (int cp : codePoints) {
            sb.appendCodePoint(cp);
        }
        sb.append(suffix);
        return sb.toString();
    }

    @Test
    public void testSubstringByCodePoint() {
        assertEquals("", Text.substringByCodepoints("", 0, 0));
        assertEquals("", Text.substringByCodepoints("abcdef", 0, 0));
        assertEquals("", Text.substringByCodepoints("abcdef", 3, 3));
        assertEquals("", Text.substringByCodepoints("abcdef", 3, 2));
        assertEquals("", Text.substringByCodepoints("abcdef", 7, 9));
        assertEquals("abcdef", Text.substringByCodepoints("abcdef", 0, 9));
        assertEquals("a", Text.substringByCodepoints("abcdef", 0, 1));
        assertEquals("cd", Text.substringByCodepoints("abcdef", 2, 4));

        String withSurrogates = fromCP("abc", new int[]{0x10F000, 0x10F001, 0x10F002}, "def");
        assertEquals(withSurrogates, Text.substringByCodepoints(withSurrogates, 0, 11));
        assertEquals(withSurrogates, Text.substringByCodepoints(withSurrogates, 0, 20));
        assertEquals("", Text.substringByCodepoints(withSurrogates, 10, 11));
        assertEquals(fromCP("bc", new int[]{0x10F000, 0x10F001}, ""),
                     Text.substringByCodepoints(withSurrogates, 1, 5));
        assertEquals(fromCP("", new int[]{0x10F001}, ""),
                     Text.substringByCodepoints(withSurrogates, 4, 5));
        assertEquals(fromCP("", new int[]{0x10F001, 0x10F002}, "de"),
                     Text.substringByCodepoints(withSurrogates, 4, 8));
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
        assertEquals("abc ...", Text.truncate("abc\uD83D\uDE48\uD83D\uDE49\uD83D\uDE4Adef", 7));
        assertEquals("abc\uD83D\uDE48 ...", Text.truncate("abc\uD83D\uDE48\uD83D\uDE49\uD83D\uDE4Adef", 8));
        assertEquals("abc\uD83D\uDE48\uD83D\uDE49\uD83D\uDE4Adef", Text.truncate("abc\uD83D\uDE48\uD83D\uDE49\uD83D\uDE4Adef", 9));
    }

    @Test
    public void testFormat() {
	assertEquals("foo 3.14", Text.format("%s %.2f", "foo", 3.1415926536));
    }

    @Test
    public void testToExcessHex8() {
        assertEquals("00000000", Text.toExcessHex8(Integer.MIN_VALUE));
        assertEquals("00000001", Text.toExcessHex8(Integer.MIN_VALUE + 1));
        assertEquals("7fffffff", Text.toExcessHex8(-1));
        assertEquals("80000000", Text.toExcessHex8(0));
        assertEquals("80000001", Text.toExcessHex8(1));
        assertEquals("8000000a", Text.toExcessHex8(10));
        assertEquals("fffffffe", Text.toExcessHex8(Integer.MAX_VALUE - 1));
        assertEquals("ffffffff", Text.toExcessHex8(Integer.MAX_VALUE));
    }

    @Test
    public void testToExcessHex8PreservesOrdering() {
        int[] values = new int[] { Integer.MIN_VALUE, Integer.MIN_VALUE + 1, -1000000, -256, -2, -1,
                                   0, 1, 2, 256, 1000000, Integer.MAX_VALUE - 1, Integer.MAX_VALUE };
        for (int i = 1; i < values.length; i++) {
            String lower = Text.toExcessHex8(values[i - 1]);
            String higher = Text.toExcessHex8(values[i]);
            assertEquals(8, lower.length());
            assertEquals(8, higher.length());
            assertTrue(lower + " should sort before " + higher, lower.compareTo(higher) < 0);
        }
    }

    @Test
    public void testToExcessHex16() {
        assertEquals("0000000000000000", Text.toExcessHex16(Long.MIN_VALUE));
        assertEquals("0000000000000001", Text.toExcessHex16(Long.MIN_VALUE + 1));
        assertEquals("7fffffffffffffff", Text.toExcessHex16(-1L));
        assertEquals("8000000000000000", Text.toExcessHex16(0L));
        assertEquals("8000000000000001", Text.toExcessHex16(1L));
        assertEquals("800000000000000a", Text.toExcessHex16(10L));
        assertEquals("fffffffffffffffe", Text.toExcessHex16(Long.MAX_VALUE - 1));
        assertEquals("ffffffffffffffff", Text.toExcessHex16(Long.MAX_VALUE));

        // Ints widen, and get a different encoding than from toExcessHex8
        assertEquals("7fffffff80000000", Text.toExcessHex16(Integer.MIN_VALUE));
        assertEquals("800000007fffffff", Text.toExcessHex16(Integer.MAX_VALUE));
    }

    @Test
    public void testToExcessHex16PreservesOrdering() {
        long[] values = new long[] { Long.MIN_VALUE, Long.MIN_VALUE + 1, Integer.MIN_VALUE, -1000000L, -256L, -2L, -1L,
                                     0L, 1L, 2L, 256L, 1000000L, Integer.MAX_VALUE, Long.MAX_VALUE - 1, Long.MAX_VALUE };
        for (int i = 1; i < values.length; i++) {
            String lower = Text.toExcessHex16(values[i - 1]);
            String higher = Text.toExcessHex16(values[i]);
            assertEquals(16, lower.length());
            assertEquals(16, higher.length());
            assertTrue(lower + " should sort before " + higher, lower.compareTo(higher) < 0);
        }
    }

    @Test
    public void testFloatToExcessHex8() {
        assertEquals("007fffff", Text.floatToExcessHex8(Float.NEGATIVE_INFINITY));
        assertEquals("00800000", Text.floatToExcessHex8(-Float.MAX_VALUE));
        assertEquals("407fffff", Text.floatToExcessHex8(-1.0f));
        assertEquals("7ffffffe", Text.floatToExcessHex8(-Float.MIN_VALUE));
        assertEquals("7fffffff", Text.floatToExcessHex8(-0.0f));
        assertEquals("80000000", Text.floatToExcessHex8(0.0f));
        assertEquals("80000001", Text.floatToExcessHex8(Float.MIN_VALUE));
        assertEquals("bf800000", Text.floatToExcessHex8(1.0f));
        assertEquals("ff7fffff", Text.floatToExcessHex8(Float.MAX_VALUE));
        assertEquals("ff800000", Text.floatToExcessHex8(Float.POSITIVE_INFINITY));

        // NaN sorts outside the infinities, on the side given by its sign bit
        assertEquals("ffc00000", Text.floatToExcessHex8(Float.NaN));
        assertEquals("003fffff", Text.floatToExcessHex8(Float.intBitsToFloat(0xffc00000)));
    }

    @Test
    public void testFloatToExcessHex8PreservesOrdering() {
        float[] values = new float[] { Float.NEGATIVE_INFINITY, -Float.MAX_VALUE, -1.0e10f, -1.5f, -1.0f,
                                       -Float.MIN_NORMAL, -Float.MIN_VALUE, -0.0f, 0.0f, Float.MIN_VALUE,
                                       Float.MIN_NORMAL, 1.0f, 1.5f, 1.0e10f, Float.MAX_VALUE, Float.POSITIVE_INFINITY };
        for (int i = 1; i < values.length; i++) {
            String lower = Text.floatToExcessHex8(values[i - 1]);
            String higher = Text.floatToExcessHex8(values[i]);
            assertEquals(8, lower.length());
            assertEquals(8, higher.length());
            assertTrue(lower + " should sort before " + higher, lower.compareTo(higher) < 0);
        }
    }

    @Test
    public void testDoubleToExcessHex16() {
        assertEquals("000fffffffffffff", Text.doubleToExcessHex16(Double.NEGATIVE_INFINITY));
        assertEquals("0010000000000000", Text.doubleToExcessHex16(-Double.MAX_VALUE));
        assertEquals("400fffffffffffff", Text.doubleToExcessHex16(-1.0));
        assertEquals("7ffffffffffffffe", Text.doubleToExcessHex16(-Double.MIN_VALUE));
        assertEquals("7fffffffffffffff", Text.doubleToExcessHex16(-0.0));
        assertEquals("8000000000000000", Text.doubleToExcessHex16(0.0));
        assertEquals("8000000000000001", Text.doubleToExcessHex16(Double.MIN_VALUE));
        assertEquals("bff0000000000000", Text.doubleToExcessHex16(1.0));
        assertEquals("ffefffffffffffff", Text.doubleToExcessHex16(Double.MAX_VALUE));
        assertEquals("fff0000000000000", Text.doubleToExcessHex16(Double.POSITIVE_INFINITY));

        // NaN sorts outside the infinities, on the side given by its sign bit
        assertEquals("fff8000000000000", Text.doubleToExcessHex16(Double.NaN));
        assertEquals("0007ffffffffffff", Text.doubleToExcessHex16(Double.longBitsToDouble(0xfff8000000000000L)));

        // Floats widen, and get a different encoding than from floatToExcessHex8
        assertEquals("bff0000000000000", Text.doubleToExcessHex16(1.0f));
    }

    @Test
    public void testDoubleToExcessHex16PreservesOrdering() {
        double[] values = new double[] { Double.NEGATIVE_INFINITY, -Double.MAX_VALUE, -Float.MAX_VALUE, -1.0e10, -1.5, -1.0,
                                         -Double.MIN_NORMAL, -Double.MIN_VALUE, -0.0, 0.0, Double.MIN_VALUE,
                                         Double.MIN_NORMAL, 1.0, 1.5, 1.0e10, Float.MAX_VALUE, Double.MAX_VALUE,
                                         Double.POSITIVE_INFINITY };
        for (int i = 1; i < values.length; i++) {
            String lower = Text.doubleToExcessHex16(values[i - 1]);
            String higher = Text.doubleToExcessHex16(values[i]);
            assertEquals(16, lower.length());
            assertEquals(16, higher.length());
            assertTrue(lower + " should sort before " + higher, lower.compareTo(higher) < 0);
        }
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
