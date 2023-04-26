// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static com.yahoo.slime.BinaryView.byte_offset_for_testing;
import static com.yahoo.slime.BinaryView.first_child_for_testing;
import static com.yahoo.slime.BinaryView.ext_bits_for_testing;

public class DecodeIndexTest {

    int checkCapacity(DecodeIndex index, int oldCapacity) {
        int capacity = index.capacity();
        if (oldCapacity == -1) {
            System.out.println("DecodeIndex initial capacity " + capacity);
        } else if (capacity != oldCapacity) {
            System.out.println("DecodeIndex capacity increased to " + capacity);
        }
        return capacity;
    }

    @Test
    public void testSimpleUsage() {
        DecodeIndex index = new DecodeIndex(100, 10);
        assertEquals(1, index.size());
        int capacity = checkCapacity(index, -1);
        int root = 0;
        capacity = checkCapacity(index, capacity);
        int val2 = index.tryReserveChildren(3, 1, 15);
        capacity = checkCapacity(index, capacity);
        int val3 = index.tryReserveChildren(2, 2, 20);
        capacity = checkCapacity(index, capacity);
        assertEquals(1, val2);
        assertEquals(4, val3);
        assertEquals(6, index.size());
        index.set(root,        0, val2, 0);
        index.set(val2 + 0,  100,    0, 1);
        index.set(val2 + 1,  200, val3, 2);
        index.set(val2 + 2,  300,    0, 3);
        index.set(val3 + 0,  400,    0, 0);
        index.set(val3 + 1,  500,    0, 0);
        for (int i = 0; i < 6; i++) {
            assertEquals(i * 100, byte_offset_for_testing(index, i));
            if (i == 0) {
                assertEquals(1, first_child_for_testing(index, i));
            } else if (i == 2) {
                assertEquals(4, first_child_for_testing(index, i));
            } else {
                assertEquals(0, first_child_for_testing(index, i));
            }
            if (i < 4) {
                assertEquals(i, ext_bits_for_testing(index, i));
            } else {
                assertEquals(0, ext_bits_for_testing(index, i));
            }
        }
    }

    @Test
    public void testManyValues() {
        int outer = 47;
        int inner = 73;
        int symSize = 128;
        int bytesPerValue = 5;
        DecodeIndex index = new DecodeIndex(symSize + inner * outer * bytesPerValue, symSize);
        int capacity = checkCapacity(index, -1);
        int indexOffset = 1;
        int binaryOffset = symSize + bytesPerValue;
        int expectOffset = 1;
        for (int i = 0; i < outer; i++) {
            int offset = index.tryReserveChildren(inner, indexOffset, binaryOffset);
            capacity = checkCapacity(index, capacity);
            assertEquals(expectOffset, offset);
            expectOffset += inner;
            for (int j = 0; j < inner; j++) {
                index.set(offset + j, (i * j), (i + j), (j & 3));
                ++indexOffset;
                binaryOffset += bytesPerValue;
            }
        }
        assertEquals(1 + inner * outer, expectOffset);
        assertEquals(1 + inner * outer, index.size());
        for (int i = 0; i < outer; i++) {
            for (int j = 0; j < inner; j++) {
                int offset = 1 + i * inner + j;
                assertEquals(i * j, byte_offset_for_testing(index, offset));
                assertEquals(i + j, first_child_for_testing(index, offset));
                assertEquals(j & 3, ext_bits_for_testing(index, offset));
            }
        }
    }

    @Test
    public void testOverflowNoBleed() {
        DecodeIndex index = new DecodeIndex(100, 10);
        index.tryReserveChildren(2, 1, 20);
        assertEquals(3, index.size());
        index.set(0, 0xffff_ffff, 0, 0);
        index.set(1, 0, 0xffff_ffff, 0);
        index.set(2, 0, 0, 0xffff_ffff);
        assertEquals(0x7fff_ffff, byte_offset_for_testing(index, 0));
        assertEquals(0, byte_offset_for_testing(index, 1));
        assertEquals(0, byte_offset_for_testing(index, 2));
        assertEquals(0, first_child_for_testing(index, 0));
        assertEquals(0x7fff_ffff, first_child_for_testing(index, 1));
        assertEquals(0, first_child_for_testing(index, 2));
        assertEquals(0, ext_bits_for_testing(index, 0));
        assertEquals(0, ext_bits_for_testing(index, 1));
        assertEquals(3, ext_bits_for_testing(index, 2));
    }

    @Test
    public void testMinimalInitialCapacity() {
        DecodeIndex index = new DecodeIndex(2, 1);
        assertEquals(16, index.capacity());
    }

