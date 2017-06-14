// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.state;

import com.google.inject.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.container.jdisc.config.HealthMonitorConfig;
import com.yahoo.jdisc.Timer;
import com.yahoo.jdisc.application.MetricConsumer;

import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * A statemonitor keeps track of the current metrics state of a container.
 * It is used by jDisc to hand out metric update API endpoints to workers through {@link #newMetricConsumer},
 * and to inspect the current accumulated state of metrics through {@link #snapshot}.
 *
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen Hult</a>
 */
public class StateMonitor extends AbstractComponent {

    private final static Logger log = Logger.getLogger(StateMonitor.class.getName());
    private final CopyOnWriteArrayList<StateMetricConsumer> consumers = new CopyOnWriteArrayList<>();
    private final Thread thread;
    private final Timer timer;
    private final long snapshotIntervalMs;
    private long lastSnapshotTimeMs;
    private volatile MetricSnapshot snapshot;
    private final TreeSet<String> valueNames = new TreeSet<>();

    @Inject
    public StateMonitor(HealthMonitorConfig config, Timer timer) {
        this.timer = timer;
        this.snapshotIntervalMs = (long)(config.snapshot_interval() * TimeUnit.SECONDS.toMillis(1));
        this.lastSnapshotTimeMs = timer.currentTimeMillis();
        thread = new Thread(new Runnable() {

            @Override
            public void run() {
                StateMonitor.this.run();
            }
        }, "StateMonitor");
        thread.setDaemon(true);
        thread.start();
    }

    /** Returns a metric consumer for jDisc which will write metrics back to this */
    public MetricConsumer newMetricConsumer() {
        StateMetricConsumer consumer = new StateMetricConsumer();
        consumers.add(consumer);
        return consumer;
    }

    /** Returns the last snapshot taken of the metrics in this system */
    public MetricSnapshot snapshot() {
        return snapshot;
    }

    /** Returns the interval between each metrics snapshot used by this */
    public long getSnapshotIntervalMillis() { return snapshotIntervalMs; }

    boolean checkTime() {
        long now = timer.currentTimeMillis();
        if (now < lastSnapshotTimeMs + snapshotIntervalMs) {
            return false;
        }
        snapshot = createSnapshot(lastSnapshotTimeMs, now);
        lastSnapshotTimeMs = now;
        return true;
    }

    private void run() {
        log.finest("StateMonitor started.");
        try {
            while (!Thread.interrupted()) {
                checkTime();
                Thread.sleep((lastSnapshotTimeMs + snapshotIntervalMs) - timer.currentTimeMillis());
            }
        } catch (InterruptedException e) {

        }
        log.finest("StateMonitor stopped.");
    }

    private MetricSnapshot createSnapshot(long fromMillis, long toMillis) {
        MetricSnapshot snapshot = new MetricSnapshot(fromMillis, toMillis, TimeUnit.MILLISECONDS);
        for (StateMetricConsumer consumer : consumers) {
            snapshot.add(consumer.createSnapshot());
        }
        updateNames(snapshot);
        return snapshot;
    }

    private void updateNames(MetricSnapshot current) {
        TreeSet<String> seen = new TreeSet<>();
        for (Map.Entry<MetricDimensions, MetricSet> dimensionAndMetric : current) {
            for (Map.Entry<String, MetricValue> nameAndMetric : dimensionAndMetric.getValue()) {
                seen.add(nameAndMetric.getKey());
            }
        }
        synchronized (valueNames) {
            for (String name : valueNames) {
                if (!seen.contains(name)) {
                    current.add((MetricDimensions) StateMetricConsumer.NULL_CONTEXT, name, 0);
                }
            }
            valueNames.addAll(seen);
        }
    }

    @Override
    public void deconstruct() {
        thread.interrupt();
        try {
            thread.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (thread.isAlive()) {
            log.warning("StateMonitor failed to terminate within 5 seconds of interrupt signal. Ignoring.");
        }
    }
}
