// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin.monitoring;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author gjoranv
 */
public class MetricTest {

    @Test
    void this_metric_takes_precedence_when_combined_with_another_metric() {
        String COMMON_DIMENSION_KEY = "commonKey";

        Map<String, String> thisDimensions = ImmutableMap.<String, String>builder()
                .put(COMMON_DIMENSION_KEY, "thisCommonVal")
                .put("thisKey", "thisVal")
                .build();
        Metric thisMetric = new Metric("thisMetric", "this-output-name", "this-description", thisDimensions);

        Map<String, String> thatDimensions = ImmutableMap.<String, String>builder()
                .put(COMMON_DIMENSION_KEY, "thatCommonVal")
                .put("thatKey", "thatVal")
                .build();
        Metric thatMetric = new Metric("thatMetric", "that-output-name", "that-description", thatDimensions);

        Metric combinedMetric = thisMetric.addDimensionsFrom(thatMetric);
        assertEquals("this-output-name", combinedMetric.outputName);
        assertEquals("this-description", combinedMetric.description);
        assertEquals(3, combinedMetric.dimensions.size());
        assertEquals("thisCommonVal", combinedMetric.dimensions.get(COMMON_DIMENSION_KEY));
    }
}
