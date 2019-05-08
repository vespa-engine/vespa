// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin.monitoring;

import ai.vespa.metricsproxy.core.VespaMetrics;
import com.google.common.collect.ImmutableList;

import static com.yahoo.vespa.model.admin.monitoring.NetworkMetrics.networkMetricSet;
import static com.yahoo.vespa.model.admin.monitoring.SystemMetrics.systemMetricSet;
import static com.yahoo.vespa.model.admin.monitoring.VespaMetricSet.vespaMetricSet;
import static java.util.Collections.emptyList;

/**
 * This class sets up the default 'Vespa' metrics consumer.
 *
 * @author trygve
 * @author gjoranv
 */
public class DefaultMetricsConsumer {

    public static final String VESPA_CONSUMER_ID = VespaMetrics.VESPA_CONSUMER_ID.id;

    private static final MetricSet defaultConsumerMetrics = new MetricSet("default-consumer",
                                                                          emptyList(),
                                                                          ImmutableList.of(vespaMetricSet,
                                                                                           systemMetricSet,
                                                                                           networkMetricSet));

    @SuppressWarnings("UnusedDeclaration")
    public static MetricsConsumer getDefaultMetricsConsumer() {
        return new MetricsConsumer(VESPA_CONSUMER_ID, defaultConsumerMetrics);
    }

}
