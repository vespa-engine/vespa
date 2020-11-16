// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin.monitoring;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Metrics used for autoscaling
 *
 * @author bratseth
 */
public class AutoscalingMetrics {

    public static final MetricSet autoscalingMetricSet = create();

    private static MetricSet create() {
        return new MetricSet("autoscaling",
                             metrics("cpu.util",
                                     "mem_total.util",
                                     "disk.util",
                                     "application_generation",
                                     "in_rotation"));
    }

    private static Set<Metric> metrics(String ... metrics) {
        return Arrays.stream(metrics).map(Metric::new).collect(Collectors.toSet());
    }

}
