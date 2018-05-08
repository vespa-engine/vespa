// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.io;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.InvalidMarkException;
import java.nio.ReadOnlyBufferException;
import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;;

/**
 * Tests GrowableByteBuffer.
 *
 * @author Einar M R Rosenvinge
 */
public class GrowableByteBufferTestCase {

    private static final double delta = 0.00000000001;

    @Test
    public void testBuffer() {
        GrowableByteBuffer buf = new GrowableByteBuffer(20, 1.5f);

        buf.putChar((char) 5);
        assertEquals(2, buf.position());

        buf.putDouble(983.982d);
        assertEquals(10, buf.position());

        buf.putFloat(94.322f);
        assertEquals(14, buf.position());

        buf.putInt(98);
        assertEquals(18, buf.position());

        assertEquals(20, buf.capacity());

        buf.putLong(983L);
        assertEquals(26, buf.position());

        // Adding fudge factors and other fun to the growth rate,
        // makes capacity() suboptimal to test, so this should perhaps
        // be removed
        // TODO: Better test of growth rate
        assertEquals(130, buf.capacity());

        buf.putShort((short) 4);
        assertEquals(28, buf.position());


        buf.position(0);
        assertEquals((char) 5, buf.getChar());
        assertEquals(2, buf.position());

        assertEquals((int) (983.982d * 1000d), (int) (buf.getDouble() * 1000d));
        assertEquals(10, buf.position());

        assertEquals((int) (94.322f * 1000f), (int) (buf.getFloat() * 1000f));
        assertEquals(14, buf.position());

        assertEquals(98, buf.getInt());
        assertEquals(18, buf.position());

        assertEquals(983L, buf.getLong());
        assertEquals(26, buf.position());

        assertEquals((short) 4, buf.getShort());
        assertEquals(28, buf.position());


        byte[] twoBytes = new byte[2];
        buf.put(twoBytes);
        assertEquals(30, buf.position());
        assertEquals(130, buf.capacity());

        buf.put((byte) 1);
        assertEquals(31, buf.position());
        assertEquals(130, buf.capacity());

        ByteBuffer tmpBuf = ByteBuffer.allocate(15);
        tmpBuf.putInt(56);
        tmpBuf.position(0);
        buf.put(tmpBuf);
        assertEquals(46, buf.position());
        assertEquals(130, buf.capacity());
    }

    @Test
    public void testGrowth() {
        GrowableByteBuffer buf = new GrowableByteBuffer(256, 2.0f);

        //add bytes almost to the boundary
        for (int i = 0; i < 255; i++) {
            buf.put((byte) 0);
        }

        //We are just before the boundary now.
        assertEquals(255, buf.position());
        assertEquals(256, buf.capacity());
        assertEquals(256, buf.limit());

        //Test adding one more byte.
        buf.put((byte) 0);
        //The buffer is full.
        assertEquals(256, buf.position());
        assertEquals(256, buf.capacity());
        assertEquals(256, buf.limit());

        //Adding one more byte should make it grow.
        buf.put((byte) 0);
        assertEquals(257, buf.position());
        assertEquals(612, buf.capacity());
        assertEquals(612, buf.limit());

        //add a buffer exactly to the boundary
        byte[] bytes = new byte[355];
        buf.put(bytes);
        assertEquals(612, buf.position());
        assertEquals(612, buf.capacity());
        assertEquals(612, buf.limit());

        //adding a one-byte buffer should make it grow again
        byte[] oneByteBuf = new byte[1];
        buf.put(oneByteBuf);
        assertEquals(613, buf.position());
        assertEquals(1324, buf.capacity());
        assertEquals(1324, buf.limit());

        //add a large buffer that goes waaay past the boundary and makes it grow yet again,
        //but that is not enough
        byte[] largeBuf = new byte[3000];
        buf.put(largeBuf);
        //the buffer should be doubled twice now
        assertEquals(3613, buf.position());
        assertEquals(5596, buf.capacity());
        assertEquals(5596, buf.limit());

        //let's try that again, and make the buffer double three times
        byte[] veryLargeBuf = new byte[20000];
        buf.put(veryLargeBuf);
        //the buffer should be doubled three times now
        assertEquals(23613, buf.position());
        assertEquals(45468, buf.capacity());
        assertEquals(45468, buf.limit());
    }