    @Test
    public void testInitialCapacityEstimate() {
        DecodeIndex index = new DecodeIndex((33 * 24) + 167, 167);
        assertEquals(33, index.capacity());
    }

    void assertWithinRange(int low, int high, int actual) {
        if (actual >= low && actual <= high) {
            System.out.println("value " + actual + " in range [" + low + "," + high + "]");
        } else {
            fail("value " + actual + " not in range [" + low + "," + high + "]");
        }
    }

    void assertGreater(int limit, int actual) {
        if (actual > limit) {
            System.out.println("value " + actual + " is greater than " + limit);
        } else {
            fail("value " + actual + " is not greater than " + limit);
        }
    }

    void assertLess(int limit, int actual) {
        if (actual < limit) {
            System.out.println("value " + actual + " is less than " + limit);
        } else {
            fail("value " + actual + " is not less than " + limit);
        }
    }

    DecodeIndex prepareIndex(int symSize, int numValues) {
        DecodeIndex index = new DecodeIndex((numValues * 24) + symSize, symSize);
        assertEquals(1, index.tryReserveChildren(numValues - 1, 1, symSize + 24));
        assertEquals(numValues, index.size());
        assertEquals(numValues, index.capacity());
        return index;
    }

    @Test
    public void testDensityBasedCapacityEstimate() {
        var index = prepareIndex(167, 33);
        double exp = 1.25 * index.capacity();
        assertEquals(33, index.tryReserveChildren(10, 20, 167 + (20 * 4)));
        int doneCnt = 20;
        double bytesPerObject = 4.0;
        int pendingData = (33 * 24) - (20 * 4);
        double est = (doneCnt + pendingData / bytesPerObject);
        int maxSize = doneCnt + pendingData;
        assertGreater((int)(exp * 1.05), index.capacity());
        assertWithinRange((int)(1.05 * est), (int)(1.15 * est), index.capacity());
        assertLess(maxSize, index.capacity());
    }

    @Test
    public void testExpCapacityGrowth() {
        var index = prepareIndex(167, 33);
        double exp = 1.25 * index.capacity();
        assertEquals(33, index.tryReserveChildren(1, 20, 167 + (20 * 32)));
        int doneCnt = 20;
        double bytesPerObject = 32.0;
        int pendingData = (33 * 24) - (20 * 32);
        double est = (doneCnt + pendingData / bytesPerObject);
        int maxSize = doneCnt + pendingData;
        assertWithinRange((int)(0.95 * exp), (int)(1.05 * exp), index.capacity());
        assertGreater((int)(est * 1.15), index.capacity());
        assertLess(maxSize, index.capacity());
    }

    @Test
    public void testMinCapacityGrowth() {
        var index = prepareIndex(167, 33);
        double exp = 1.25 * index.capacity();
        assertEquals(33, index.tryReserveChildren(20, 20, 167 + (20 * 32)));
        int doneCnt = 20;
        double bytesPerObject = 32.0;
        int pendingData = (33 * 24) - (20 * 32);
        double est = (doneCnt + pendingData / bytesPerObject);
        int maxSize = doneCnt + pendingData;
        assertGreater((int)(exp * 1.05), index.capacity());
        assertGreater((int)(est * 1.15), index.capacity());
        assertEquals(33 + 20, index.capacity());
        assertLess(maxSize, index.capacity());
    }

    @Test
    public void testMaxCapacityGrowth() {
        var index = prepareIndex(167, 33);
        double exp = 1.25 * index.capacity();
        assertEquals(33, index.tryReserveChildren(1, 32, 167 + (33 * 24) - 3));
        int minSize = 33 + 1;
        int maxSize = 32 + 3;
        assertLess((int)(exp * 0.95), index.capacity());
        assertGreater(minSize, index.capacity());
        assertEquals(maxSize, index.capacity());
    }

    @Test
    public void testMinMaxCapacityGrowth() {
        var index = prepareIndex(167, 33);
        assertEquals(-1, index.tryReserveChildren(5, 32, 167 + (33 * 24) - 3));
        assertEquals(33, index.capacity());
    }

    @Test
    public void testExpNanCapacityGrowth() {
        var index = prepareIndex(167, 33);
        double exp = 1.25 * index.capacity();
        assertEquals(33, index.tryReserveChildren(1, 0, 167));
        assertWithinRange((int)(0.95 * exp), (int)(1.05 * exp), index.capacity());
    }

    @Test
    public void testMaxInfCapacityGrowth() {
        var index = prepareIndex(167, 17);
        double exp = 1.25 * index.capacity();
        assertEquals(17, index.tryReserveChildren(1, 10, 167));
        int maxSize = 10 + (17 * 24);
        assertEquals(maxSize, index.capacity());
    }
}
