// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin.monitoring;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * @author gjoranv
 */
public class MetricSetTest {

    @Test
    public void internal_metrics_take_precedence_over_metrics_from_children() {
        String METRIC_NAME = "metric1";
        String COMMON_DIMENSION_KEY = "commonKey";

        Map<String, String> childDimensions = ImmutableMap.<String, String>builder()
                .put(COMMON_DIMENSION_KEY, "childCommonVal")
                .put("childKey", "childVal")
                .build();
        Metric childMetric = new Metric(METRIC_NAME, "child-output-name", "child-description", childDimensions);

        Map<String, String> parentDimensions = ImmutableMap.<String, String>builder()
                .put(COMMON_DIMENSION_KEY, "parentCommonVal")
                .put("parentKey", "parentVal")
                .build();
        Metric parentMetric = new Metric(METRIC_NAME, "parent-output-name", "parent-description", parentDimensions);

        MetricSet child =  new MetricSet("set1", Sets.newHashSet(childMetric));
        MetricSet parent = new MetricSet("set1", Sets.newHashSet(parentMetric), Sets.newHashSet(child));

        Metric combinedMetric = parent.getMetrics().get(METRIC_NAME);
        assertEquals("parent-output-name", combinedMetric.outputName);
        assertEquals("parent-description", combinedMetric.description);
        assertEquals(3, combinedMetric.dimensions.size());
        assertEquals("parentCommonVal", combinedMetric.dimensions.get(COMMON_DIMENSION_KEY));
    }
}