    @Test
    public void testBadGrowthFactors() {
        try {
            new GrowableByteBuffer(100, 1.0f);
            assertTrue(false);
        } catch (IllegalArgumentException iae) {
            //we're OK
        }
        GrowableByteBuffer buf = new GrowableByteBuffer(16, 1.0000001f);
        buf.putInt(1);
        assertEquals(16, buf.capacity());
        buf.putInt(1);
        assertEquals(16, buf.capacity());
        buf.putInt(1);
        assertEquals(16, buf.capacity());
        buf.putInt(1);
        assertEquals(16, buf.capacity());

        buf.putInt(1);
        assertEquals(116, buf.capacity());

    }

    @Test
    public void testPropertiesNonDirect() {
        GrowableByteBuffer buf = new GrowableByteBuffer(10, 1.5f);
        buf.order(ByteOrder.LITTLE_ENDIAN);

        assertEquals(0, buf.position());
        // GrowableByteBuffer never makes a buffer smaller than 16 bytes
        assertEquals(16, buf.capacity());
        assertEquals(16, buf.limit());
        assertEquals(false, buf.isReadOnly());
        assertEquals(ByteOrder.LITTLE_ENDIAN, buf.order());
        assertEquals(false, buf.isDirect());

        buf.put(new byte[17]);

        assertEquals(17, buf.position());
        assertEquals(124, buf.capacity());
        assertEquals(124, buf.limit());
        assertEquals(false, buf.isReadOnly());
        assertEquals(ByteOrder.LITTLE_ENDIAN, buf.order());
        assertEquals(false, buf.isDirect());
    }

    @Test
    public void testPropertiesDirect() {
        // allocate* are simply encapsulated, so don't add logic to them,
        // therefore minimum size becomes what it says
        GrowableByteBuffer buf = GrowableByteBuffer.allocateDirect(10, 1.5f);
        buf.order(ByteOrder.LITTLE_ENDIAN);

        assertEquals(0, buf.position());
        assertEquals(10, buf.capacity());
        assertEquals(10, buf.limit());
        assertEquals(false, buf.isReadOnly());
        assertEquals(ByteOrder.LITTLE_ENDIAN, buf.order());
        assertEquals(true, buf.isDirect());

        buf.put(new byte[11]);

        assertEquals(11, buf.position());
        assertEquals(115, buf.capacity());
        assertEquals(115, buf.limit());
        assertEquals(false, buf.isReadOnly());
        assertEquals(ByteOrder.LITTLE_ENDIAN, buf.order());
        assertEquals(true, buf.isDirect());
    }

