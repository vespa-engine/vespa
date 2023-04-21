// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class DecodeIndexTest {

    @Test
    public void testSimpleUsage() {
        DecodeIndex index = new DecodeIndex();
        int val1 = index.reserve(1);
        int val2 = index.reserve(3);
        int val3 = index.reserve(2);
        assertEquals(0, val1);
        assertEquals(1, val2);
        assertEquals(4, val3);
        assertEquals(6, index.size());
        index.set(val1 + 0,    0, val2, 0);
        index.set(val2 + 0,  100,    0, 1);
        index.set(val2 + 1,  200, val3, 2);
        index.set(val2 + 2,  300,    0, 3);
        index.set(val3 + 0,  400,    0, 0);
        index.set(val3 + 1,  500,    0, 0);
        for (int i = 0; i < 6; i++) {
            assertEquals(i * 100, index.getByteOffset(i));
            if (i == 0) {
                assertEquals(1, index.getFirstChild(i));
            } else if (i == 2) {
                assertEquals(4, index.getFirstChild(i));
            } else {
                assertEquals(0, index.getFirstChild(i));
            }
            if (i < 4) {
                assertEquals(i, index.getExtBits(i));
            } else {
                assertEquals(0, index.getExtBits(i));
            }
        }
    }

    @Test
    public void testManyValues() {
        DecodeIndex index = new DecodeIndex();
        int outer = 47;
        int inner = 73;
        int expectOffset = 0;
        for (int i = 0; i < outer; i++) {
            int offset = index.reserve(inner);
            assertEquals(expectOffset, offset);
            expectOffset += inner;
            for (int j = 0; j < inner; j++) {
                index.set(offset + j, (i * j), (i + j), (j & 3));
            }
        }
        assertEquals(inner * outer, expectOffset);
        assertEquals(inner * outer, index.size());
        for (int i = 0; i < outer; i++) {
            for (int j = 0; j < inner; j++) {
                int offset = i * inner + j;
                assertEquals(i * j, index.getByteOffset(offset));
                assertEquals(i + j, index.getFirstChild(offset));
                assertEquals(j & 3, index.getExtBits(offset));
            }
        }
    }

    @Test
    public void testOverflowNoBleed() {
        DecodeIndex index = new DecodeIndex();
        index.reserve(3);
        index.set(0, 0xffff_ffff, 0, 0);
        index.set(1, 0, 0xffff_ffff, 0);
        index.set(2, 0, 0, 0xffff_ffff);
        assertEquals(0x7fff_ffff, index.getByteOffset(0));
        assertEquals(0, index.getByteOffset(1));
        assertEquals(0, index.getByteOffset(2));
        assertEquals(0, index.getFirstChild(0));
        assertEquals(0x7fff_ffff, index.getFirstChild(1));
        assertEquals(0, index.getFirstChild(2));
        assertEquals(0, index.getExtBits(0));
        assertEquals(0, index.getExtBits(1));
        assertEquals(3, index.getExtBits(2));
    }
}
