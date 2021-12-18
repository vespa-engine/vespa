// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.metric;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Once a getter is called, the instance is frozen and no more metrics can be added.
 */
// TODO: remove timestamp, only used as temporary storage.
// TODO: instances of this class can probably be replaced by a simple freezable map.
public class Metrics {

    private final List<Metric> metrics = new ArrayList<>();
    private Instant timestamp;
    private boolean isFrozen = false;

    public Metrics() {
        this(Instant.now());
    }

    public Metrics(Instant timestamp) {
        this.timestamp = timestamp;
    }

    private void ensureNotFrozen() {
        if (isFrozen) throw new IllegalStateException("Frozen Metrics cannot be modified!");
    }

    public Instant getTimeStamp() {
        return this.timestamp;
    }

    /**
     * Update the timestamp
     *
     * @param timestamp IN UTC seconds resolution
     */
    public void setTimeStamp(Instant timestamp) {
        ensureNotFrozen();
        this.timestamp = timestamp;
    }

    public void add(Metric m) {
        ensureNotFrozen();
        this.timestamp = m.getTimeStamp();
        this.metrics.add(m);
    }

    /** Returns the size of the metrics covered. Note that this might also contain expired metrics. */
    public int size() {
        return this.metrics.size();
    }

    public List<Metric> list() {
        isFrozen = true;
        return Collections.unmodifiableList(metrics);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Metric m : metrics) {
            sb.append(m.getName()).append(":").append(m.getValue()).append("\n");
        }
        return sb.toString();
    }

}