    @Test
    public void testNumberEncodings() {
        GrowableByteBuffer buf = new GrowableByteBuffer();
        buf.putInt1_2_4Bytes(124);
        buf.putInt2_4_8Bytes(124);
        buf.putInt1_4Bytes(124);

        buf.putInt1_2_4Bytes(127);
        buf.putInt2_4_8Bytes(127);
        buf.putInt1_4Bytes(127);

        buf.putInt1_2_4Bytes(128);
        buf.putInt2_4_8Bytes(128);
        buf.putInt1_4Bytes(128);

        buf.putInt1_2_4Bytes(255);
        buf.putInt2_4_8Bytes(255);
        buf.putInt1_4Bytes(255);

        buf.putInt1_2_4Bytes(256);
        buf.putInt2_4_8Bytes(256);
        buf.putInt1_4Bytes(256);

        buf.putInt1_2_4Bytes(0);
        buf.putInt2_4_8Bytes(0);
        buf.putInt1_4Bytes(0);

        buf.putInt1_2_4Bytes(1);
        buf.putInt2_4_8Bytes(1);
        buf.putInt1_4Bytes(1);

        try {
            buf.putInt1_2_4Bytes(Integer.MAX_VALUE);
            fail("Should have gotten exception here...");
        } catch (Exception e) { }
        buf.putInt2_4_8Bytes(Integer.MAX_VALUE);
        buf.putInt1_4Bytes(Integer.MAX_VALUE);

        try {
            buf.putInt2_4_8Bytes(Long.MAX_VALUE);
            fail("Should have gotten exception here...");
        } catch (Exception e) { }

        buf.putInt1_2_4Bytes(Short.MAX_VALUE);
        buf.putInt2_4_8Bytes(Short.MAX_VALUE);
        buf.putInt1_4Bytes(Short.MAX_VALUE);

        buf.putInt1_2_4Bytes(Byte.MAX_VALUE);
        buf.putInt2_4_8Bytes(Byte.MAX_VALUE);
        buf.putInt1_4Bytes(Byte.MAX_VALUE);

        try {
            buf.putInt1_2_4Bytes(-1);
            fail("Should have gotten exception here...");
        } catch (Exception e) { }
        try {
            buf.putInt2_4_8Bytes(-1);
            fail("Should have gotten exception here...");
        } catch (Exception e) { }
        try {
            buf.putInt1_4Bytes(-1);
            fail("Should have gotten exception here...");
        } catch (Exception e) { }

        try {
            buf.putInt1_2_4Bytes(Integer.MIN_VALUE);
            fail("Should have gotten exception here...");
        } catch (Exception e) { }
        try {
            buf.putInt2_4_8Bytes(Integer.MIN_VALUE);
            fail("Should have gotten exception here...");
        } catch (Exception e) { }
        try {
            buf.putInt1_4Bytes(Integer.MIN_VALUE);
            fail("Should have gotten exception here...");
        } catch (Exception e) { }

        try {
            buf.putInt2_4_8Bytes(Long.MIN_VALUE);
            fail("Should have gotten exception here...");
        } catch (Exception e) { }

        int endWritePos = buf.position();
        buf.position(0);

        assertEquals(124, buf.getInt1_2_4Bytes());
        assertEquals(124, buf.getInt2_4_8Bytes());
        assertEquals(124, buf.getInt1_4Bytes());

        assertEquals(127, buf.getInt1_2_4Bytes());
        assertEquals(127, buf.getInt2_4_8Bytes());
        assertEquals(127, buf.getInt1_4Bytes());

        assertEquals(128, buf.getInt1_2_4Bytes());
        assertEquals(128, buf.getInt2_4_8Bytes());
        assertEquals(128, buf.getInt1_4Bytes());

        assertEquals(255, buf.getInt1_2_4Bytes());
        assertEquals(255, buf.getInt2_4_8Bytes());
        assertEquals(255, buf.getInt1_4Bytes());

        assertEquals(256, buf.getInt1_2_4Bytes());
        assertEquals(256, buf.getInt2_4_8Bytes());
        assertEquals(256, buf.getInt1_4Bytes());

        assertEquals(0, buf.getInt1_2_4Bytes());
        assertEquals(0, buf.getInt2_4_8Bytes());
        assertEquals(0, buf.getInt1_4Bytes());

        assertEquals(1, buf.getInt1_2_4Bytes());
        assertEquals(1, buf.getInt2_4_8Bytes());
        assertEquals(1, buf.getInt1_4Bytes());

        assertEquals(Integer.MAX_VALUE, buf.getInt2_4_8Bytes());
        assertEquals(Integer.MAX_VALUE, buf.getInt1_4Bytes());

        assertEquals(Short.MAX_VALUE, buf.getInt1_2_4Bytes());
        assertEquals(Short.MAX_VALUE, buf.getInt2_4_8Bytes());
        assertEquals(Short.MAX_VALUE, buf.getInt1_4Bytes());

        assertEquals(Byte.MAX_VALUE, buf.getInt1_2_4Bytes());
        assertEquals(Byte.MAX_VALUE, buf.getInt2_4_8Bytes());
        assertEquals(Byte.MAX_VALUE, buf.getInt1_4Bytes());

        int endReadPos = buf.position();

        assertEquals(endWritePos, endReadPos);
    }

