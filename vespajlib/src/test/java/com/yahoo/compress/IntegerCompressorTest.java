// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.compress;

import org.junit.Test;

import java.nio.ByteBuffer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Test that integers compresses correctly.
 *
 * @author baldersheim
 */
public class IntegerCompressorTest {
    private void verifyPositiveNumber(int n, byte [] expected) {
        ByteBuffer buf = ByteBuffer.allocate(expected.length);
        IntegerCompressor.putCompressedPositiveNumber(n, buf);
        assertArrayEquals(expected, buf.array());
    }
    private void verifyNumber(int n, byte [] expected) {
        ByteBuffer buf = ByteBuffer.allocate(expected.length);
        IntegerCompressor.putCompressedNumber(n, buf);
        assertArrayEquals(expected, buf.array());
    }

    @Test
    public void requireThatPositiveNumberCompressCorrectly() {
        byte [] zero = {0};
        verifyPositiveNumber(0, zero);
        byte [] one = {0x01};
        verifyPositiveNumber(1, one);
        byte [] x3f = {0x3f};
        verifyPositiveNumber(0x3f, x3f);
        byte [] x40 = {(byte)0x80,0x40};
        verifyPositiveNumber(0x40, x40);
        byte [] x3fff = {(byte)0xbf, (byte)0xff};
        verifyPositiveNumber(0x3fff, x3fff);
        byte [] x4000 = {(byte)0xc0, 0x00, 0x40, 0x00};
        verifyPositiveNumber(0x4000, x4000);
        byte [] x3fffffff = {(byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff};
        verifyPositiveNumber(0x3fffffff, x3fffffff);
        byte [] x40000000 = {0,0,0,0};
        try {
            verifyPositiveNumber(0x40000000, x40000000);
            assertTrue(false);
        } catch (IllegalArgumentException e)  {
            assertEquals("Number '1073741824' too big, must extend encoding", e.getMessage());
        }
        try {
            verifyPositiveNumber(-1, x40000000);
            assertTrue(false);
        } catch (IllegalArgumentException e)  {
            assertEquals("Number '-1' must be positive", e.getMessage());
        }
    }

    @Test
    public void requireThatNumberCompressCorrectly() {
        byte [] zero = {0};
        verifyNumber(0, zero);
        byte [] one = {0x01};
        verifyNumber(1, one);
        byte [] x1f = {0x1f};
        verifyNumber(0x1f, x1f);
        byte [] x20 = {0x40,0x20};
        verifyNumber(0x20, x20);
        byte [] x1fff = {0x5f, (byte)0xff};
        verifyNumber(0x1fff, x1fff);
        byte [] x2000 = {0x60, 0x00, 0x20, 0x00};
        verifyNumber(0x2000, x2000);
        byte [] x1fffffff = {0x7f, (byte)0xff, (byte)0xff, (byte)0xff};
        verifyNumber(0x1fffffff, x1fffffff);
        byte [] x20000000 = {0,0,0,0};
        try {
            verifyNumber(0x20000000, x20000000);
            assertTrue(false);
        } catch (IllegalArgumentException e)  {
            assertEquals("Number '536870912' too big, must extend encoding", e.getMessage());
        }
        byte [] mzero = {(byte)0x81};
        verifyNumber(-1, mzero);
        byte [] mone = {(byte)0x82};
        verifyNumber(-2, mone);
        byte [] mx1f = {(byte)0x9f};
        verifyNumber(-0x1f, mx1f);
        byte [] mx20 = {(byte)0xc0,0x20};
        verifyNumber(-0x20, mx20);
        byte [] mx1fff = {(byte)0xdf, (byte)0xff};
        verifyNumber(-0x1fff, mx1fff);
        byte [] mx2000 = {(byte)0xe0, 0x00, 0x20, 0x00};
        verifyNumber(-0x2000, mx2000);
        byte [] mx1fffffff = {(byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff};
        verifyNumber(-0x1fffffff, mx1fffffff);
        byte [] mx20000000 = {0,0,0,0};
        try {
            verifyNumber(-0x20000000, mx20000000);
            assertTrue(false);
        } catch (IllegalArgumentException e)  {
            assertEquals("Number '-536870912' too big, must extend encoding", e.getMessage());
        }

    }

}
