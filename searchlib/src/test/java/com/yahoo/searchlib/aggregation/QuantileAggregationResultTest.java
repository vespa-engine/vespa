// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.aggregation;

import com.yahoo.data.disclosure.slime.SlimeDataSink;
import com.yahoo.slime.Slime;
import org.junit.Test;
import com.yahoo.slime.SlimeUtils;

import java.util.List;

import static com.yahoo.slime.SlimeUtils.toJson;
import static org.junit.Assert.assertTrue;

/**
 * @author johsol
 */
public class QuantileAggregationResultTest {

    private void assertSlime(Slime expected, Slime actual) {
        assertTrue("Expected " + toJson(expected) + " but got " + toJson(actual),
                expected.get().equalTo(actual.get()));
    }

    @Test
    public void testQuantileResultDataSourceEmitsCorrectly() {
        QuantileAggregationResult quantiles = new QuantileAggregationResult();
        quantiles.updateSketch(1);
        quantiles.updateSketch(1);
        quantiles.updateSketch(2);
        quantiles.updateSketch(2);
        quantiles.updateSketch(3);
        quantiles.setQuantiles(List.of(0.0, 0.5, 1.0));

        Slime output = SlimeDataSink.buildSlime(quantiles.getQuantileResults());
        assertSlime(SlimeUtils.jsonToSlime("[{quantile: 0.0, value: 1.0}," +
                                           " {quantile: 0.5, value: 2.0}," +
                                           " {quantile: 1.0, value: 3.0}]"), output);
    }

}