    @Test
    public void testNumberLengths() {
        assertEquals(1, GrowableByteBuffer.getSerializedSize1_4Bytes(0));
        assertEquals(1, GrowableByteBuffer.getSerializedSize1_4Bytes(1));
        assertEquals(1, GrowableByteBuffer.getSerializedSize1_4Bytes(4));
        assertEquals(1, GrowableByteBuffer.getSerializedSize1_4Bytes(31));
        assertEquals(1, GrowableByteBuffer.getSerializedSize1_4Bytes(126));
        assertEquals(1, GrowableByteBuffer.getSerializedSize1_4Bytes(127));
        assertEquals(4, GrowableByteBuffer.getSerializedSize1_4Bytes(128));
        assertEquals(4, GrowableByteBuffer.getSerializedSize1_4Bytes(129));
        assertEquals(4, GrowableByteBuffer.getSerializedSize1_4Bytes(255));
        assertEquals(4, GrowableByteBuffer.getSerializedSize1_4Bytes(256));
        assertEquals(4, GrowableByteBuffer.getSerializedSize1_4Bytes(0x7FFFFFFF));

        assertEquals(2, GrowableByteBuffer.getSerializedSize2_4_8Bytes(0));
        assertEquals(2, GrowableByteBuffer.getSerializedSize2_4_8Bytes(1));
        assertEquals(2, GrowableByteBuffer.getSerializedSize2_4_8Bytes(4));
        assertEquals(2, GrowableByteBuffer.getSerializedSize2_4_8Bytes(31));
        assertEquals(2, GrowableByteBuffer.getSerializedSize2_4_8Bytes(126));
        assertEquals(2, GrowableByteBuffer.getSerializedSize2_4_8Bytes(127));
        assertEquals(2, GrowableByteBuffer.getSerializedSize2_4_8Bytes(128));
        assertEquals(2, GrowableByteBuffer.getSerializedSize2_4_8Bytes(32767));
        assertEquals(4, GrowableByteBuffer.getSerializedSize2_4_8Bytes(32768));
        assertEquals(4, GrowableByteBuffer.getSerializedSize2_4_8Bytes(32769));
        assertEquals(4, GrowableByteBuffer.getSerializedSize2_4_8Bytes(1030493));
        assertEquals(4, GrowableByteBuffer.getSerializedSize2_4_8Bytes(0x3FFFFFFF));
        assertEquals(8, GrowableByteBuffer.getSerializedSize2_4_8Bytes(0x40000000));
        assertEquals(8, GrowableByteBuffer.getSerializedSize2_4_8Bytes(0x40000001));

        assertEquals(1, GrowableByteBuffer.getSerializedSize1_2_4Bytes(0));
        assertEquals(1, GrowableByteBuffer.getSerializedSize1_2_4Bytes(1));
        assertEquals(1, GrowableByteBuffer.getSerializedSize1_2_4Bytes(4));
        assertEquals(1, GrowableByteBuffer.getSerializedSize1_2_4Bytes(31));
        assertEquals(1, GrowableByteBuffer.getSerializedSize1_2_4Bytes(126));
        assertEquals(1, GrowableByteBuffer.getSerializedSize1_2_4Bytes(127));
        assertEquals(2, GrowableByteBuffer.getSerializedSize1_2_4Bytes(128));
        assertEquals(2, GrowableByteBuffer.getSerializedSize1_2_4Bytes(16383));
        assertEquals(4, GrowableByteBuffer.getSerializedSize1_2_4Bytes(16384));
        assertEquals(4, GrowableByteBuffer.getSerializedSize1_2_4Bytes(16385));
    }

    @Test
    public void testSize0() {
        GrowableByteBuffer buf = new GrowableByteBuffer(0, 2.0f);
        buf.put((byte) 1);
        buf.put((byte) 1);
    }

    @Test
    public void testExceptionSafety() {
        GrowableByteBuffer g = new GrowableByteBuffer(32);
        ByteBuffer b = ByteBuffer.allocate(232);
        for (int i = 0; i < 232; ++i) {
            b.put((byte) 32);
        }
        b.flip();
        g.put(b);
        b.flip();
        g.put(b);
        assertEquals(464, g.position());
        g.flip();
        for (int i = 0; i < 464; ++i) {
            assertEquals(32, (int) g.get());
        }
    }

    @Test
    public void testGrowthFactorAccessor() {
        GrowableByteBuffer g = new GrowableByteBuffer(32);
        assertEquals(GrowableByteBuffer.DEFAULT_GROW_FACTOR, g.getGrowFactor(), delta);
    }

