// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.state;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * A snapshot of the metrics of this system in a particular time interval.
 *
 * @author Simon Thoresen Hult
 */
public final class MetricSnapshot implements Iterable<Map.Entry<MetricDimensions, MetricSet>> {

    private final Map<MetricDimensions, MetricSet> data;
    private final long fromMillis;
    private final long toMillis;

    public MetricSnapshot(long from, long to, TimeUnit unit, Map<MetricDimensions, MetricSet> data) {
        this.fromMillis = unit.toMillis(from);
        this.toMillis = unit.toMillis(to);
        this.data = data;
    }

    MetricSnapshot() {
        this(0, 0, TimeUnit.MILLISECONDS, new HashMap<>());
    }

    MetricSnapshot(long from, long to, TimeUnit unit) {
        this(from, to, unit, new HashMap<>());
    }

    private static MetricSnapshot createWithMetrics(Map<MetricDimensions, MetricSet> data) {
        return new MetricSnapshot(0, 0, TimeUnit.MILLISECONDS, data);
    }

    public long getFromTime(TimeUnit unit) {
        return unit.convert(fromMillis, TimeUnit.MILLISECONDS);
    }

    public long getToTime(TimeUnit unit) {
        return unit.convert(toMillis, TimeUnit.MILLISECONDS);
    }

    /** Returns all the metrics in this snapshot. */
    @Override
    public Iterator<Map.Entry<MetricDimensions, MetricSet>> iterator() {
        return data.entrySet().iterator();
    }

    void add(MetricDimensions dim, String key, Number val) {
        metricSet(dim).add(key, val);
    }

    void set(MetricDimensions dim, String key, Number val) {
        metricSet(dim).set(key, val);
    }

    void add(MetricSnapshot snapshot) {
        for (Map.Entry<MetricDimensions, MetricSet> entry : snapshot) {
            MetricSet metricSet = data.get(entry.getKey());
            if (metricSet != null) {
                metricSet.add(entry.getValue());
            } else {
                data.put(entry.getKey(), entry.getValue());
            }
        }
    }

    /** Returns a metric set from this snapshot for a given set of dimensions */
    public MetricSet metricSet(MetricDimensions dim) {
        MetricSet metricSet = data.get(dim);
        if (metricSet == null) {
            data.put(dim, metricSet = new MetricSet());
        }
        return metricSet;
    }

    /**
     * Create a new snapshot instance where Gauge metrics are preserved
     * with the last-values they have in this snapshot instance.
     */
    public MetricSnapshot createSnapshot() {
        Map<MetricDimensions, MetricSet> newData = new HashMap<>();
        for (Map.Entry<MetricDimensions, MetricSet> entry : data.entrySet()) {
            MetricSet newSet = entry.getValue().partialClone();
            if ( ! newSet.isEmpty()) {
                newData.put(entry.getKey(), newSet);
            }
        }
        return createWithMetrics(newData);
    }

}
