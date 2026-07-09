// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch;

import com.yahoo.search.dispatch.searchcluster.DocumentCount;
import com.yahoo.search.result.Coverage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author boeker
 */
public class CoverageAggregatorTest {
    private MockTimeoutHandler timeoutHandler;
    private CoverageAggregator aggregator;

    @BeforeEach
    public void setUp() {
        timeoutHandler = new MockTimeoutHandler();
        aggregator = new CoverageAggregator(2);
        aggregator.add(new Coverage(10_000L, 10_000L, 1));
    }

    @Test
    void coverageIsAdjusted() {
        Coverage coverage = aggregator.adjustedDegradedCoverage(1, timeoutHandler, new DocumentCount()).createCoverage(timeoutHandler);
        assertEquals(10_000L, coverage.getDocs());
        assertEquals(20_000L, coverage.getActive());
        assertEquals(20_000L, coverage.getTargetActive());
    }

    @Test
    void adjustedCoverageIsNotClampedAtDocumentCountWhenNotReliable() {
        DocumentCount count = new DocumentCount(10_000L, 10_000L, false);
        Coverage coverage = aggregator.adjustedDegradedCoverage(1, timeoutHandler, count).createCoverage(timeoutHandler);
        assertEquals(10_000L, coverage.getDocs());
        assertEquals(20_000L, coverage.getActive());
        assertEquals(20_000L, coverage.getTargetActive());
    }

    @Test
    void adjustedCoverageIsClampedAtDocumentCount() {
        DocumentCount count = new DocumentCount(10_000L, 10_000L, true);
        Coverage coverage = aggregator.adjustedDegradedCoverage(1, timeoutHandler, count).createCoverage(timeoutHandler);
        assertEquals(10_000L, coverage.getDocs());
        assertEquals(10_000L, coverage.getActive());
        assertEquals(10_000L, coverage.getTargetActive());
    }

    @Test
    void adjustedCoverageIsClampedAtDocumentCountWithDifferentTarget() {
        DocumentCount count = new DocumentCount(12_345L, 16_789L, true);
        Coverage coverage = aggregator.adjustedDegradedCoverage(1, timeoutHandler, count).createCoverage(timeoutHandler);
        assertEquals(10_000L, coverage.getDocs());
        assertEquals(12_345L, coverage.getActive());
        assertEquals(16_789L, coverage.getTargetActive());
    }

    @Test
    void adjustedCoverageIsNotClampedWhenOneCountIsZero() {
        DocumentCount count = new DocumentCount(0L, 20_000L, true);
        Coverage coverage = aggregator.adjustedDegradedCoverage(1, timeoutHandler, count).createCoverage(timeoutHandler);
        assertEquals(10_000L, coverage.getDocs());
        assertEquals(20_000L, coverage.getActive());
        assertEquals(20_000L, coverage.getTargetActive());

        count = new DocumentCount(20_000L, 0L, true);
        coverage = aggregator.adjustedDegradedCoverage(1, timeoutHandler, count).createCoverage(timeoutHandler);
        assertEquals(10_000L, coverage.getDocs());
        assertEquals(20_000L, coverage.getActive());
        assertEquals(20_000L, coverage.getTargetActive());
    }
}

class MockTimeoutHandler implements TimeoutHandler {
    @Override
    public long nextTimeoutMS(int answeredNodes) {
        return 1000L;
    }

    @Override
    public int reason() {
        return 0;
    }
}
