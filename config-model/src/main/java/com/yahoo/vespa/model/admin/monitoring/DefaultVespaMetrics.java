// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin.monitoring;

import com.google.common.collect.ImmutableSet;

import java.util.Set;

/**
 * Encapsulates a minimal set of Vespa metrics to be used as default for all metrics consumers.
 *
 * Note: most predefined metric sets use this as a child, so changing this will require updating
 *       e.g. the list of Vespa metrics in the Datadog integration.
 *
 * @author leandroalves
 */
public class DefaultVespaMetrics {
    public static final MetricSet defaultVespaMetricSet = createDefaultVespaMetricSet();

    private static MetricSet createDefaultVespaMetricSet() {

        Set<Metric> defaultContainerMetrics =
                ImmutableSet.of(new Metric("feed.operations.rate")
                );

        Set<Metric> defaultContentMetrics =
                ImmutableSet.of(new Metric("content.proton.resource_usage.feeding_blocked.last")
                );

        Set<Metric> defaultMetrics = ImmutableSet.<Metric>builder()
                .addAll(defaultContainerMetrics)
                .addAll(defaultContentMetrics)
                .build();

        return new MetricSet("default-vespa", defaultMetrics);
    }
}
