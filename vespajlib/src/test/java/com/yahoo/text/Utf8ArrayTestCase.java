// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text;

import static org.junit.Assert.*;

import java.nio.ByteBuffer;

import org.junit.Before;
import org.junit.Test;

/**
 * Check the Utf8Array API behaves as expected.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class Utf8ArrayTestCase {
    private String raw;
    private byte[] rawBytes;
    private Utf8Array toCheck;

    @Before
    public void setUp() {
        raw = "0123456789";
        rawBytes = Utf8.toBytes(raw);
        toCheck = new Utf8Array(rawBytes);
    }

    @Test
    public final void testGetByteLength() {
        assertEquals(rawBytes.length, toCheck.getByteLength());
    }

    @Test
    public final void testGetBytes() {
        assertSame(rawBytes, toCheck.getBytes());
    }

    @Test
    public final void testGetByteOffset() {
        assertEquals(0, toCheck.getByteOffset());
    }

    @Test
    public final void testUtf8ArrayByteArrayIntInt() {
        Utf8Array otherConstructed = new Utf8Array(rawBytes, 0, rawBytes.length);
        assertNotSame(rawBytes, otherConstructed.getBytes());
        assertArrayEquals(rawBytes, otherConstructed.getBytes());
    }

    @Test
    public final void testUtf8ArrayByteBufferInt() {
        final ByteBuffer wrapper = ByteBuffer.wrap(rawBytes);
        Utf8Array otherConstructed = new Utf8Array(wrapper, wrapper.remaining());
        assertNotSame(rawBytes, otherConstructed.getBytes());
        assertArrayEquals(rawBytes, otherConstructed.getBytes());
    }

    @Test
    public final void testHashCode() {
        Utf8Array other = new Utf8Array(Utf8.toBytes(" a23456789"));
        assertFalse(other.hashCode() == toCheck.hashCode());
    }

    @Test
    public final void testWriteTo() {
        ByteBuffer b = ByteBuffer.allocate(rawBytes.length * 2);
        byte[] copy = new byte[rawBytes.length];
        toCheck.writeTo(b);
        assertEquals(rawBytes.length, b.position());
        b.position(0);
        b.limit(rawBytes.length);
        b.get(copy);
        assertArrayEquals(rawBytes, copy);
    }

    @Test
    public final void testGetByte() {
        assertEquals('8', toCheck.getByte(8));
    }

    @Test
    public final void testWrap() {
        ByteBuffer b1 = toCheck.wrap();
        ByteBuffer b2 = ByteBuffer.wrap(rawBytes);
        byte[] c1 = new byte[b1.remaining()];
        byte[] c2 = new byte[b2.remaining()];
        b1.get(c1);
        b2.get(c2);
        assertArrayEquals(c2, c1);
    }

    @Test
    public final void testIsEmpty() {
        assertFalse(toCheck.isEmpty());
        assertTrue(new Utf8Array(new byte[] {}).isEmpty());
    }

    @Test
    public final void testEqualsObject() {
        assertTrue(toCheck.equals(raw));
        assertFalse(toCheck.equals(new Utf8Array(new byte[] {})));
        assertFalse(toCheck.equals(new Utf8Array(Utf8.toBytes(" " + raw.substring(1)))));
        assertTrue(toCheck.equals(toCheck));
        assertTrue(toCheck.equals(new Utf8Array(rawBytes)));
    }

    @Test
    public final void testToString() {
        assertEquals(raw, toCheck.toString());
    }

    @Test
    public final void testCompareTo() {
        assertTrue(toCheck.compareTo(new Utf8Array(new byte[] {})) > 0);
        assertTrue(toCheck.compareTo(new Utf8Array(Utf8.toBytes(raw + raw))) < 0);
        assertTrue(toCheck.compareTo(new Utf8Array(Utf8.toBytes(" " + raw.substring(1)))) > 0);
        assertTrue(toCheck.compareTo(new Utf8Array(Utf8.toBytes("a" + raw.substring(1)))) < 0);
        assertTrue(toCheck.compareTo(new Utf8Array(rawBytes)) == 0);
    }

    @Test
    public final void testPartial() {
        final int length = 3;
        final int offset = 1;
        Utf8PartialArray partial = new Utf8PartialArray(rawBytes, offset, length);
        assertEquals(length, partial.getByteLength());
        assertEquals(offset, partial.getByteOffset());
        byte[] expected = new byte[length];
        ByteBuffer intermediate = ByteBuffer.allocate(rawBytes.length * 2);
        System.arraycopy(rawBytes, offset, expected, 0, length);
        partial.writeTo(intermediate);
        intermediate.flip();
        byte written[] = new byte[intermediate.remaining()];
        intermediate.get(written);
        assertArrayEquals(expected, written);
    }

    @Test
    public final void testUtf8Strings() {
        String nalle = "nalle";
        Utf8String utf = new Utf8String(new Utf8Array(Utf8.toBytes(nalle)));
        assertEquals('n', utf.charAt(0));
        assertEquals(nalle.length(), utf.length());
        assertEquals("alle", utf.subSequence(1, 5));
        assertTrue(utf.equals(new Utf8String(new Utf8Array(Utf8.toBytes(nalle)))));
        assertTrue(utf.equals(nalle));
    }

    @Test
    public final void testAscii7bitLowercase() {
        final byte [] expected = {
                0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06 ,0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f,
                0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16 ,0x17, 0x18, 0x19, 0x1a, 0x1b, 0x1c, 0x1d, 0x1e, 0x1f,
                0x20, 0x21, 0x22, 0x23, 0x24, 0x25, 0x26 ,0x27, 0x28, 0x29, 0x2a, 0x2b, 0x2c, 0x2d, 0x2e, 0x2f,
                0x30, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36 ,0x37, 0x38, 0x39, 0x3a, 0x3b, 0x3c, 0x3d, 0x3e, 0x3f,
                0x40, 0x61, 0x62, 0x63, 0x64, 0x65, 0x66 ,0x67, 0x68, 0x69, 0x6a, 0x6b, 0x6c, 0x6d, 0x6e, 0x6f,
                0x70, 0x71, 0x72, 0x73, 0x74, 0x75, 0x76 ,0x77, 0x78, 0x79, 0x7a, 0x5b, 0x5c, 0x5d, 0x5e, 0x5f,
                0x60, 0x61, 0x62, 0x63, 0x64, 0x65, 0x66 ,0x67, 0x68, 0x69, 0x6a, 0x6b, 0x6c, 0x6d, 0x6e, 0x6f,
                0x70, 0x71, 0x72, 0x73, 0x74, 0x75, 0x76 ,0x77, 0x78, 0x79, 0x7a, 0x7b, 0x7c, 0x7d, 0x7e, 0x7f
        };
        byte [] org = new byte[128];
        for (byte b = 0; b >= 0; b++) {
            org[b] = b;
        }
        assertArrayEquals(expected, new Utf8Array(org).ascii7BitLowerCase().getBytes());
    }
}
