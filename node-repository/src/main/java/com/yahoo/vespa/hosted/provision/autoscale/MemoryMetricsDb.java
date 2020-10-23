// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.collections.Pair;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * An in-memory implementation of the metrics Db.
 * Thread model: One writer, many readers.
 *
 * @author bratseth
 */
public class MemoryMetricsDb implements MetricsDb {

    private final NodeRepository nodeRepository;

    /** Metric time seriest by node (hostname). Each list of metric snapshots is sorted by increasing timestamp */
    private final Map<String, NodeTimeseries> db = new HashMap<>();

    /** Lock all access for now since we modify lists inside a map */
    private final Object lock = new Object();

    public MemoryMetricsDb(NodeRepository nodeRepository) {
        this.nodeRepository = nodeRepository;
    }

    @Override
    public void add(Collection<Pair<String, MetricSnapshot>> nodeMetrics) {
        synchronized (lock) {
            for (var value : nodeMetrics) {
                add(value.getFirst(), value.getSecond());
            }
        }
    }

    @Override
    public List<NodeTimeseries> getNodeTimeseries(Instant startTime, Set<String> hostnames) {
        synchronized (lock) {
            return hostnames.stream()
                            .map(hostname -> db.getOrDefault(hostname, new NodeTimeseries(hostname, List.of())).justAfter(startTime))
                            .collect(Collectors.toList());
        }
    }

    @Override
    public void gc() {
        synchronized (lock) {
            // Each measurement is Object + long + float = 16 + 8 + 4 = 28 bytes
            // 12 hours with 1k nodes and 3 resources and 1 measurement/sec is about 5Gb
            for (String hostname : db.keySet()) {
                var timeseries = db.get(hostname);
                timeseries = timeseries.justAfter(nodeRepository.clock().instant().minus(Autoscaler.maxScalingWindow()));
                if (timeseries.isEmpty())
                    db.remove(hostname);
                else
                    db.put(hostname, timeseries);
            }
        }
    }

    @Override
    public void close() {}

    private void add(String hostname, MetricSnapshot snapshot) {
        NodeTimeseries timeseries = db.get(hostname);
        if (timeseries == null) { // new node
            Optional<Node> node = nodeRepository.getNode(hostname);
            if (node.isEmpty()) return;
            if (node.get().allocation().isEmpty()) return;
            timeseries = new NodeTimeseries(hostname, new ArrayList<>());
            db.put(hostname, timeseries);
        }
        db.put(hostname, timeseries.add(snapshot));
    }

}