    @Test
    public void testGrowthWithNonZeroMark() {
        GrowableByteBuffer g = new GrowableByteBuffer(32);
        final int mark = 16;
        byte[] stuff = new byte[mark];
        Arrays.fill(stuff, (byte) 37);
        g.put(stuff);
        g.mark();
        stuff = new byte[637];
        Arrays.fill(stuff, (byte) 38);
        g.put(stuff);
        assertEquals(mark, g.getByteBuffer().reset().position());
    }

    @Test
    public void testPutInt2_4_8BytesMore() {
        GrowableByteBuffer g = new GrowableByteBuffer(32);
        g.putInt2_4_8Bytes(0x9000);
        assertEquals(4, g.position());
    }

    @Test
    public void testPutInt2_4_8BytesAs4() {
        GrowableByteBuffer g = new GrowableByteBuffer(32);
        boolean caught = false;
        try {
            g.putInt2_4_8BytesAs4(-1);
        } catch (IllegalArgumentException e) {
            caught = true;
        }
        assertTrue(caught);
        caught = false;
        try {
            g.putInt2_4_8BytesAs4(1L << 37);
        } catch (IllegalArgumentException e) {
            caught = true;
        }
        assertTrue(caught);
        g.putInt2_4_8BytesAs4(37);
        assertEquals(4, g.position());
    }

    @Test
    public void testGetInt2_4_8Bytes() {
        GrowableByteBuffer g = new GrowableByteBuffer(32);
        final long expected3 = 37L;
        g.putInt2_4_8Bytes(expected3);
        final long expected2 = 0x9000L;
        g.putInt2_4_8Bytes(expected2);
        final long expected = 1L << 56;
        g.putInt2_4_8Bytes(expected);
        g.flip();
        assertEquals(expected3, g.getInt2_4_8Bytes());
        assertEquals(expected2, g.getInt2_4_8Bytes());
        assertEquals(expected, g.getInt2_4_8Bytes());
    }

    @Test
    public void testSerializedSize2_4_8BytesIllegalValues() {
        boolean caught = false;
        try {
            GrowableByteBuffer.getSerializedSize2_4_8Bytes(-1);
        } catch (IllegalArgumentException e) {
            caught = true;
        }
        assertTrue(caught);
        caught = false;
        try {
            GrowableByteBuffer.getSerializedSize2_4_8Bytes((1L << 62) + 1L);
        } catch (IllegalArgumentException e) {
            caught = true;
        }
        assertTrue(caught);
    }

    @Test
    public void testPutInt1_2_4BytesAs4IllegalValues() {
        GrowableByteBuffer g = new GrowableByteBuffer(32);
        boolean caught = false;
        try {
            g.putInt1_2_4BytesAs4(-1);
        } catch (IllegalArgumentException e) {
            caught = true;
        }
        assertTrue(caught);
        caught = false;
        try {
            g.putInt1_2_4BytesAs4((1 << 30) + 1);
        } catch (IllegalArgumentException e) {
            caught = true;
        }
        assertTrue(caught);
    }

    @Test
    public void testSerializedSize1_2_4BytesIllegalValues() {
        boolean caught = false;
        try {
            GrowableByteBuffer.getSerializedSize1_2_4Bytes(-1);
        } catch (IllegalArgumentException e) {
            caught = true;
        }
        assertTrue(caught);
        caught = false;
        try {
            GrowableByteBuffer.getSerializedSize1_2_4Bytes((1 << 30) + 1);
        } catch (IllegalArgumentException e) {
            caught = true;
        }
        assertTrue(caught);
    }

    @Test
    public void testPutInt1_4BytesAs4() {
        GrowableByteBuffer g = new GrowableByteBuffer(32);
        boolean caught = false;
        try {
            g.putInt1_4BytesAs4(-1);
        } catch (IllegalArgumentException e) {
            caught = true;
        }
        assertTrue(caught);
        g.putInt1_4BytesAs4(37);
        assertEquals(4, g.position());
    }

    @Test
    public void testSerializedSize1_4BytesIllegalValues() {
        boolean caught = false;
        try {
            GrowableByteBuffer.getSerializedSize1_4Bytes(-1);
        } catch (IllegalArgumentException e) {
            caught = true;
        }
        assertTrue(caught);
    }

