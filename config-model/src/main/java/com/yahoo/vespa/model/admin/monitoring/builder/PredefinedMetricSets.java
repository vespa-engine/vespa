// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin.monitoring.builder;

import ai.vespa.metrics.set.MetricSet;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static ai.vespa.metrics.set.AutoscalingMetrics.autoscalingMetricSet;
import static ai.vespa.metrics.set.DefaultMetrics.defaultMetricSet;
import static ai.vespa.metrics.set.Vespa9DefaultMetricSet.vespa9defaultMetricSet;
import static ai.vespa.metrics.set.DefaultVespaMetrics.defaultVespaMetricSet;
import static ai.vespa.metrics.set.InfrastructureMetricSet.infrastructureMetricSet;
import static ai.vespa.metrics.set.NetworkMetrics.networkMetricSet;
import static ai.vespa.metrics.set.SystemMetrics.systemMetricSet;
import static ai.vespa.metrics.set.VespaMetricSet.vespaMetricSet;
import static ai.vespa.metrics.set.Vespa9VespaMetricSet.vespa9vespaMetricSet;

/**
 * A data object for predefined metric sets.
 *
 * @author gjoranv
 */
public class PredefinedMetricSets {

    private static final Map<String, MetricSet> sets = toMapById(
            defaultMetricSet,
            vespa9defaultMetricSet,
            defaultVespaMetricSet,
            vespaMetricSet,
            vespa9vespaMetricSet,
            systemMetricSet,
            networkMetricSet,
            autoscalingMetricSet,
            infrastructureMetricSet
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
