// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.state;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * A set of metrics.
 *
 * @author Simon Thoresen Hult
 */
public final class MetricSet implements Iterable<Map.Entry<String, MetricValue>> {

    private final static Logger log = Logger.getLogger(MetricSet.class.getName());

    private final Map<String, MetricValue> data;

    MetricSet() {
        data = new HashMap<>();
    }

    public MetricSet(Map<String, MetricValue> data) {
        this.data = data;
    }

    /** Returns all metrics in this */
    @Override
    public Iterator<Map.Entry<String, MetricValue>> iterator() {
        return data.entrySet().iterator();
    }

    /** Returns a metric value */
    public MetricValue get(String key) {
        return data.get(key);
    }

    void add(String key, Number val) {
        add(key, CountMetric.newSingleValue(val));
    }

    void set(String key, Number val) {
        add(key, GaugeMetric.newSingleValue(val));
    }

    void add(MetricSet metricSet) {
        for (Map.Entry<String, MetricValue> entry : metricSet) {
            add(entry.getKey(), entry.getValue());
        }
    }

    private void add(String key, MetricValue value) {
        MetricValue existingValue = data.get(key);

        if (existingValue == null) {
            data.put(key, value);
            return;
        }

        if ( ! existingValue.getClass().isAssignableFrom(value.getClass())) {
            log.info("Resetting metric '" + key + "' as it changed type. " +
                     "If you see this outside of deployment changes it means you incorrectly call both set() and add() " +
                     "on the same metric");
            data.put(key, value);
            return;
        }

        existingValue.add(value);
    }

    boolean isEmpty() { return data.isEmpty(); }

    /**
     * Create and return a MetricSet which carries over the last values
     * set for gauges in the this MetricSet. Aggregate metrics are currently
     * not carried over and will not be present in the returned set.
     */
    public MetricSet partialClone() {
        return new MetricSet(
                data.entrySet().stream()
                .filter(kv -> kv.getValue() instanceof GaugeMetric)
                .collect(Collectors.toMap(
                        kv -> kv.getKey(),
                        kv -> ((GaugeMetric)kv.getValue()).newWithPreservedLastValue())));
    }

}
