// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.collections.Pair;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * An in-memory time-series "database" of node metrics.
 * Thread model: One writer, many readers.
 *
 * @author bratseth
 */
public class NodeMetricsDb {

    private final NodeRepository nodeRepository;

    /** Metric time seriest by node (hostname). Each list of metric snapshots is sorted by increasing timestamp */
    private final Map<String, NodeTimeseries> db = new HashMap<>();

    /** Lock all access for now since we modify lists inside a map */
    private final Object lock = new Object();

    public NodeMetricsDb(NodeRepository nodeRepository) {
        this.nodeRepository = nodeRepository;
    }

    /** Adds snapshots to this. */
    public void add(Collection<Pair<String, MetricSnapshot>> nodeMetrics) {
        synchronized (lock) {
            for (var value : nodeMetrics) {
                add(value.getFirst(), value.getSecond());
            }
        }
    }

    private void add(String hostname, MetricSnapshot snapshot) {
        NodeTimeseries timeseries = db.get(hostname);
        if (timeseries == null) { // new node
            Optional<Node> node = nodeRepository.getNode(hostname);
            if (node.isEmpty()) return;
            if (node.get().allocation().isEmpty()) return;
            timeseries = new NodeTimeseries(hostname,
                                              node.get().allocation().get().membership().cluster().type(),
                                              new ArrayList<>());
            db.put(hostname, timeseries);
        }
        db.put(hostname, timeseries.add(snapshot));
    }

    /** Must be called intermittently (as long as any add methods are called) to gc old data */
    public void gc(Clock clock) {
        synchronized (lock) {
            // Each measurement is Object + long + float = 16 + 8 + 4 = 28 bytes
            // 12 hours with 1k nodes and 3 resources and 1 measurement/sec is about 5Gb
            for (String hostname : db.keySet()) {
                var timeseries = db.get(hostname);
                timeseries = timeseries.justAfter(clock.instant().minus(Autoscaler.scalingWindow(timeseries.type())));
                if (timeseries.isEmpty())
                    db.remove(hostname);
                else
                    db.put(hostname, timeseries);
            }
        }
    }

    /**
     * Returns a list of measurements with one entry for each of the given host names
     * which have any values after startTime, in the same order
     */
    public List<NodeTimeseries> getNodeTimeseries(Instant startTime, List<String> hostnames) {
        synchronized (lock) {
            List<NodeTimeseries> measurementsList = new ArrayList<>(hostnames.size());
            for (String hostname : hostnames) {
                NodeTimeseries measurements = db.get(hostname);
                if (measurements == null) continue;
                measurements = measurements.justAfter(startTime);
                if (measurements.isEmpty()) continue;
                measurementsList.add(measurements);
            }
            return measurementsList;
        }
    }

}