    @Test
    public void testBuilders() {
        GrowableByteBuffer g = GrowableByteBuffer.allocate(1063);
        assertEquals(1063, g.capacity());
        g = GrowableByteBuffer.allocate(1063, 37.0f);
        assertEquals(1063, g.capacity());
        assertEquals(37.0f, g.getGrowFactor(), delta);
        g = GrowableByteBuffer.allocateDirect(1063);
        assertTrue(g.isDirect());
    }

    @Test
    public void testForwarding() {
        GrowableByteBuffer g = new GrowableByteBuffer(1063);
        int first = g.arrayOffset();
        g.put(0, (byte) 37);
        assertTrue(g.hasArray());
        assertEquals((byte) 37, g.array()[first]);
        g.putChar(0, 'a');
        assertEquals('a', g.getChar(0));
        assertEquals('a', g.asCharBuffer().get(0));
        g.putDouble(0, 10.0d);
        assertEquals(10.0d, g.getDouble(0), delta);
        assertEquals(10.0d, g.asDoubleBuffer().get(0), delta);
        g.putFloat(0, 10.0f);
        assertEquals(10.0f, g.getFloat(0), delta);
        assertEquals(10.0f, g.asFloatBuffer().get(0), delta);
        g.putInt(0, 10);
        assertEquals(10, g.getInt(0));
        assertEquals(10, g.asIntBuffer().get(0));
        g.putLong(0, 10L);
        assertEquals(10L, g.getLong(0));
        assertEquals(10L, g.asLongBuffer().get(0));
        boolean caught = false;
        try {
            g.asReadOnlyBuffer().put((byte) 10);
        } catch (ReadOnlyBufferException e) {
            caught = true;
        }
        assertTrue(caught);
        g.putShort(0, (short) 10);
        assertEquals((short) 10, g.getShort(0));
        assertEquals((short) 10, g.asShortBuffer().get(0));
        g.position(0);
        g.put((byte) 0);
        g.put((byte) 10);
        g.limit(2);
        g.position(1);
        g.compact();
        assertEquals((byte) 10, g.get(0));
    }

    @Test
    public void testComparison() {
        GrowableByteBuffer g0 = new GrowableByteBuffer(32);
        GrowableByteBuffer g1 = new GrowableByteBuffer(32);
        assertEquals(g0.hashCode(), g1.hashCode());
        assertFalse(g0.equals(Integer.valueOf(12)));
        assertFalse(g0.hashCode() == new GrowableByteBuffer(1063).hashCode());
        assertTrue(g0.equals(g1));
        assertEquals(0, g0.compareTo(g1));
        g0.put((byte) 9);
        assertFalse(g0.equals(g1));
        assertEquals(-1, g0.compareTo(g1));
    }

    @Test
    public void testDuplicate() {
        GrowableByteBuffer g0 = new GrowableByteBuffer(32);
        GrowableByteBuffer g1 = g0.duplicate();
        g0.put((byte) 12);
        assertEquals(12, g1.get());
    }

    @Test
    public void testGetByteArrayOffsetLen() {
        GrowableByteBuffer g = new GrowableByteBuffer(32);
        byte[] expected = new byte[] { (byte) 1, (byte) 2, (byte) 3 };
        for (int i = 0; i < expected.length; ++i) {
            g.put(expected[i]);
        }
        byte[] got = new byte[3];
        g.flip();
        g.get(got, 0, got.length);
        assertArrayEquals(expected, got);
    }

    @Test
    public void testPutByteArrayOffsetLen() {
        GrowableByteBuffer g = new GrowableByteBuffer(32);
        byte[] expected = new byte[] { (byte) 1, (byte) 2, (byte) 3 };
        g.put(expected, 0, expected.length);
        byte[] got = new byte[3];
        g.flip();
        g.get(got, 0, got.length);
        assertArrayEquals(expected, got);
    }

