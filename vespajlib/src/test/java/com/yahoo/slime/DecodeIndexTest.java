// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;

public class DecodeIndexTest {

    @Test
    public void testSimpleUsage() {
        DecodeIndex index = new DecodeIndex();
        int val1 = index.reserve(1);
        int val2 = index.reserve(3);
        int val3 = index.reserve(2);
        assertThat(val1, is(0));
        assertThat(val2, is(1));
        assertThat(val3, is(4));
        assertThat(index.size(), is(6));
        index.set(val1 + 0,    0, val2, 0);
        index.set(val2 + 0,  100,    0, 1);
        index.set(val2 + 1,  200, val3, 2);
        index.set(val2 + 2,  300,    0, 3);
        index.set(val3 + 0,  400,    0, 0);
        index.set(val3 + 1,  500,    0, 0);
        for (int i = 0; i < 6; i++) {
            assertThat(index.getByteOffset(i), is(i * 100));
            if (i == 0) {
                assertThat(index.getFirstChild(i), is(1));
            } else if (i == 2) {
                assertThat(index.getFirstChild(i), is(4));
            } else {
                assertThat(index.getFirstChild(i), is(0));
            }
            if (i < 4) {
                assertThat(index.getExtBits(i), is(i));
            } else {
                assertThat(index.getExtBits(i), is(0));
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
            assertThat(offset, is(expectOffset));
            expectOffset += inner;
            for (int j = 0; j < inner; j++) {
                index.set(offset + j, (i * j), (i + j), (j & 3));
            }
        }
        assertThat(expectOffset, is(inner * outer));
        assertThat(index.size(), is(inner * outer));
        for (int i = 0; i < outer; i++) {
            for (int j = 0; j < inner; j++) {
                int offset = i * inner + j;
                assertThat(index.getByteOffset(offset), is(i * j));
                assertThat(index.getFirstChild(offset), is(i + j));
                assertThat(index.getExtBits(offset), is(j & 3));
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
        assertThat(index.getByteOffset(0), is(0x7fff_ffff));
        assertThat(index.getByteOffset(1), is(0));
        assertThat(index.getByteOffset(2), is(0));
        assertThat(index.getFirstChild(0), is(0));
        assertThat(index.getFirstChild(1), is(0x7fff_ffff));
        assertThat(index.getFirstChild(2), is(0));
        assertThat(index.getExtBits(0), is(0));
        assertThat(index.getExtBits(1), is(0));
        assertThat(index.getExtBits(2), is(3));
    }
}
