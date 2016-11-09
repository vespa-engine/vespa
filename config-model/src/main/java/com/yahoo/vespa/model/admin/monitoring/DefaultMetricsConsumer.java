// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin.monitoring;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * This class sets up the default 'vespa' metrics consumer.
 *
 * @author <a href="mailto:trygve@yahoo-inc.com">Trygve Bolsø Berdal</a>
 * @author gjoranv
 */
public class DefaultMetricsConsumer {

    public static final String VESPA_CONSUMER_ID = "vespa";

    private static final MetricSet vespaMetricSet = new VespaMetricSet();

    /**
     * Populates a map of with consumer as key and metrics for that consumer as value. The metrics
     * are to be forwarded to consumers.
     *
     * @return A map of default metric consumers and default metrics for that consumer.
     */
    @SuppressWarnings("UnusedDeclaration")
    public static Map<String, MetricsConsumer> getDefaultMetricsConsumers() {
        Map<String, MetricsConsumer> metricsConsumers = new LinkedHashMap<>();
        metricsConsumers.put(VESPA_CONSUMER_ID, new MetricsConsumer(VESPA_CONSUMER_ID, vespaMetricSet));
        return metricsConsumers;
    }

}
