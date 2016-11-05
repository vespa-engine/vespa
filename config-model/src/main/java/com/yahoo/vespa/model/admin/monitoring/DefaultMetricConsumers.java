// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin.monitoring;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * This class sets up the default metrics and the default 'vespa' metrics consumer.
 *
 * TODO: remove for Vespa 7 or when the 'metric-consumers' element in 'admin' has been removed.
 *
 * @author <a href="mailto:trygve@yahoo-inc.com">Trygve Bolsø Berdal</a>
 * @author gjoranv
 */
@SuppressWarnings("UnusedDeclaration") // All public apis are used by model amenders
public class DefaultMetricConsumers {

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
        metricsConsumers.put("yamas", getYamasConsumer());
        return metricsConsumers;
    }

    private static MetricsConsumer getYamasConsumer(){
        return new MetricsConsumer("yamas", vespaMetricSet);
    }

}
