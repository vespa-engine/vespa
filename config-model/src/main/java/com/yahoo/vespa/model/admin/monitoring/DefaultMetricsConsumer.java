// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin.monitoring;

import com.google.common.collect.ImmutableList;

import static com.yahoo.vespa.model.admin.monitoring.SystemMetrics.systemMetricSet;
import static java.util.Collections.emptyList;

/**
 * This class sets up the default 'Vespa' metrics consumer.
 *
 * @author trygve
 * @author gjoranv
 */
public class DefaultMetricsConsumer {

    public static final String VESPA_CONSUMER_ID = "Vespa";

    private static final MetricSet defaultConsumerMetrics = new MetricSet("default-consumer",
                                                                          emptyList(),
                                                                          ImmutableList.of(new VespaMetricSet(),
                                                                                           systemMetricSet));

    @SuppressWarnings("UnusedDeclaration")
    public static MetricsConsumer getDefaultMetricsConsumer() {
        return new MetricsConsumer(VESPA_CONSUMER_ID, defaultConsumerMetrics);
    }

}
