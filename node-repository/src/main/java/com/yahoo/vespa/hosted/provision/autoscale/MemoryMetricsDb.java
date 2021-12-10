// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.collections.Pair;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * An in-memory implementation of the metrics Db.
 * Thread model: One writer, many readers.
 *
 * @author bratseth
 */
public class MemoryMetricsDb implements MetricsDb {

    private final Clock clock;

    /** Metric time series by node (hostname). Each list of metric snapshots is sorted by increasing timestamp */
    private final Map<String, NodeTimeseries> nodeTimeseries = new HashMap<>();

    private final Map<Pair<ApplicationId, ClusterSpec.Id>, ClusterTimeseries> clusterTimeseries = new HashMap<>();

    /** Lock all access for now since we modify lists inside a map */
    private final Object lock = new Object();

    public MemoryMetricsDb(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Clock clock() { return clock; }

    @Override
    public void addNodeMetrics(Collection<Pair<String, NodeMetricSnapshot>> nodeMetrics) {
        synchronized (lock) {
            for (var value : nodeMetrics) {
                add(value.getFirst(), value.getSecond());
            }
        }
    }

    @Override
    public void addClusterMetrics(ApplicationId application, Map<ClusterSpec.Id, ClusterMetricSnapshot> clusterMetrics) {
        synchronized (lock) {
            for (var value : clusterMetrics.entrySet()) {
                add(application, value.getKey(), value.getValue());
            }
        }
    }

    public void clearClusterMetrics(ApplicationId application, ClusterSpec.Id cluster) {
        synchronized (lock) {
            clusterTimeseries.remove(new Pair<>(application, cluster));
        }
    }

    @Override
    public List<NodeTimeseries> getNodeTimeseries(Duration period, Set<String> hostnames) {
        Instant startTime = clock().instant().minus(period);
        synchronized (lock) {
            if (hostnames.isEmpty())
                return nodeTimeseries.values().stream().map(ns -> ns.keepAfter(startTime)).collect(Collectors.toList());
            else
                return hostnames.stream()
                                .map(hostname -> nodeTimeseries.getOrDefault(hostname, new NodeTimeseries(hostname, List.of())).keepAfter(startTime))
                                .collect(Collectors.toList());
        }
    }

    @Override
    public ClusterTimeseries getClusterTimeseries(ApplicationId application, ClusterSpec.Id cluster) {
        return clusterTimeseries.computeIfAbsent(new Pair<>(application, cluster),
                                                 __ -> new ClusterTimeseries(cluster, new ArrayList<>()));
    }

    @Override
    public void gc() {
        synchronized (lock) {
            // Each measurement is Object + long + float = 16 + 8 + 4 = 28 bytes
            // 12 hours with 1k nodes and 3 resources and 1 measurement/sec is about 5Gb
            for (String hostname : nodeTimeseries.keySet()) {
                var timeseries = nodeTimeseries.get(hostname);
                timeseries = timeseries.keepAfter(clock().instant().minus(Autoscaler.maxScalingWindow()));
                if (timeseries.isEmpty())
                    nodeTimeseries.remove(hostname);
                else
                    nodeTimeseries.put(hostname, timeseries);
            }
        }
    }

    @Override
    public void close() {}

    private void add(String hostname, NodeMetricSnapshot snapshot) {
        NodeTimeseries timeseries = nodeTimeseries.get(hostname);
        if (timeseries == null) { // new node
            timeseries = new NodeTimeseries(hostname, new ArrayList<>());
            nodeTimeseries.put(hostname, timeseries);
        }
        nodeTimeseries.put(hostname, timeseries.add(snapshot));
    }

    private void add(ApplicationId application, ClusterSpec.Id cluster, ClusterMetricSnapshot snapshot) {
        var key = new Pair<>(application, cluster);
        var existing = clusterTimeseries.computeIfAbsent(key, __ -> new ClusterTimeseries(cluster, new ArrayList<>()));
        clusterTimeseries.put(key, existing.add(snapshot));
    }

}
