package com.yahoo.vespa.curator.stats;// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import org.junit.Test;

import java.time.Duration;

import static org.junit.Assert.assertEquals;

/**
 * @author hakon
 */
public class AtomicDurationSumTest {
    private final AtomicDurationSum atomicDurationSum = new AtomicDurationSum();

    @Test
    public void test() {
        assertAtomicDurationSum(Duration.ZERO, 0);
        atomicDurationSum.add(Duration.ofMillis(3));
        assertAtomicDurationSum(Duration.ofMillis(3), 1);
        atomicDurationSum.add(Duration.ofMillis(5));
        assertAtomicDurationSum(Duration.ofMillis(8), 2);
        assertEquals(0.004, atomicDurationSum.get().averageDuration().get().toMillis() / 1000., 0.00001);

        DurationSum durationSum = atomicDurationSum.getAndReset();
        assertEquals(Duration.ofMillis(8), durationSum.duration());
        assertEquals(2, durationSum.count());
        assertAtomicDurationSum(Duration.ZERO, 0);
    }

    @Test
    public void testNegatives() {
        atomicDurationSum.add(Duration.ofMillis(-1));
        assertAtomicDurationSum(Duration.ofMillis(-1), 1);
    }

    private void assertAtomicDurationSum(Duration expectedDuration, int expectedCount) {
        DurationSum durationSum = atomicDurationSum.get();
        assertEquals(expectedDuration, durationSum.duration());
        assertEquals(expectedCount, durationSum.count());
    }

    @Test
    public void encoding() {
        assertEquals(40, AtomicDurationSum.DURATION_BITS);
        assertEquals(24, AtomicDurationSum.COUNT_BITS);

        assertEquals(0xFFFFFFFFFF000000L, AtomicDurationSum.DURATION_MASK);
        assertEquals(0x0000000000FFFFFFL, AtomicDurationSum.COUNT_MASK);

        // duration is signed
        assertEquals(0xFFFFFF8000000000L, AtomicDurationSum.MIN_DURATION);
        assertEquals(0x0000007FFFFFFFFFL, AtomicDurationSum.MAX_DURATION);

        // count is unsigned
        assertEquals(0x0000000000000000L, AtomicDurationSum.MIN_COUNT);
        assertEquals(0x0000000000FFFFFFL, AtomicDurationSum.MAX_COUNT);

        assertDurationEncoding(Duration.ZERO);
        assertDurationEncoding(Duration.ofMillis(1));
        assertDurationEncoding(Duration.ofMillis(-1));
        assertDurationEncoding(Duration.ofMillis(AtomicDurationSum.MIN_DURATION));
        assertDurationEncoding(Duration.ofMillis(AtomicDurationSum.MAX_DURATION));

        assertCountEncoding(1L);
        assertCountEncoding(AtomicDurationSum.MIN_COUNT);
        assertCountEncoding(AtomicDurationSum.MAX_COUNT);
        assertEquals(0L, AtomicDurationSum.decodeCount(AtomicDurationSum.MAX_COUNT + 1));
    }

    private void assertDurationEncoding(Duration duration) {
        long encoded = AtomicDurationSum.encodeDuration(duration);
        Duration decodedDuration = AtomicDurationSum.decodeDuration(encoded);
        assertEquals(duration, decodedDuration);
    }

    private void assertCountEncoding(long count) {
        int actualCount = AtomicDurationSum.decodeCount(count);
        assertEquals(count, actualCount);
    }
}