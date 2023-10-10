// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BufferTest {

    @org.junit.Test
    public void testEmptyBufferAutoCompact() {
        Buffer buf = new Buffer(1024);
        ByteBuffer b = buf.getWritable(10);
        for (int x = 0; x < 10; x++) {
            b.put((byte)x);
        }
        b = buf.getReadable();
        while (b.remaining() > 0) {
            b.get();
        }
        b = buf.getWritable(10);
        assertEquals(1024, b.remaining());
    }

    @org.junit.Test
    public void testBuffer() {

        int        size = 70*1024;
        Buffer     buf  = new Buffer(1024);
        ByteBuffer b    = null;

        byte[] x = new byte[size];
        byte[] y = new byte[size];

        Arrays.fill(x, (byte) 10);
        Arrays.fill(y, (byte) 55);

        assertEquals(buf.bytes(), 0);
        assertFalse(Arrays.equals(x, y));

        b = buf.getWritable(512);
        assertEquals(buf.bytes(), 0);
        assertTrue(b.remaining() >= 512);
        b.put((byte)42);
        assertEquals(buf.bytes(), 1);

        b =  buf.getReadable();
        assertEquals(buf.bytes(), 1);
        assertEquals(b.remaining(), 1);
        assertEquals(b.get(), 42);
        assertEquals(buf.bytes(), 0);
        assertEquals(b.remaining(), 0);

        b = buf.getWritable(512);
        assertTrue(b.remaining() >= 512);
        assertEquals(buf.bytes(), 0);
        b.put((byte)42);
        assertEquals(buf.bytes(), 1);

        b = buf.getWritable(size);
        assertTrue(b.remaining() >= size);
        assertEquals(buf.bytes(), 1);
        b.put(x);
        assertEquals(buf.bytes(), size + 1);

        b = buf.getReadable();
        assertEquals(buf.bytes(), size + 1);
        assertEquals(b.remaining(), size + 1);
        assertEquals(b.get(), 42);
        assertEquals(buf.bytes(), size);
        assertEquals(b.remaining(), size);
        b.get(y);
        assertEquals(buf.bytes(), 0);
        assertEquals(b.remaining(), 0);
        assertTrue(Arrays.equals(x, y));
    }

    @org.junit.Test
    public void testBufferCompact() {
        Buffer buf = new Buffer(10);
        buf.getWritable(3).put((byte)10).put((byte)20).put((byte)30);
        assertEquals(10, buf.getReadable().capacity());
        buf.getWritable(3).put((byte)11).put((byte)21).put((byte)31);
        buf.getWritable(3).put((byte)12).put((byte)22).put((byte)32);
        {
            ByteBuffer bb = buf.getReadable();
            assertEquals(10, bb.get());
            assertEquals(20, bb.get());
            assertEquals(30, bb.get());
        }
        {
            ByteBuffer bb = buf.getReadable();
            assertEquals(11, bb.get());
            assertEquals(21, bb.get());
            assertEquals(31, bb.get());
        }
        buf.getWritable(3).put((byte)13).put((byte)23).put((byte)33);
        assertEquals(10, buf.getReadable().capacity());
        {
            ByteBuffer bb = buf.getReadable();
            assertEquals(12, bb.get());
            assertEquals(22, bb.get());
            assertEquals(32, bb.get());
        }
        {
            ByteBuffer bb = buf.getReadable();
            assertEquals(13, bb.get());
            assertEquals(23, bb.get());
            assertEquals(33, bb.get());
        }
        {
            ByteBuffer bb = buf.getReadable();
            assertEquals(bb.position(), bb.limit());
        }
    }

    @org.junit.Test
    public void testBufferShrink() {
        Buffer     buf = new Buffer(500);
        ByteBuffer b   = null;
        {
            b = buf.getWritable(10);
            assertEquals(500, b.capacity());
            b.put((byte)10);
            b.put((byte)20);
            b.put((byte)30);
            b.put((byte)40);
            b.put((byte)50);

            assertTrue(buf.shrink(400));
            b = buf.getReadable();
            assertEquals(400, b.capacity());
            assertEquals(5, b.remaining());
            assertEquals(10, b.get());
            assertEquals(20, b.get());
            assertEquals(30, b.get());
            assertEquals(40, b.get());
            assertEquals(50, b.get());
        }
        {
            b = buf.getWritable(10);
            assertEquals(400, b.capacity());
            b.put((byte)10);
            b.put((byte)20);
            b.put((byte)30);
            b.put((byte)40);
            b.put((byte)50);

            assertTrue(buf.shrink(300));
            b = buf.getReadable();
            assertEquals(300, b.capacity());
            assertEquals(5, b.remaining());
            assertEquals(10, b.get());
            assertEquals(20, b.get());
            assertEquals(30, b.get());
            assertEquals(40, b.get());
            assertEquals(50, b.get());
        }
        {
            b = buf.getWritable(10);
            assertEquals(300, b.capacity());
            b.put((byte)10);
            b.put((byte)20);
            b.put((byte)30);
            b.put((byte)40);
            b.put((byte)50);

            b = buf.getReadable();
            assertTrue(buf.shrink(200));
            b = buf.getReadable();
            assertEquals(200, b.capacity());
            assertEquals(5, b.remaining());
            assertEquals(10, b.get());
            assertEquals(20, b.get());
            assertEquals(30, b.get());
            assertEquals(40, b.get());
            assertEquals(50, b.get());
        }
        {
            b = buf.getWritable(10);
            assertEquals(200, b.capacity());
            b.put((byte)10);
            b.put((byte)20);
            b.put((byte)30);
            b.put((byte)40);
            b.put((byte)50);

            b = buf.getReadable();
            assertFalse(buf.shrink(500));
            b = buf.getReadable();
            assertEquals(200, b.capacity());
            assertEquals(5, b.remaining());
            assertEquals(10, b.get());
            assertEquals(20, b.get());
            assertEquals(30, b.get());
            assertEquals(40, b.get());
            assertEquals(50, b.get());
        }
        {
            b = buf.getWritable(10);
            assertEquals(200, b.capacity());
            b.put((byte)10);
            b.put((byte)20);
            b.put((byte)30);
            b.put((byte)40);
            b.put((byte)50);

            b = buf.getReadable();
            assertTrue(buf.shrink(5));
            assertFalse(buf.shrink(4));
            b = buf.getReadable();
            assertEquals(5, b.capacity());
            assertEquals(5, b.remaining());
            assertEquals(10, b.get());
            assertEquals(20, b.get());
            assertEquals(30, b.get());
            assertEquals(40, b.get());
            assertEquals(50, b.get());
        }
    }

}
