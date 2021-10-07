// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin.monitoring.builder;

import com.yahoo.vespa.model.admin.monitoring.MetricSet;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.yahoo.vespa.model.admin.monitoring.AutoscalingMetrics.autoscalingMetricSet;
import static com.yahoo.vespa.model.admin.monitoring.DefaultMetrics.defaultMetricSet;
import static com.yahoo.vespa.model.admin.monitoring.NetworkMetrics.networkMetricSet;
import static com.yahoo.vespa.model.admin.monitoring.SystemMetrics.systemMetricSet;
import static com.yahoo.vespa.model.admin.monitoring.DefaultVespaMetrics.defaultVespaMetricSet;
import static com.yahoo.vespa.model.admin.monitoring.VespaMetricSet.vespaMetricSet;

/**
 * A data object for predefined metric sets.
 *
 * @author gjoranv
 */
public class PredefinedMetricSets {

    private static final Map<String, MetricSet> sets = toMapById(
            defaultMetricSet,
            defaultVespaMetricSet,
            vespaMetricSet,
            systemMetricSet,
            networkMetricSet,
            autoscalingMetricSet
    );

    public static Map<String, MetricSet> get() { return sets; }

    private static Map<String, MetricSet> toMapById(MetricSet... metricSets) {
        Map<String, MetricSet> availableMetricSets = new LinkedHashMap<>();
        for (MetricSet metricSet : metricSets) {
            var existing = availableMetricSets.put(metricSet.getId(), metricSet);
            if (existing != null)
                throw new IllegalArgumentException("There are two predefined metric sets with id " + existing.getId());
        }
        return Collections.unmodifiableMap(availableMetricSets);
    }

}
