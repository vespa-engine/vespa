// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class CborFormatTestCase {

    // CBOR major types
    static final int UINT   = 0; // major 0: unsigned integer
    static final int NINT   = 1; // major 1: negative integer
    static final int BSTR   = 2; // major 2: byte string
    static final int TSTR   = 3; // major 3: text string
    static final int ARRAY  = 4; // major 4: array
    static final int MAP    = 5; // major 5: map
    static final int TAG    = 6; // major 6: tag
    static final int SIMPLE = 7; // major 7: simple values and floats

    static byte initial(int major, int additional) {
        return (byte) ((major << 5) | additional);
    }

    Slime decode(byte[] data) {
        return CborFormat.decode(data);
    }

    void verifyDecodeFails(byte[] data) {
        Slime slime = decode(data);
        Cursor c = slime.get();
        assertTrue(c.valid());
        assertEquals(Type.OBJECT, c.type());
        assertTrue(c.field("error_message").valid());
    }

    // --- Half-precision float conversion ---

    @Test
    public void testHalfToDouble() {
        assertEquals(0.0, CborDecoder.halfToDouble(0x0000), 0.0);
        assertEquals(-0.0, CborDecoder.halfToDouble(0x8000), 0.0);
        assertEquals(1.0, CborDecoder.halfToDouble(0x3c00), 0.0);
        assertEquals(-1.0, CborDecoder.halfToDouble(0xbc00), 0.0);
        assertEquals(0.5, CborDecoder.halfToDouble(0x3800), 0.0);
        assertEquals(65504.0, CborDecoder.halfToDouble(0x7bff), 0.0); // max finite half
        assertEquals(Double.POSITIVE_INFINITY, CborDecoder.halfToDouble(0x7c00), 0.0);
        assertEquals(Double.NEGATIVE_INFINITY, CborDecoder.halfToDouble(0xfc00), 0.0);
        assertTrue(Double.isNaN(CborDecoder.halfToDouble(0x7e00)));
        // subnormal: smallest positive
        assertEquals(Math.scalb(1.0, -24), CborDecoder.halfToDouble(0x0001), 0.0);
    }

    // --- Null / undefined ---

    @Test
    public void testDecodingNull() {
        byte[] data = { initial(SIMPLE, 22) }; // null
        Slime slime = decode(data);
        Cursor c = slime.get();
        assertTrue(c.valid());
        assertEquals(Type.NIX, c.type());
    }

    @Test
    public void testDecodingUndefined() {
        byte[] data = { initial(SIMPLE, 23) }; // undefined
        Slime slime = decode(data);
        Cursor c = slime.get();
        assertTrue(c.valid());
        assertEquals(Type.NIX, c.type());
    }

    // --- Booleans ---

    @Test
    public void testDecodingBooleans() {
        {
            byte[] data = { initial(SIMPLE, 20) }; // false
            Slime slime = decode(data);
            Cursor c = slime.get();
            assertTrue(c.valid());
            assertEquals(Type.BOOL, c.type());
            assertFalse(c.asBool());
        }
        {
            byte[] data = { initial(SIMPLE, 21) }; // true
            Slime slime = decode(data);
            Cursor c = slime.get();
            assertTrue(c.valid());
            assertEquals(Type.BOOL, c.type());
            assertTrue(c.asBool());
        }
    }

    // --- Unsigned integers (major type 0) ---

    @Test
    public void testDecodingUnsignedIntegers() {
        { // 0 in-line
            byte[] data = { initial(UINT, 0) };
            Slime slime = decode(data);
            assertEquals(0L, slime.get().asLong());
        }
        { // 13 in-line
            byte[] data = { initial(UINT, 13) };
            Slime slime = decode(data);
            assertEquals(13L, slime.get().asLong());
        }
        { // 23 in-line (max)
            byte[] data = { initial(UINT, 23) };
            Slime slime = decode(data);
            assertEquals(23L, slime.get().asLong());
        }
        { // 24 with 1-byte argument
            byte[] data = { initial(UINT, 24), 24 };
            Slime slime = decode(data);
            assertEquals(24L, slime.get().asLong());
        }
        { // 42 with 1-byte argument
            byte[] data = { initial(UINT, 24), 42 };
            Slime slime = decode(data);
            assertEquals(42L, slime.get().asLong());
        }
        { // 255 with 1-byte argument
            byte[] data = { initial(UINT, 24), (byte) 0xff };
            Slime slime = decode(data);
            assertEquals(255L, slime.get().asLong());
        }
        { // 256 with 2-byte argument
            byte[] data = { initial(UINT, 25), 0x01, 0x00 };
            Slime slime = decode(data);
            assertEquals(256L, slime.get().asLong());
        }
        { // 1000 with 2-byte argument
            byte[] data = { initial(UINT, 25), 0x03, (byte) 0xe8 };
            Slime slime = decode(data);
            assertEquals(1000L, slime.get().asLong());
        }
        { // 65535 with 2-byte argument
            byte[] data = { initial(UINT, 25), (byte) 0xff, (byte) 0xff };
            Slime slime = decode(data);
            assertEquals(65535L, slime.get().asLong());
        }
        { // 1000000 with 4-byte argument
            byte[] data = { initial(UINT, 26), 0x00, 0x0f, 0x42, 0x40 };
            Slime slime = decode(data);
            assertEquals(1000000L, slime.get().asLong());
        }
        { // large value with 8-byte argument
            byte[] data = { initial(UINT, 27), 0x00, 0x00, 0x00, (byte) 0xe8,
                            (byte) 0xd4, (byte) 0xa5, 0x10, 0x00 };
            Slime slime = decode(data);
            assertEquals(1000000000000L, slime.get().asLong());
        }
    }

    // --- Negative integers (major type 1) ---

    @Test
    public void testDecodingNegativeIntegers() {
        { // -1 (argument 0)
            byte[] data = { initial(NINT, 0) };
            Slime slime = decode(data);
            assertEquals(-1L, slime.get().asLong());
        }
        { // -10 (argument 9)
            byte[] data = { initial(NINT, 9) };
            Slime slime = decode(data);
            assertEquals(-10L, slime.get().asLong());
        }
        { // -100 (argument 99, 1-byte)
            byte[] data = { initial(NINT, 24), 99 };
            Slime slime = decode(data);
            assertEquals(-100L, slime.get().asLong());
        }
        { // -1000 (argument 999, 2-byte)
            byte[] data = { initial(NINT, 25), 0x03, (byte) 0xe7 };
            Slime slime = decode(data);
            assertEquals(-1000L, slime.get().asLong());
        }
        { // -123456789 (argument 123456788, 4-byte)
            byte[] data = { initial(NINT, 26), 0x07, 0x5b, (byte) 0xcd, 0x14 };
            Slime slime = decode(data);
            assertEquals(-123456789L, slime.get().asLong());
        }
    }

    // --- Floating point ---

    @Test
    public void testDecodingHalfPrecisionFloat() {
        { // 0.0
            byte[] data = { initial(SIMPLE, 25), 0x00, 0x00 };
            Slime slime = decode(data);
            assertEquals(Type.DOUBLE, slime.get().type());
            assertEquals(0.0, slime.get().asDouble(), 0.0);
        }
        { // 1.0
            byte[] data = { initial(SIMPLE, 25), 0x3c, 0x00 };
            Slime slime = decode(data);
            assertEquals(1.0, slime.get().asDouble(), 0.0);
        }
        { // -1.0
            byte[] data = { initial(SIMPLE, 25), (byte) 0xbc, 0x00 };
            Slime slime = decode(data);
            assertEquals(-1.0, slime.get().asDouble(), 0.0);
        }
        { // 0.5
            byte[] data = { initial(SIMPLE, 25), 0x38, 0x00 };
            Slime slime = decode(data);
            assertEquals(0.5, slime.get().asDouble(), 0.0);
        }
    }

    @Test
    public void testDecodingSinglePrecisionFloat() {
        { // 1.0
            byte[] data = { initial(SIMPLE, 26), 0x3f, (byte) 0x80, 0x00, 0x00 };
            Slime slime = decode(data);
            assertEquals(Type.DOUBLE, slime.get().type());
            assertEquals(1.0, slime.get().asDouble(), 0.0);
        }
        { // 100000.0
            byte[] data = { initial(SIMPLE, 26), 0x47, (byte) 0xc3, 0x50, 0x00 };
            Slime slime = decode(data);
            assertEquals(100000.0, slime.get().asDouble(), 0.0);
        }
        { // 3.4028234663852886e+38 (max float)
            byte[] data = { initial(SIMPLE, 26), 0x7f, 0x7f, (byte) 0xff, (byte) 0xff };
            Slime slime = decode(data);
            assertEquals(Float.MAX_VALUE, slime.get().asDouble(), 0.0);
        }
    }

    @Test
    public void testDecodingDoublePrecisionFloat() {
        { // 0.0
            byte[] data = { initial(SIMPLE, 27), 0, 0, 0, 0, 0, 0, 0, 0 };
            Slime slime = decode(data);
            assertEquals(Type.DOUBLE, slime.get().type());
            assertEquals(0.0, slime.get().asDouble(), 0.0);
        }
        { // 1.0
            byte[] data = { initial(SIMPLE, 27), 0x3f, (byte) 0xf0, 0, 0, 0, 0, 0, 0 };
            Slime slime = decode(data);
            assertEquals(1.0, slime.get().asDouble(), 0.0);
        }
        { // -1.0
            byte[] data = { initial(SIMPLE, 27), (byte) 0xbf, (byte) 0xf0, 0, 0, 0, 0, 0, 0 };
            Slime slime = decode(data);
            assertEquals(-1.0, slime.get().asDouble(), 0.0);
        }
        { // 3.5
            byte[] data = { initial(SIMPLE, 27), 0x40, 0x0c, 0, 0, 0, 0, 0, 0 };
            Slime slime = decode(data);
            assertEquals(3.5, slime.get().asDouble(), 0.0);
        }
        { // 65535.875
            byte[] data = { initial(SIMPLE, 27),
                            0x40, (byte) 0xef, (byte) 0xff, (byte) 0xfc, 0, 0, 0, 0 };
            Slime slime = decode(data);
            assertEquals(65535.875, slime.get().asDouble(), 0.0);
        }
    }

    // --- Strings ---

    @Test
    public void testDecodingStrings() {
        { // empty string
            byte[] data = { initial(TSTR, 0) };
            Slime slime = decode(data);
            assertEquals(Type.STRING, slime.get().type());
            assertEquals("", slime.get().asString());
        }
        { // "fo"
            byte[] data = { initial(TSTR, 2), 'f', 'o' };
            Slime slime = decode(data);
            assertEquals("fo", slime.get().asString());
        }
        { // "string"
            byte[] data = { initial(TSTR, 6), 's', 't', 'r', 'i', 'n', 'g' };
            Slime slime = decode(data);
            assertEquals("string", slime.get().asString());
        }
        { // long string (> 23 bytes, needs 1-byte length)
            byte[] data = new byte[2 + 52];
            data[0] = initial(TSTR, 24);
            data[1] = 52;
            byte[] str = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".getBytes();
            System.arraycopy(str, 0, data, 2, 52);
            Slime slime = decode(data);
            assertEquals("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ",
                         slime.get().asString());
        }
    }

    // --- Data (byte strings) ---

    @Test
    public void testDecodingData() {
        { // empty
            byte[] data = { initial(BSTR, 0) };
            Slime slime = decode(data);
            assertEquals(Type.DATA, slime.get().type());
            assertArrayEquals(new byte[0], slime.get().asData());
        }
        { // two bytes
            byte[] data = { initial(BSTR, 2), 42, -123 };
            Slime slime = decode(data);
            byte[] expected = { 42, -123 };
            assertArrayEquals(expected, slime.get().asData());
        }
        { // "data" as bytes
            byte[] data = { initial(BSTR, 4), 'd', 'a', 't', 'a' };
            Slime slime = decode(data);
            byte[] expected = { 'd', 'a', 't', 'a' };
            assertArrayEquals(expected, slime.get().asData());
        }
    }

    // --- Arrays ---

    @Test
    public void testDecodingArray() {
        byte[] data = {
            initial(ARRAY, 6),                                      // array of 6 items
            initial(SIMPLE, 22),                                    // null -> NIX
            initial(SIMPLE, 21),                                    // true
            initial(UINT, 24), 42,                                  // 42
            initial(SIMPLE, 27), 0x40, 0x0c, 0, 0, 0, 0, 0, 0,    // 3.5
            initial(TSTR, 6), 's', 't', 'r', 'i', 'n', 'g',       // "string"
            initial(BSTR, 4), 'd', 'a', 't', 'a'                   // data
        };
        Slime slime = decode(data);
        Cursor c = slime.get();
        assertTrue(c.valid());
        assertEquals(Type.ARRAY, c.type());
        assertEquals(6, c.children());
        assertEquals(Type.NIX, c.entry(0).type());
        assertTrue(c.entry(1).asBool());
        assertEquals(42L, c.entry(2).asLong());
        assertEquals(3.5, c.entry(3).asDouble(), 0.0);
        assertEquals("string", c.entry(4).asString());
        byte[] expd = { 'd', 'a', 't', 'a' };
        assertArrayEquals(expd, c.entry(5).asData());
    }

    // --- Objects (maps) ---

    @Test
    public void testDecodingObject() {
        byte[] data = {
            initial(MAP, 6),                                        // map of 6 pairs
            initial(TSTR, 1), 'a', initial(SIMPLE, 22),             // "a" -> null
            initial(TSTR, 1), 'b', initial(SIMPLE, 21),             // "b" -> true
            initial(TSTR, 1), 'c', initial(UINT, 24), 42,           // "c" -> 42
            initial(TSTR, 1), 'd',
                initial(SIMPLE, 27), 0x40, 0x0c, 0, 0, 0, 0, 0, 0, // "d" -> 3.5
            initial(TSTR, 1), 'e',
                initial(TSTR, 6), 's', 't', 'r', 'i', 'n', 'g',    // "e" -> "string"
            initial(TSTR, 1), 'f',
                initial(BSTR, 4), 'd', 'a', 't', 'a'                // "f" -> data
        };
        Slime slime = decode(data);
        Cursor c = slime.get();
        assertTrue(c.valid());
        assertEquals(Type.OBJECT, c.type());
        assertEquals(6, c.children());
        assertEquals(Type.NIX, c.field("a").type());
        assertTrue(c.field("b").asBool());
        assertEquals(42L, c.field("c").asLong());
        assertEquals(3.5, c.field("d").asDouble(), 0.0);
        assertEquals("string", c.field("e").asString());
        byte[] expd = { 'd', 'a', 't', 'a' };
        assertArrayEquals(expd, c.field("f").asData());
    }

    // --- Complex nested structure ---

    @Test
    public void testDecodingComplexStructure() {
        // Equivalent to BinaryFormatTestCase.testEncodingComplexSlimeStructure:
        // { "bar": 10, "foo": [ 20, { "answer": 42 } ] }
        byte[] data = {
            initial(MAP, 2),                                        // map of 2 pairs
            initial(TSTR, 3), 'b', 'a', 'r',                       // key "bar"
                initial(UINT, 10),                                  // value 10
            initial(TSTR, 3), 'f', 'o', 'o',                       // key "foo"
                initial(ARRAY, 2),                                  // value: array of 2
                    initial(UINT, 20),                              // 20
                    initial(MAP, 1),                                // map of 1
                        initial(TSTR, 6), 'a', 'n', 's', 'w', 'e', 'r', // key "answer"
                        initial(UINT, 24), 42                       // value 42
        };
        Slime slime = decode(data);
        Cursor c = slime.get();
        assertTrue(c.valid());
        assertEquals(Type.OBJECT, c.type());
        assertEquals(2, c.children());
        assertEquals(10L, c.field("bar").asLong());

        Cursor arr = c.field("foo");
        assertEquals(Type.ARRAY, arr.type());
        assertEquals(2, arr.children());
        assertEquals(20L, arr.entry(0).asLong());

        Cursor inner = arr.entry(1);
        assertEquals(Type.OBJECT, inner.type());
        assertEquals(42L, inner.field("answer").asLong());
    }

    @Test
    public void testDecodingRepeatedKeys() {
        // Equivalent to BinaryFormatTestCase.testEncodingSlimeReusingSymbols:
        // [ { "foo": 10, "bar": 20 }, { "foo": 100, "bar": 200 } ]
        byte[] data = {
            initial(ARRAY, 2),                                      // array of 2
            initial(MAP, 2),                                        // map of 2
                initial(TSTR, 3), 'f', 'o', 'o',
                initial(UINT, 10),
                initial(TSTR, 3), 'b', 'a', 'r',
                initial(UINT, 20),
            initial(MAP, 2),                                        // map of 2
                initial(TSTR, 3), 'f', 'o', 'o',
                initial(UINT, 24), 100,
                initial(TSTR, 3), 'b', 'a', 'r',
                initial(UINT, 24), (byte) 200
        };
        Slime slime = decode(data);
        Cursor c = slime.get();
        assertEquals(Type.ARRAY, c.type());
        assertEquals(2, c.children());

        Cursor c1 = c.entry(0);
        assertEquals(10L, c1.field("foo").asLong());
        assertEquals(20L, c1.field("bar").asLong());

        Cursor c2 = c.entry(1);
        assertEquals(100L, c2.field("foo").asLong());
        assertEquals(200L, c2.field("bar").asLong());
    }

    // --- Tags ---

    @Test
    public void testDecodingTaggedValue() {
        // tag 1 (epoch-based date/time) wrapping integer 1363896240
        byte[] data = {
            initial(TAG, 1),                                        // tag(1)
            initial(UINT, 26), 0x51, 0x4b, 0x67, (byte) 0xb0       // 1363896240
        };
        Slime slime = decode(data);
        assertEquals(Type.LONG, slime.get().type());
        assertEquals(1363896240L, slime.get().asLong());
    }

    // --- Offset-based decoding ---

    @Test
    public void testDecodingWithOffset() {
        byte[] inner = { initial(UINT, 24), 42 };
        byte[] padded = new byte[inner.length + 3];
        padded[0] = (byte) 0xff; // padding before
        System.arraycopy(inner, 0, padded, 1, inner.length);
        padded[padded.length - 2] = (byte) 0xff; // padding after
        padded[padded.length - 1] = (byte) 0xff;

        Slime slime = CborFormat.decode(padded, 1, inner.length);
        assertEquals(42L, slime.get().asLong());
    }

    // --- Error handling ---

    @Test
    public void testDecodingNonStringMapKey() {
        byte[] data = {
            initial(MAP, 1),    // map of 1
            initial(UINT, 1),   // integer key (invalid for Slime)
            initial(UINT, 2)    // value
        };
        verifyDecodeFails(data);
    }

    @Test
    public void testDecodingTruncatedData() {
        byte[] data = { initial(TSTR, 10), 'h', 'e' }; // claims 10 bytes but only 2
        verifyDecodeFails(data);
    }

    @Test
    public void testDecodingUnsupportedAdditionalInfo() {
        byte[] data = { initial(UINT, 28) }; // additional info 28 is reserved
        verifyDecodeFails(data);
    }
}
