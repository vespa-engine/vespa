// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.metrics.simple;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import com.yahoo.collections.LazyMap;
import com.yahoo.collections.LazySet;
import java.util.logging.Level;

/**
 * An aggregation of data which is only written to from a single thread.
 *
 * @author Steinar Knutsen
 */
public class Bucket {

    private static final Logger log = Logger.getLogger(Bucket.class.getName());
    private final Map<Identifier, UntypedMetric> values = LazyMap.newHashMap();

    boolean gotTimeStamps;
    long fromMillis;
    long toMillis;

    public Bucket() {
        this.gotTimeStamps = false;
        this.fromMillis = 0;
        this.toMillis = 0;
    }

    public Bucket(long fromMillis, long toMillis) {
        this.gotTimeStamps = true;
        this.fromMillis = fromMillis;
        this.toMillis = toMillis;
    }

    public Set<Map.Entry<Identifier, UntypedMetric>> entrySet() {
        return values.entrySet();
    }

    void put(Sample x) {
        UntypedMetric value = get(x);
        Measurement m = x.getMeasurement();
        switch (x.getMetricType()) {
            case GAUGE:
                value.put(m.getMagnitude());
                break;
            case COUNTER:
                value.add(m.getMagnitude());
                break;
            default:
                throw new IllegalArgumentException("Unsupported metric type: " + x.getMetricType());
        }
    }

    void put(Identifier id, UntypedMetric value) {
        values.put(id, value);
    }

    boolean hasIdentifier(Identifier id) {
        return values.containsKey(id);
    }

    void merge(Bucket other, boolean otherIsNewer) {
        LazySet<String> malformedMetrics = LazySet.newHashSet();
        for (Map.Entry<Identifier, UntypedMetric> entry : other.values.entrySet()) {
            String metricName = entry.getKey().getName();
            try {
                if (!malformedMetrics.contains(metricName)) {
                    get(entry.getKey(), entry.getValue()).merge(entry.getValue(), otherIsNewer);
                }
            } catch (IllegalArgumentException e) {
                log.log(Level.WARNING, "Problems merging metric " + metricName + ", possibly ignoring data.");
                // avoid spamming the log if there are a lot of mismatching
                // threads
                malformedMetrics.add(metricName);
            }
        }
    }

    void merge(Bucket other) {
        boolean otherIsNewer = resolveTimeStamps(other);
        merge(other, otherIsNewer);
    }

    private boolean resolveTimeStamps(Bucket other) {
        boolean otherIsNewer = other.fromMillis > this.fromMillis;
        if (! gotTimeStamps) {
            fromMillis = other.fromMillis;
            toMillis = other.toMillis;
            gotTimeStamps = other.gotTimeStamps;
        } else if (other.gotTimeStamps) {
            fromMillis = Math.min(fromMillis, other.fromMillis);
            toMillis = Math.max(toMillis, other.toMillis);
        }
        return otherIsNewer;
    }

    private UntypedMetric get(Sample sample) {
        Identifier dim = sample.getIdentifier();
        UntypedMetric v = values.get(dim);

        if (v == null) {
            // please keep inside guard, as sample.getHistogramDefinition(String) touches a volatile
            v = new UntypedMetric(sample.getHistogramDefinition(dim.getName()));
            values.put(dim, v);
        }
        return v;
    }

    private UntypedMetric get(Identifier dim, UntypedMetric other) {
        UntypedMetric v = values.get(dim);

        if (v == null) {
            v = new UntypedMetric(other.getMetricDefinition());
            values.put(dim, v);
        }
        return v;
    }

    public Collection<String> getAllMetricNames() {
        Set<String> names = new HashSet<>();
        for (Identifier id : values.keySet()) {
            names.add(id.getName());
        }
        return names;
    }

    public Collection<Map.Entry<Point, UntypedMetric>> getValuesForMetric(String metricName) {
        List<Map.Entry<Point, UntypedMetric>> singleMetric = new ArrayList<>();
        for (Map.Entry<Identifier, UntypedMetric> entry : values.entrySet()) {
            if (metricName.equals(entry.getKey().getName())) {
                singleMetric.add(locationValuePair(entry));
            }
        }
        return singleMetric;
    }

    public Map<Point, UntypedMetric> getMapForMetric(String metricName) {
        Map<Point, UntypedMetric> result = new HashMap<>();
        for (Map.Entry<Identifier, UntypedMetric> entry : values.entrySet()) {
            if (metricName.equals(entry.getKey().getName())) {
                result.put(entry.getKey().getLocation(), entry.getValue());
            }
        }
        return result;
    }

    public Map<String, List<Map.Entry<Point, UntypedMetric>>> getValuesByMetricName() {
        Map<String, List<Map.Entry<Point, UntypedMetric>>> result = new HashMap<>();
        for (Map.Entry<Identifier, UntypedMetric> entry : values.entrySet()) {
            List<Map.Entry<Point, UntypedMetric>> singleMetric;
            if (result.containsKey(entry.getKey().getName())) {
                singleMetric = result.get(entry.getKey().getName());
            } else {
                singleMetric = new ArrayList<>();
                result.put(entry.getKey().getName(), singleMetric);
            }
            singleMetric.add(locationValuePair(entry));
        }
        return result;
    }

    private SimpleImmutableEntry<Point, UntypedMetric> locationValuePair(Map.Entry<Identifier, UntypedMetric> entry) {
        return new SimpleImmutableEntry<>(entry.getKey().getLocation(), entry.getValue());
    }

    @Override
    public String toString() {
        return "Bucket [values=" + toString(values.entrySet(), 3) + "]";
    }

    private String toString(Collection<?> collection, int maxLen) {
        StringBuilder builder = new StringBuilder();
        builder.append("[");
        int i = 0;
        for (Iterator<?> iterator = collection.iterator(); iterator.hasNext() && i < maxLen; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(iterator.next());
        }
        builder.append("]");
        return builder.toString();
    }

    /**
     * This bucket contains data newer than approximately this point in time.
     */
    public long getFromMillis() {
        return fromMillis;
    }

    /**
     * This bucket contains data older than approximately this point in time.
     */
    public long getToMillis() {
        return toMillis;
    }

}
