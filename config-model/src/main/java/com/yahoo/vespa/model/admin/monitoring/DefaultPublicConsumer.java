// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.model.admin.monitoring;

import ai.vespa.metricsproxy.http.ValuesFetcher;
import com.google.common.collect.ImmutableList;

import static com.yahoo.vespa.model.admin.monitoring.DefaultPublicMetrics.defaultPublicMetricSet;
import static com.yahoo.vespa.model.admin.monitoring.DefaultVespaMetrics.defaultVespaMetricSet;
import static com.yahoo.vespa.model.admin.monitoring.SystemMetrics.systemMetricSet;
import static java.util.Collections.emptyList;

/**
 * @author gjoranv
 */
public class DefaultPublicConsumer {

    public static final String DEFAULT_PUBLIC_CONSUMER_ID = ValuesFetcher.DEFAULT_PUBLIC_CONSUMER_ID.id;

    private static final MetricSet publicConsumerMetrics = new MetricSet("public-consumer-metrics",
                                                                         emptyList(),
                                                                         ImmutableList.of(defaultPublicMetricSet,
                                                                                          systemMetricSet));

    public static MetricsConsumer getDefaultPublicConsumer() {
        return new MetricsConsumer(DEFAULT_PUBLIC_CONSUMER_ID, publicConsumerMetrics);
    }

}
