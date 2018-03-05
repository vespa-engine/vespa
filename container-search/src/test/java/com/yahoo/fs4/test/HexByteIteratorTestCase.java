// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.fs4.test;

import java.nio.ByteBuffer;

import com.yahoo.fs4.HexByteIterator;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Test of HexByteIterator
 *
 * @author tonytv
 */
public class HexByteIteratorTestCase {

    @Test
    public void testHexByteIterator() {
        int[] numbers = { 0x00, 0x01, 0xDE, 0xAD, 0xBE, 0xEF, 0xFF };

        HexByteIterator i = new HexByteIterator(
                ByteBuffer.wrap(toBytes(numbers)));

        assertEquals("00", i.next());
        assertEquals("01", i.next());
        assertEquals("DE", i.next());
        assertEquals("AD", i.next());
        assertEquals("BE", i.next());
        assertEquals("EF", i.next());
        assertEquals("FF", i.next());
        assertTrue(!i.hasNext());
    }

    private byte[] toBytes(int[] ints) {
        byte[] bytes = new byte[ints.length];
        for (int i=0; i<bytes.length; ++i)
            bytes[i] = (byte)ints[i];
        return bytes;
    }
}
