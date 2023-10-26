// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metrics.set;

import ai.vespa.metrics.ContainerMetrics;

/**
 * Defines metric sets that are meant to be used as building blocks for other metric sets.
 *
 * @author gjoranv
 */
public class BasicMetricSets {

    static MetricSet containerHttpStatusMetrics() {
        return new MetricSet.Builder("basic-container-http-status")
                .metric(ContainerMetrics.HTTP_STATUS_1XX.rate())

                .metric(ContainerMetrics.HTTP_STATUS_2XX.rate())
                .metric(ContainerMetrics.HTTP_STATUS_3XX.rate())
                .metric(ContainerMetrics.HTTP_STATUS_4XX.rate())
                .metric(ContainerMetrics.HTTP_STATUS_5XX.rate())
                .build();
    }

}
