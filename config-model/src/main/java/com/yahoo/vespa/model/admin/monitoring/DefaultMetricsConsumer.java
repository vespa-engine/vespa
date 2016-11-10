// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin.monitoring;

/**
 * This class sets up the default 'vespa' metrics consumer.
 *
 * @author <a href="mailto:trygve@yahoo-inc.com">Trygve Bolsø Berdal</a>
 * @author gjoranv
 */
public class DefaultMetricsConsumer {

    public static final String VESPA_CONSUMER_ID = "vespa";

    private static final MetricSet vespaMetricSet = new VespaMetricSet();

    @SuppressWarnings("UnusedDeclaration")
    public static MetricsConsumer getDefaultMetricsConsumer() {
        return new MetricsConsumer(VESPA_CONSUMER_ID, vespaMetricSet);
    }

}
