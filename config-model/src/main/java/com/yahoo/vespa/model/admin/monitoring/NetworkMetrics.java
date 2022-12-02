// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin.monitoring;

import com.google.common.collect.ImmutableSet;

import java.util.Set;

/**
 * @author gjoranv
 */
public class NetworkMetrics {

    public static final MetricSet networkMetricSet = createNetworkMetricSet();

    private static MetricSet createNetworkMetricSet() {
        Set<Metric> dockerNetworkMetrics =
                ImmutableSet.of(new Metric("net.in.bytes"),
                                new Metric("net.in.errors"),
                                new Metric("net.in.dropped"),
                                new Metric("net.out.bytes"),
                                new Metric("net.out.errors"),
                                new Metric("net.out.dropped")
                );

        Set<Metric> networkMetrics = ImmutableSet.<Metric>builder()
                .addAll(dockerNetworkMetrics)
                .build();

        return new MetricSet("network", networkMetrics);
    }
}
