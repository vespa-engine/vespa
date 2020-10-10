// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.state;

import com.google.inject.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.container.jdisc.config.HealthMonitorConfig;
import com.yahoo.jdisc.Timer;
import com.yahoo.jdisc.application.MetricConsumer;
import com.yahoo.jdisc.core.SystemTimer;

import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A state monitor keeps track of the current health and metrics state of a container.
 * It is used by jDisc to hand out metric update API endpoints to workers through {@link #newMetricConsumer},
 * and to inspect the current accumulated state of metrics through {@link #snapshot}.
 *
 * @author Simon Thoresen Hult
 */
public class StateMonitor extends AbstractComponent {

    private final static Logger log = Logger.getLogger(StateMonitor.class.getName());

    public enum Status {up, down, initializing}

    private final CopyOnWriteArrayList<StateMetricConsumer> consumers = new CopyOnWriteArrayList<>();
    private final Optional<ScheduledExecutorService> executor;
    private final Timer timer;
    private final long snapshotIntervalMs;
    private volatile long lastSnapshotTimeMs;
    private volatile MetricSnapshot snapshot;
    private volatile Status status;
    private final TreeSet<String> valueNames = new TreeSet<>();

    @Inject
    public StateMonitor(HealthMonitorConfig config, Timer timer) {
        this(config, timer, runnable -> {
            Thread thread = new Thread(runnable, "StateMonitor");
            thread.setDaemon(true);
            return thread;
        });
    }

    public static StateMonitor createForTesting() {
        return new StateMonitor(new HealthMonitorConfig.Builder().build(), new SystemTimer());
    }

    /** Non-private only for unit testing this class. */
    StateMonitor(HealthMonitorConfig config, Timer timer, ThreadFactory threadFactory) {
        this.timer = timer;
        this.snapshotIntervalMs = (long)(config.snapshot_interval() * TimeUnit.SECONDS.toMillis(1));
        this.lastSnapshotTimeMs = timer.currentTimeMillis();
        this.status = Status.valueOf(config.initialStatus());
        this.executor = Optional.ofNullable(threadFactory).map(Executors::newSingleThreadScheduledExecutor);
        this.executor.ifPresent(exec -> exec.scheduleAtFixedRate(this::updateSnapshot, snapshotIntervalMs,
                snapshotIntervalMs, TimeUnit.MILLISECONDS));
    }

    /** Returns a metric consumer for jDisc which will write metrics back to this */
    public MetricConsumer newMetricConsumer() {
        StateMetricConsumer consumer = new StateMetricConsumer();
        consumers.add(consumer);
        return consumer;
    }

    public void status(Status status) {
        if (status != this.status) {
            log.log(Level.INFO, "Changing health status code from '" + this.status + "' to '" + status.name() + "'");
            this.status = status;
        }
    }

    public Status status() { return status; }

    /** Returns the last snapshot taken of the metrics in this system */
    public MetricSnapshot snapshot() {
        return snapshot;
    }

    /** Returns the interval between each metrics snapshot used by this */
    public long getSnapshotIntervalMillis() { return snapshotIntervalMs; }

    /** NOTE: Non-private for unit testing only. **/
    void updateSnapshot() {
        long now = timer.currentTimeMillis();
        snapshot = createSnapshot(lastSnapshotTimeMs, now);
        lastSnapshotTimeMs = now;
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
        executor.ifPresent(exec -> {
            exec.shutdown();
            try {
                exec.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
            }

            if (!exec.isTerminated()) {
                log.warning("StateMonitor failed to terminate within 5 seconds of interrupt signal. Ignoring.");
            }
        });
    }
}