    @Test
    public void testPutGrowableBuffer() {
        GrowableByteBuffer g0 = new GrowableByteBuffer(32);
        byte[] expected = new byte[] { (byte) 1, (byte) 2, (byte) 3 };
        GrowableByteBuffer g1 = new GrowableByteBuffer(32);
        g0.put(expected, 0, expected.length);
        byte[] got = new byte[3];
        g0.flip();
        g1.put(g0);
        g1.flip();
        g1.get(got, 0, got.length);
        assertArrayEquals(expected, got);
    }

    private GrowableByteBuffer fullBuffer() {
        GrowableByteBuffer g = new GrowableByteBuffer(32);
        byte[] stuffer = new byte[g.remaining()];
        Arrays.fill(stuffer, (byte) 'a');
        g.put(stuffer);
        return g;
    }

    @Test
    public void testPutWithGrow() {
        GrowableByteBuffer g = fullBuffer();
        final int capacity = g.capacity();
        byte[] b = new byte[] { (byte) 'b' };
        g.put(b, 0, b.length);
        assertTrue(capacity < g.capacity());

        g = fullBuffer();
        GrowableByteBuffer toPut = fullBuffer();
        toPut.flip();
        g.put(toPut);

        assertTrue(capacity < g.capacity());
        g = fullBuffer();
        g.put(g.position(), (byte) 'b');
        assertTrue(capacity < g.capacity());

        g = fullBuffer();
        g.putChar('b');
        assertTrue(capacity < g.capacity());
        g = fullBuffer();
        g.putChar(g.position(), 'b');
        assertTrue(capacity < g.capacity());

        g = fullBuffer();
        g.putDouble(1.0d);
        assertTrue(capacity < g.capacity());
        g = fullBuffer();
        g.putDouble(g.position(), 1.0d);
        assertTrue(capacity < g.capacity());

        g = fullBuffer();
        g.putFloat(1.0f);
        assertTrue(capacity < g.capacity());
        g = fullBuffer();
        g.putFloat(g.position(), 1.0f);
        assertTrue(capacity < g.capacity());

        g = fullBuffer();
        g.putInt(g.position(), 1);
        assertTrue(capacity < g.capacity());

        g = fullBuffer();
        g.putLong(g.position(), 1L);
        assertTrue(capacity < g.capacity());

        g = fullBuffer();
        g.putShort((short) 1);
        assertTrue(capacity < g.capacity());
        g = fullBuffer();
        g.putShort(g.position(), (short) 1);
        assertTrue(capacity < g.capacity());
    }

    @Test
    public void testSlice() {
        GrowableByteBuffer g0 = new GrowableByteBuffer(32);
        GrowableByteBuffer g1 = g0.slice();
        final int expected = 37;
        g0.putInt(expected);
        assertEquals(expected, g1.getInt());
    }

    @Test
    public void testToString() {
        assertEquals("GrowableByteBuffer[pos=32 lim=32 cap=32 grow=2.0]",
                     fullBuffer().toString());
    }

    @Test
    public void testWrappers() {
        final byte expected = (byte) 2;
        byte[] data = new byte[] { (byte) 1, expected, (byte) 3 };
        final float grow = 9e5f;
        GrowableByteBuffer g = GrowableByteBuffer.wrap(data, grow);
        assertEquals(expected, g.get(1));
        assertEquals(grow, g.getGrowFactor(), delta);
        g = GrowableByteBuffer.wrap(data, 1, 1);
        assertEquals(expected, g.get());
        assertEquals(2, g.limit());
        g = GrowableByteBuffer.wrap(data, 1, 1, grow);
        assertEquals(expected, g.get());
        assertEquals(2, g.limit());
        assertEquals(grow, g.getGrowFactor(), delta);
    }

    @Test
    public void testByteBufferMethods() {
        GrowableByteBuffer g = fullBuffer();
        assertFalse(g.hasRemaining());
        g.clear();
        assertTrue(g.hasRemaining());
        g = fullBuffer();
        g.mark();
        g.limit(16);
        boolean caught = false;
        try {
            g.reset();
        } catch (InvalidMarkException e) {
            caught = true;
        }
        assertTrue(caught);
        caught = false;
        g = fullBuffer();
        g.mark();
        g.position(16);
        try {
            g.reset();
        } catch (InvalidMarkException e) {
            caught = true;
        }
        assertTrue(caught);
    }

}
