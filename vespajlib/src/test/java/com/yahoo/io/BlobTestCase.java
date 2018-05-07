// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.io;

import org.junit.Test;

import java.nio.ByteBuffer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BlobTestCase {

    @Test
    public void testEmpty() {
        Blob empty = new Blob();
        assertTrue(empty.get() != null);
        assertEquals(0, empty.get().length);
    }

    @Test
    public void testCopyArray() {
        byte[] d = { 1, 2, 3 };
        Blob b = new Blob(d);
        d[0] = 7;
        d[1] = 8;
        d[2] = 9;
        assertEquals(3, b.get().length);
        assertEquals(1, b.get()[0]);
        assertEquals(2, b.get()[1]);
        assertEquals(3, b.get()[2]);
    }

    @Test
    public void testCopyArraySubset() {
        byte[] d = { 1, 2, 3 };
        Blob b = new Blob(d, 1, 1);
        d[0] = 7;
        d[1] = 8;
        d[2] = 9;
        assertEquals(1, b.get().length);
        assertEquals(2, b.get()[0]);
    }

    @Test
    public void testCopyBlob() {
        byte[] d = { 1, 2, 3 };
        Blob b = new Blob(d);
        Blob x = new Blob(b);
        b.get()[1] = 4;
        assertEquals(3, x.get().length);
        assertEquals(1, x.get()[0]);
        assertEquals(4, b.get()[1]);
        assertEquals(2, x.get()[1]);
        assertEquals(3, x.get()[2]);
    }

    @Test
    public void testReadBuffer() {
        ByteBuffer buf = ByteBuffer.allocate(100);
        buf.put((byte)1);
        buf.put((byte)2);
        buf.put((byte)3);
        buf.flip();
        assertEquals(3, buf.remaining());
        Blob b = new Blob(buf);
        assertEquals(0, buf.remaining());
        assertEquals(3, b.get().length);
        assertEquals(1, b.get()[0]);
        assertEquals(2, b.get()[1]);
        assertEquals(3, b.get()[2]);
    }

    @Test
    public void testReadPartialBuffer() {
        ByteBuffer buf = ByteBuffer.allocate(100);
        buf.put((byte)1);
        buf.put((byte)2);
        buf.put((byte)3);
        buf.put((byte)4);
        buf.put((byte)5);
        buf.flip();
        assertEquals(5, buf.remaining());
        Blob b = new Blob(buf, 3);
        assertEquals(2, buf.remaining());
        assertEquals(3, b.get().length);
        assertEquals(1, b.get()[0]);
        assertEquals(2, b.get()[1]);
        assertEquals(3, b.get()[2]);
        assertEquals(4, buf.get());
        assertEquals(5, buf.get());
        assertEquals(0, buf.remaining());
    }

    @Test
    public void testWriteBuffer() {
        byte[] d = { 1, 2, 3 };
        Blob b = new Blob(d);
        ByteBuffer buf = ByteBuffer.allocate(100);
        b.write(buf);
        buf.flip();
        assertEquals(3, buf.remaining());
        assertEquals(1, buf.get());
        assertEquals(2, buf.get());
        assertEquals(3, buf.get());
        assertEquals(0, buf.remaining());
    }

}
