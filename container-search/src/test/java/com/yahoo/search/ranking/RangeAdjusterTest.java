// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.ranking;

import com.google.common.collect.RangeMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RangeAdjusterTest {
    static void rerank(RangeAdjuster adjuster, double before, double after) {
        adjuster.withInitialScore(before);
        adjuster.withFinalScore(after);
    }
    static double adjust(RangeAdjuster adjuster, double score) {
        return score * adjuster.scale() + adjuster.bias();
    }
    @Test void noScoresNoNeed() {
        var adjuster = new RangeAdjuster();
        assertFalse(adjuster.rescaleNeeded());
    }
    @Test void increasingScoresNoNeed() {
        var adjuster = new RangeAdjuster();
        rerank(adjuster, 1.0, 2.0);
        rerank(adjuster, 3.0, 4.0);
        rerank(adjuster, 2.0, 3.0);
        assertFalse(adjuster.rescaleNeeded());
    }
    @Test void singleScoreAdjuster() {
        var adjuster = new RangeAdjuster();
        rerank(adjuster, 10.0, 5.0);
        assertEquals(adjuster.scale(), 1.0);
        assertEquals(adjuster.bias(), -5.0);
        assertEquals(adjust(adjuster, 10.0), 5.0);
        assertEquals(adjust(adjuster, 7.0), 2.0);
    }
    @Test void movingAdjuster() {
        var adjuster = new RangeAdjuster();
        rerank(adjuster, -10.0, -15.0);
        rerank(adjuster, -11.0, -16.0);
        rerank(adjuster, -12.0, -17.0);
        assertEquals(adjuster.scale(), 1.0);
        assertEquals(adjuster.bias(), -5.0);
        assertEquals(adjust(adjuster, -10.0), -15.0);
        assertEquals(adjust(adjuster, -15.0), -20.0);
    }
    @Test void compactingAdjuster() {
        var adjuster = new RangeAdjuster();
        rerank(adjuster, 100.0, 10.0);
        rerank(adjuster, 200.0, 20.0);
        rerank(adjuster, 300.0, 30.0);
        assertEquals(adjuster.scale(), 0.1);
        assertEquals(adjuster.bias(), 0.0);
        assertEquals(adjust(adjuster, 100.0), 10.0);
        assertEquals(adjust(adjuster, 50.0), 5.0);
    }
    @Test void expandingAdjuster() {
        var adjuster = new RangeAdjuster();
        rerank(adjuster, -10.0, -100.0);
        rerank(adjuster, -20.0, -200.0);
        rerank(adjuster, -30.0, -300.0);
        assertEquals(adjuster.scale(), 10.0);
        assertEquals(adjuster.bias(), 0.0);
        assertEquals(adjust(adjuster, -10.0), -100.0);
        assertEquals(adjust(adjuster, -40.0), -400.0);
    }
    // this test represents the normal re-scaling case.
    @Test void compactingAndMovingAdjuster() {
        var adjuster = new RangeAdjuster();
        rerank(adjuster, 1000.0, 1.0);
        rerank(adjuster, 800.0, 0.8);
        rerank(adjuster, 500.0, 0.5);
        assertEquals(adjuster.scale(), 1.0/500.0);
        assertEquals(adjuster.bias(), -0.5);
        assertEquals(adjust(adjuster, 500.0), 0.5);
        assertEquals(adjust(adjuster, 250.0), 0.0);
        assertEquals(adjust(adjuster, 0.0), -0.5);
    }
}