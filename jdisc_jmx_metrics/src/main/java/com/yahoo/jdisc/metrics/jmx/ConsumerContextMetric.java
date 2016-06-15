// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.metrics.jmx;

import com.yahoo.jdisc.metrics.jmx.core.CounterMetricUnit;
import com.yahoo.jdisc.metrics.jmx.core.GaugeMetricUnit;
import com.yahoo.jdisc.metrics.jmx.core.MetricUnit;

import java.util.HashMap;
import java.util.Map;

/**
 * <p>This class contains a map of metrics. It is used by {@link JmxMetricConsumer} to keep track of the metrics
 * for the current thread. There is one instance of this class for each new {@link JmxMetricContext}</p>
 *
 * <p>{@link #snapshot} can be potentially called by another thread which is consuming a snapshot of the data.
 * In so doing, it is possible that either {@link #incrementMetric} or {@link #setMetric} are accessing the {@link MetricUnit}
 * data of the snapshot. If the thread calling {@link #snapshot} starts processing {@link MetricUnit} data at the
 * same time, we might end up in an inconsistent state. The {@link MetricUnit} implementations take care of this case.
 *
 * @author <a href="mailto:alain@yahoo-inc.com">Alain Wan Buen Cheong</a>
 */
class ConsumerContextMetric {

    private volatile Map<String, MetricUnit> metrics = new HashMap<String, MetricUnit>();
    private final int gaugeDepth;

    public ConsumerContextMetric(int gaugeDepth) {
        this.gaugeDepth = gaugeDepth;
    }

    public void incrementMetric(String key, Number value) {
        key.getClass(); // throws NullPointerException
        value.getClass();

        MetricUnit unit = metrics.get(key);
        if (unit == null) {
            metrics.put(key, unit=new CounterMetricUnit());
        }
        unit.addValue(value);
    }

    public void setMetric(String key, Number value) {
        key.getClass(); // throws NullPointerException
        value.getClass();

        MetricUnit unit = metrics.get(key);
        if (unit == null) {
            metrics.put(key, unit=new GaugeMetricUnit(gaugeDepth));
        }
        unit.addValue(value);
    }

    public Map<String, MetricUnit> snapshot() {
        Map<String, MetricUnit> snapshot = metrics;
        metrics = new HashMap<String, MetricUnit>();
        return snapshot;
    }

}
