// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.service;

import ai.vespa.metricsproxy.metric.Metric;
import ai.vespa.metricsproxy.metric.Metrics;

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
    public Metrics getMetrics() {
        Metrics m = new Metrics();

        long timestamp = System.currentTimeMillis() / 1000;
        m.add(new Metric(METRIC_1, 5 * num + 1, timestamp));
        m.add(new Metric(METRIC_2, 1.3 * num + 1.05, timestamp));

        return m;
    }

}
