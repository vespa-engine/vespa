// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.service;

import ai.vespa.metricsproxy.metric.Metric;
import ai.vespa.metricsproxy.metric.model.MetricId;

/**
 * @author Unknown
 */
public class DummyService extends VespaService {
    public static final String NAME = "dummy";
    public static final String METRIC_1 = "c.test";
    public static final String METRIC_2 = "val";

    private final int num;

    public DummyService(int num, String configid) {
        super(NAME, NAME + num, configid);
        this.num = num;
    }

    @Override
    public void consumeMetrics(MetricsParser.Consumer consumer) {
        long timestamp = System.currentTimeMillis() / 1000;
        consumer.consume(new Metric(MetricId.toMetricId(METRIC_1), 5 * num + 1, timestamp));
        consumer.consume(new Metric(MetricId.toMetricId(METRIC_2), 1.3 * num + 1.05, timestamp));
    }

}
