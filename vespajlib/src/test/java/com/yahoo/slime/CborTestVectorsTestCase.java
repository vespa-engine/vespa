// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

/**
 * Tests CborDecoder against the official CBOR test vectors from RFC 8949 Appendix A.
 */
public class CborTestVectorsTestCase {

    static byte[] hex(String s) {
        int len = s.length() / 2;
        byte[] data = new byte[len];
        for (int i = 0; i < len; i++) {
            data[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        }
        return data;
    }

    Slime decode(String hexStr) {
        return CborFormat.decode(hex(hexStr));
    }

    void verifyLong(String hexStr, long expected) {
        Slime slime = decode(hexStr);
        assertEquals(Type.LONG, slime.get().type());
        assertEquals(expected, slime.get().asLong());
    }

    void verifyDouble(String hexStr, double expected) {
        Slime slime = decode(hexStr);
        assertEquals(Type.DOUBLE, slime.get().type());
        assertEquals(expected, slime.get().asDouble(), 0.0);
    }

    void verifyString(String hexStr, String expected) {
        Slime slime = decode(hexStr);
        assertEquals(Type.STRING, slime.get().type());
        assertEquals(expected, slime.get().asString());
    }

    void verifyData(String hexStr, byte[] expected) {
        Slime slime = decode(hexStr);
        assertEquals(Type.DATA, slime.get().type());
        assertArrayEquals(expected, slime.get().asData());
    }

    void verifyDecodeFails(String hexStr) {
        Slime slime = decode(hexStr);
        Cursor c = slime.get();
        assertEquals(Type.OBJECT, c.type());
        assertTrue(c.field("error_message").valid());
    }

    // --- Unsigned integers (major type 0) ---
    // RFC 8949 Appendix A test vectors

    @Test
    public void testUnsignedIntegers() {
        verifyLong("00", 0);        // 0
        verifyLong("01", 1);        // 1
        verifyLong("0a", 10);       // 10
        verifyLong("17", 23);       // 23
        verifyLong("1818", 24);     // 24
        verifyLong("1819", 25);     // 25
        verifyLong("1864", 100);    // 100
        verifyLong("1903e8", 1000); // 1000
        verifyLong("1a000f4240", 1000000);          // 1000000
        verifyLong("1b000000e8d4a51000", 1000000000000L); // 1000000000000
    }

    @Test
    public void testMaxSignedLong() {
        // Long.MAX_VALUE (2^63-1 = 0x7fffffffffffffff) fits
        verifyLong("1b7fffffffffffffff", Long.MAX_VALUE);
    }

    @Test
    public void testMaxUint64Overflow() {
        // 18446744073709551615 (2^64-1) does not fit in Java signed long
        verifyDecodeFails("1bffffffffffffffff");
    }

    // --- Negative integers (major type 1) ---

    @Test
    public void testNegativeIntegers() {
        verifyLong("20", -1);       // -1
        verifyLong("29", -10);      // -10
        verifyLong("3863", -100);   // -100
        verifyLong("3903e7", -1000); // -1000
    }

    @Test
    public void testMinSignedLong() {
        // Long.MIN_VALUE (-2^63): argument is 2^63-1 = 0x7fffffffffffffff, value = -1 - (2^63-1) = -2^63
        verifyLong("3b7fffffffffffffff", Long.MIN_VALUE);
    }

    @Test
    public void testMaxNegativeOverflow() {
        // -18446744073709551616 (-2^64) does not fit in Java signed long
        verifyDecodeFails("3bffffffffffffffff");
    }

    // --- Half-precision floats ---

    @Test
    public void testHalfPrecisionFloats() {
        verifyDouble("f90000", 0.0);        // 0.0
        verifyDouble("f98000", -0.0);       // -0.0
        verifyDouble("f93c00", 1.0);        // 1.0
        verifyDouble("f93e00", 1.5);        // 1.5
        verifyDouble("f97bff", 65504.0);    // 65504.0 (max finite half)
        verifyDouble("f90001", 5.960464477539063e-08);  // smallest positive subnormal
        verifyDouble("f90400", 6.103515625e-05);        // smallest positive normal
        verifyDouble("f9c400", -4.0);       // -4.0
    }

    @Test
    public void testHalfPrecisionSpecialValues() {
        Slime slime;

        slime = decode("f97c00"); // Infinity
        assertEquals(Type.DOUBLE, slime.get().type());
        assertTrue(Double.isInfinite(slime.get().asDouble()));
        assertTrue(slime.get().asDouble() > 0);

        slime = decode("f97e00"); // NaN
        assertEquals(Type.DOUBLE, slime.get().type());
        assertTrue(Double.isNaN(slime.get().asDouble()));

        slime = decode("f9fc00"); // -Infinity
        assertEquals(Type.DOUBLE, slime.get().type());
        assertTrue(Double.isInfinite(slime.get().asDouble()));
        assertTrue(slime.get().asDouble() < 0);
    }

    // --- Single-precision floats ---

    @Test
    public void testSinglePrecisionFloats() {
        verifyDouble("fa47c35000", 100000.0);
        verifyDouble("fa7f7fffff", 3.4028234663852886e+38); // Float.MAX_VALUE
    }

    @Test
    public void testSinglePrecisionSpecialValues() {
        Slime slime;

        slime = decode("fa7f800000"); // Infinity
        assertTrue(Double.isInfinite(slime.get().asDouble()));
        assertTrue(slime.get().asDouble() > 0);

        slime = decode("fa7fc00000"); // NaN
        assertTrue(Double.isNaN(slime.get().asDouble()));

        slime = decode("faff800000"); // -Infinity
        assertTrue(Double.isInfinite(slime.get().asDouble()));
        assertTrue(slime.get().asDouble() < 0);
    }

    // --- Double-precision floats ---

    @Test
    public void testDoublePrecisionFloats() {
        verifyDouble("fb3ff199999999999a", 1.1);
        verifyDouble("fb7e37e43c8800759c", 1.0e+300);
        verifyDouble("fbc010666666666666", -4.1);
    }

    @Test
    public void testDoublePrecisionSpecialValues() {
        Slime slime;

        slime = decode("fb7ff0000000000000"); // Infinity
        assertTrue(Double.isInfinite(slime.get().asDouble()));
        assertTrue(slime.get().asDouble() > 0);

        slime = decode("fb7ff8000000000000"); // NaN
        assertTrue(Double.isNaN(slime.get().asDouble()));

        slime = decode("fbfff0000000000000"); // -Infinity
        assertTrue(Double.isInfinite(slime.get().asDouble()));
        assertTrue(slime.get().asDouble() < 0);
    }

    // --- Simple values ---

    @Test
    public void testBooleans() {
        Slime slime;

        slime = decode("f4"); // false
        assertEquals(Type.BOOL, slime.get().type());
        assertFalse(slime.get().asBool());

        slime = decode("f5"); // true
        assertEquals(Type.BOOL, slime.get().type());
        assertTrue(slime.get().asBool());
    }

    @Test
    public void testNull() {
        Slime slime = decode("f6"); // null
        assertEquals(Type.NIX, slime.get().type());
    }

    @Test
    public void testUndefined() {
        Slime slime = decode("f7"); // undefined
        assertEquals(Type.NIX, slime.get().type());
    }

    @Test
    public void testUnsupportedSimpleValues() {
        verifyDecodeFails("f0");   // simple(16)
        verifyDecodeFails("f818"); // simple(24)
        verifyDecodeFails("f8ff"); // simple(255)
    }

    // --- Byte strings (major type 2) -> Slime DATA ---

    @Test
    public void testByteStrings() {
        verifyData("40", new byte[0]);                    // h''
        verifyData("4401020304", new byte[]{1, 2, 3, 4}); // h'01020304'
    }

    // --- Text strings (major type 3) -> Slime STRING ---

    @Test
    public void testTextStrings() {
        verifyString("60", "");        // ""
        verifyString("6161", "a");     // "a"
        verifyString("6449455446", "IETF"); // "IETF"
        verifyString("62225c", "\"\\");      // "\"\\"
    }

    @Test
    public void testTextStringsWithUnicode() {
        verifyString("62c3bc", "\u00fc");    // "ü"
        verifyString("63e6b0b4", "\u6c34");  // "水"
        verifyString("64f0908591", "\ud800\udd51"); // "𐅑" (U+10151)
    }

    // --- Arrays (major type 4) ---

    @Test
    public void testEmptyArray() {
        Slime slime = decode("80"); // []
        Cursor c = slime.get();
        assertEquals(Type.ARRAY, c.type());
        assertEquals(0, c.children());
    }

    @Test
    public void testSimpleArray() {
        Slime slime = decode("83010203"); // [1, 2, 3]
        Cursor c = slime.get();
        assertEquals(Type.ARRAY, c.type());
        assertEquals(3, c.children());
        assertEquals(1L, c.entry(0).asLong());
        assertEquals(2L, c.entry(1).asLong());
        assertEquals(3L, c.entry(2).asLong());
    }

    @Test
    public void testNestedArrays() {
        Slime slime = decode("8301820203820405"); // [1, [2, 3], [4, 5]]
        Cursor c = slime.get();
        assertEquals(Type.ARRAY, c.type());
        assertEquals(3, c.children());
        assertEquals(1L, c.entry(0).asLong());

        Cursor inner1 = c.entry(1);
        assertEquals(Type.ARRAY, inner1.type());
        assertEquals(2, inner1.children());
        assertEquals(2L, inner1.entry(0).asLong());
        assertEquals(3L, inner1.entry(1).asLong());

        Cursor inner2 = c.entry(2);
        assertEquals(Type.ARRAY, inner2.type());
        assertEquals(2, inner2.children());
        assertEquals(4L, inner2.entry(0).asLong());
        assertEquals(5L, inner2.entry(1).asLong());
    }

    @Test
    public void testLargeArray() {
        // [1, 2, 3, ... 25] - 25 elements, length encoded with 1-byte additional
        Slime slime = decode("98190102030405060708090a0b0c0d0e0f101112131415161718181819");
        Cursor c = slime.get();
        assertEquals(Type.ARRAY, c.type());
        assertEquals(25, c.children());
        for (int i = 0; i < 25; i++) {
            assertEquals(i + 1L, c.entry(i).asLong());
        }
    }

    // --- Maps (major type 5) with text string keys ---

    @Test
    public void testEmptyMap() {
        Slime slime = decode("a0"); // {}
        Cursor c = slime.get();
        assertEquals(Type.OBJECT, c.type());
        assertEquals(0, c.children());
    }

    @Test
    public void testMapWithTextKeys() {
        Slime slime = decode("a26161016162820203"); // {"a": 1, "b": [2, 3]}
        Cursor c = slime.get();
        assertEquals(Type.OBJECT, c.type());
        assertEquals(2, c.children());
        assertEquals(1L, c.field("a").asLong());

        Cursor arr = c.field("b");
        assertEquals(Type.ARRAY, arr.type());
        assertEquals(2, arr.children());
        assertEquals(2L, arr.entry(0).asLong());
        assertEquals(3L, arr.entry(1).asLong());
    }

    @Test
    public void testArrayContainingMap() {
        Slime slime = decode("826161a161626163"); // ["a", {"b": "c"}]
        Cursor c = slime.get();
        assertEquals(Type.ARRAY, c.type());
        assertEquals(2, c.children());
        assertEquals("a", c.entry(0).asString());

        Cursor obj = c.entry(1);
        assertEquals(Type.OBJECT, obj.type());
        assertEquals("c", obj.field("b").asString());
    }

    @Test
    public void testMapWithFiveEntries() {
        // {"a": "A", "b": "B", "c": "C", "d": "D", "e": "E"}
        Slime slime = decode("a56161614161626142616361436164614461656145");
        Cursor c = slime.get();
        assertEquals(Type.OBJECT, c.type());
        assertEquals(5, c.children());
        assertEquals("A", c.field("a").asString());
        assertEquals("B", c.field("b").asString());
        assertEquals("C", c.field("c").asString());
        assertEquals("D", c.field("d").asString());
        assertEquals("E", c.field("e").asString());
    }

    @Test
    public void testMapWithIntegerKeysIsRejected() {
        // {1: 2, 3: 4} - integer keys not supported for Slime OBJECT
        verifyDecodeFails("a201020304");
    }

    // --- Tagged values (major type 6) ---

    @Test
    public void testTaggedDateTimeString() {
        // 0("2013-03-21T20:04:00Z") - tag 0 wrapping text string
        Slime slime = decode("c074323031332d30332d32315432303a30343a30305a");
        assertEquals(Type.STRING, slime.get().type());
        assertEquals("2013-03-21T20:04:00Z", slime.get().asString());
    }

    @Test
    public void testTaggedEpochInteger() {
        // 1(1363896240) - tag 1 wrapping unsigned int
        Slime slime = decode("c11a514b67b0");
        assertEquals(Type.LONG, slime.get().type());
        assertEquals(1363896240L, slime.get().asLong());
    }

    @Test
    public void testTaggedEpochFloat() {
        // 1(1363896240.5) - tag 1 wrapping double
        Slime slime = decode("c1fb41d452d9ec200000");
        assertEquals(Type.DOUBLE, slime.get().type());
        assertEquals(1363896240.5, slime.get().asDouble(), 0.0);
    }

    @Test
    public void testTaggedByteString() {
        // 23(h'01020304') - tag 23 wrapping byte string
        Slime slime = decode("d74401020304");
        assertEquals(Type.DATA, slime.get().type());
        assertArrayEquals(new byte[]{1, 2, 3, 4}, slime.get().asData());
    }

    @Test
    public void testTaggedEncodedData() {
        // 24(h'6449455446') - tag 24 wrapping byte string
        Slime slime = decode("d818456449455446");
        assertEquals(Type.DATA, slime.get().type());
        byte[] expected = hex("6449455446");
        assertArrayEquals(expected, slime.get().asData());
    }

    @Test
    public void testTaggedUri() {
        // 32("http://www.example.com") - tag 32 wrapping text string
        Slime slime = decode("d82076687474703a2f2f7777772e6578616d706c652e636f6d");
        assertEquals(Type.STRING, slime.get().type());
        assertEquals("http://www.example.com", slime.get().asString());
    }

    @Test
    public void testTaggedBignum() {
        // tag 2 wrapping byte string representing 18446744073709551616 (2^64)
        // Our decoder skips the tag and returns the inner byte string as DATA
        Slime slime = decode("c249010000000000000000");
        assertEquals(Type.DATA, slime.get().type());
        assertArrayEquals(hex("010000000000000000"), slime.get().asData());
    }

    @Test
    public void testTaggedNegativeBignum() {
        // tag 3 wrapping byte string representing -18446744073709551617
        // Our decoder skips the tag and returns the inner byte string as DATA
        Slime slime = decode("c349010000000000000000");
        assertEquals(Type.DATA, slime.get().type());
        assertArrayEquals(hex("010000000000000000"), slime.get().asData());
    }

    // --- Indefinite-length arrays ---

    @Test
    public void testIndefiniteLengthEmptyArray() {
        // [_ ]
        Slime slime = decode("9fff");
        assertEquals(Type.ARRAY, slime.get().type());
        assertEquals(0, slime.get().children());
    }

    @Test
    public void testIndefiniteLengthArray() {
        // [_ 1, [2, 3], [_ 4, 5]]
        Slime slime = decode("9f018202039f0405ffff");
        Cursor c = slime.get();
        assertEquals(Type.ARRAY, c.type());
        assertEquals(3, c.children());
        assertEquals(1L, c.entry(0).asLong());
        assertEquals(2L, c.entry(1).entry(0).asLong());
        assertEquals(3L, c.entry(1).entry(1).asLong());
        assertEquals(4L, c.entry(2).entry(0).asLong());
        assertEquals(5L, c.entry(2).entry(1).asLong());
    }

    @Test
    public void testIndefiniteLengthOuterArrayOnly() {
        // [_ 1, [2, 3], [4, 5]]
        Slime slime = decode("9f01820203820405ff");
        Cursor c = slime.get();
        assertEquals(3, c.children());
        assertEquals(1L, c.entry(0).asLong());
        assertEquals(2, c.entry(1).children());
        assertEquals(2, c.entry(2).children());
    }

    @Test
    public void testIndefiniteLengthInnerArrayOnly() {
        // [1, [2, 3], [_ 4, 5]]
        Slime slime = decode("83018202039f0405ff");
        Cursor c = slime.get();
        assertEquals(3, c.children());
        assertEquals(4L, c.entry(2).entry(0).asLong());
        assertEquals(5L, c.entry(2).entry(1).asLong());
    }

    @Test
    public void testIndefiniteLengthMiddleArrayOnly() {
        // [1, [_ 2, 3], [4, 5]]
        Slime slime = decode("83019f0203ff820405");
        Cursor c = slime.get();
        assertEquals(3, c.children());
        assertEquals(2L, c.entry(1).entry(0).asLong());
        assertEquals(3L, c.entry(1).entry(1).asLong());
    }

    @Test
    public void testIndefiniteLengthLargeArray() {
        // [_ 1, 2, 3, ... 25]
        Slime slime = decode("9f0102030405060708090a0b0c0d0e0f101112131415161718181819ff");
        Cursor c = slime.get();
        assertEquals(Type.ARRAY, c.type());
        assertEquals(25, c.children());
        for (int i = 0; i < 25; i++) {
            assertEquals(i + 1L, c.entry(i).asLong());
        }
    }

    // --- Indefinite-length maps ---

    @Test
    public void testIndefiniteLengthMap() {
        // {_ "a": 1, "b": [_ 2, 3]}
        Slime slime = decode("bf61610161629f0203ffff");
        Cursor c = slime.get();
        assertEquals(Type.OBJECT, c.type());
        assertEquals(2, c.children());
        assertEquals(1L, c.field("a").asLong());
        Cursor arr = c.field("b");
        assertEquals(Type.ARRAY, arr.type());
        assertEquals(2L, arr.entry(0).asLong());
        assertEquals(3L, arr.entry(1).asLong());
    }

    @Test
    public void testDefiniteArrayWithIndefiniteMap() {
        // ["a", {_ "b": "c"}]
        Slime slime = decode("826161bf61626163ff");
        Cursor c = slime.get();
        assertEquals(Type.ARRAY, c.type());
        assertEquals("a", c.entry(0).asString());
        assertEquals("c", c.entry(1).field("b").asString());
    }

    @Test
    public void testIndefiniteLengthMapMixedValues() {
        // {_ "Fun": true, "Amt": -2}
        Slime slime = decode("bf6346756ef563416d7421ff");
        Cursor c = slime.get();
        assertEquals(Type.OBJECT, c.type());
        assertEquals(2, c.children());
        assertTrue(c.field("Fun").asBool());
        assertEquals(-2L, c.field("Amt").asLong());
    }

    // --- Indefinite-length byte/text strings ---

    @Test
    public void testIndefiniteLengthByteString() {
        // (_ h'0102', h'030405') -> h'0102030405'
        Slime slime = decode("5f42010243030405ff");
        assertEquals(Type.DATA, slime.get().type());
        assertArrayEquals(new byte[]{1, 2, 3, 4, 5}, slime.get().asData());
    }

    @Test
    public void testIndefiniteLengthTextString() {
        // (_ "strea", "ming") -> "streaming"
        Slime slime = decode("7f657374726561646d696e67ff");
        assertEquals(Type.STRING, slime.get().type());
        assertEquals("streaming", slime.get().asString());
    }
}
