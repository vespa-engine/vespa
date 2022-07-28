// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class LeanHitTest {
    private static final byte [] gidA = {'a'};
    private static final byte [] gidB = {'b'};
    private static final byte [] gidC = {'c'};
    private final double DELTA = 0.00000000000000;
    private void verifyTransitiveOrdering(LeanHit a, LeanHit b, LeanHit c) {
        assertTrue(a.compareTo(b) < 0);
        assertTrue(b.compareTo(c) < 0);
        assertTrue(a.compareTo(c) < 0);
        assertTrue(b.compareTo(a) > 0);
        assertTrue(c.compareTo(b) > 0);
        assertTrue(c.compareTo(a) > 0);
    }

    @Test
    void testOrderingByRelevance() {
        assertEquals(0, new LeanHit(gidA, 0, 0, 1).compareTo(new LeanHit(gidA, 0, 0, 1)));
        verifyTransitiveOrdering(new LeanHit(gidA, 0, 0, 1),
                new LeanHit(gidA, 0, 0, 0),
                new LeanHit(gidA, 0, 0, -1));
    }

    @Test
    void testOrderingByGid() {
        assertEquals(0, new LeanHit(gidA, 0, 0, 1).compareTo(new LeanHit(gidA, 0, 0, 1)));

        verifyTransitiveOrdering(new LeanHit(gidA, 0, 0, 1),
                new LeanHit(gidB, 0, 0, 1),
                new LeanHit(gidC, 0, 0, 1));
    }

    @Test
    void testOrderingBySortData() {
        assertEquals(0, new LeanHit(gidA, 0, 0, 0.0, gidA).compareTo(new LeanHit(gidA, 0, 0, 0.0, gidA)));
        verifyTransitiveOrdering(new LeanHit(gidA, 0, 0, 0.0, gidA),
                new LeanHit(gidA, 0, 0, 0.0, gidB),
                new LeanHit(gidA, 0, 0, 0.0, gidC));
    }

    @Test
    void testRelevanceIsKeptEvenWithBySortData() {
        assertEquals(1.3, new LeanHit(gidA, 0, 0, 1.3, gidA).getRelevance(), 0.0);
    }

    @Test
    void testNaN2negativeInfinity() {
        LeanHit nan = new LeanHit(gidA, 0, 0, Double.NaN);
        assertFalse(Double.isNaN(nan.getRelevance()));
        assertTrue(Double.isInfinite(nan.getRelevance()));
        assertEquals(Double.NEGATIVE_INFINITY, nan.getRelevance(), DELTA);
    }
}
