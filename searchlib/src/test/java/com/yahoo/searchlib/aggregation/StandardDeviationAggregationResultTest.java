// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.aggregation;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author bjorncs
 */
public class StandardDeviationAggregationResultTest {

    @Test
    public void rank_is_standard_deviation() {
        StandardDeviationAggregationResult aggregationResult =
                new StandardDeviationAggregationResult(3, 131.875, 10595.8);
        double rank = aggregationResult.getRank().getFloat();
        assertEquals(aggregationResult.getStandardDeviation(), rank, 0);
        assertEquals(40, rank, 0.1);
    }

}
