// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.metric;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Once a getter is called, the instance is frozen and no more metrics can be added.
 *
 * @author Unknown
 */
// TODO: remove timestamp, only used as temporary storage.
// TODO: instances of this class can probably be replaced by a simple freezable map.
public class Metrics {

    private final List<Metric> metrics = new ArrayList<>();
    private long timestamp;
    private boolean isFrozen = false;

    public Metrics() {
        this(System.currentTimeMillis() / 1000L);
    }

    public Metrics(long timestamp) {
        this.timestamp = timestamp;
    }

    private void ensureNotFrozen() {
        if (isFrozen) throw new IllegalStateException("Frozen Metrics cannot be modified!");
    }

    public long getTimeStamp() {
        return this.timestamp;
    }

    /**
     * Update the timestamp
     *
     * @param timestamp IN UTC seconds resolution
     */
    public void setTimeStamp(long timestamp) {
        ensureNotFrozen();
        this.timestamp = timestamp;
    }

    public void add(Metric m) {
        ensureNotFrozen();
        this.timestamp = m.getTimeStamp();
        this.metrics.add(m);
    }

    /**
     * Get the size of the metrics covered. Note that this might also contain expired metrics
     *
     * @return size of metrics
     */
    public int size() {
        return this.metrics.size();
    }

    /**
     * TODO: Remove, might be multiple metrics with same name but different dimensions
     *
     * @param key metric name
     * @return the metric, or null
     */
    public Metric getMetric(String key) {
        isFrozen = true;
        for (Metric m: metrics) {
            if (m.getName().equals(key)) {
                return m;
            }
        }
        return null;
    }

    public List<Metric> getMetrics() {
        isFrozen = true;
        return Collections.unmodifiableList(metrics);
    }


    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Metric m : metrics) {
            sb.append(m.getName()).append(":").append(m.getValue()).append("\n");
        }
        return sb.toString();
    }

}
