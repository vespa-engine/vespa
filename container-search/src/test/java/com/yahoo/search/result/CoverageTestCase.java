// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.result;

import com.yahoo.search.Query;
import com.yahoo.search.Result;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author Steinar Knutsen
 */
public class CoverageTestCase {

    @Test
    void testZeroCoverage() {
        Coverage c = new Coverage(0L, 0, 0, 0);
        assertEquals(0, c.getResultPercentage());
        assertEquals(0, c.getResultSets());
    }

    @Test
    void testActiveCoverage() {
        Coverage c = new Coverage(6, 5);
        assertEquals(5, c.getActive());
        assertEquals(6, c.getDocs());

        Coverage d = new Coverage(7, 6);
        c.merge(d);
        assertEquals(11, c.getActive());
        assertEquals(13, c.getDocs());
    }

    @Test
    void testCoverageBasedOnTargetActive() {
        var c = new Coverage(8, 10).setTargetActive(16);
        assertEquals(50, c.getResultPercentage());
    }

    @Test
    void testDefaultCoverage() {
        boolean create = true;

        Result r1 = new Result(new Query());
        assertEquals(0, r1.getCoverage(create).getResultSets());
        Result r2 = new Result(new Query());

        r1.mergeWith(r2);
        assertEquals(0, r1.getCoverage(create).getResultSets());
    }

    @Test
    void testDefaultSearchScenario() {
        boolean create = true;

        Result federationSearcherResult = new Result(new Query());
        Result singleSourceResult = new Result(new Query());
        federationSearcherResult.mergeWith(singleSourceResult);
        assertNull(federationSearcherResult.getCoverage(!create));
        assertEquals(0, federationSearcherResult.getCoverage(create).getResultSets());
    }

    @Test
    void testRequestingCoverageSearchScenario() {
        boolean create = true;

        Result federationSearcherResult = new Result(new Query());
        Result singleSourceResult = new Result(new Query());
        singleSourceResult.setCoverage(new Coverage(10, 1));
        federationSearcherResult.mergeWith(singleSourceResult);
        assertEquals(1, federationSearcherResult.getCoverage(create).getResultSets());
    }

    void verifyCoverageConversion(com.yahoo.container.handler.Coverage c) {
        com.yahoo.container.logging.Coverage lc = c.toLoggingCoverage();
        assertEquals(lc.getDocs(), c.getDocs());
        assertEquals(lc.getActive(), c.getActive());
        assertEquals(lc.getTargetActive(), c.getTargetActive());
        assertEquals(lc.getResultPercentage(), c.getResultPercentage());
        assertEquals(lc.isDegraded(), c.isDegraded());
        assertEquals(lc.isDegradedByNonIdealState(), c.isDegradedByNonIdealState());
        assertEquals(lc.isDegradedByAdapativeTimeout(), c.isDegradedByAdapativeTimeout());
        assertEquals(lc.isDegradedByMatchPhase(), c.isDegradedByMatchPhase());
        assertEquals(lc.isDegradedByTimeout(), c.isDegradedByTimeout());
    }

    @Test
    void testCoverageConversion() {
        verifyCoverageConversion(new Coverage(6, 10).setDegradedReason(7).setTargetActive(12));
    }

}
