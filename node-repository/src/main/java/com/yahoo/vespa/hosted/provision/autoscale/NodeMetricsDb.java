// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
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

    /** Measurements by host. Each list of measurements is sorted by increasing timestamp */
    private final Map<String, NodeMeasurements> db = new HashMap<>();

    /** Lock all access for now since we modify lists inside a map */
    private final Object lock = new Object();

    public NodeMetricsDb(NodeRepository nodeRepository) {
        this.nodeRepository = nodeRepository;
    }

    /** Adds measurements to this. */
    public void add(Collection<MetricsFetcher.NodeMetrics> nodeMetrics) {
        synchronized (lock) {
            for (var value : nodeMetrics) {
                add(value.hostname(), new Measurement(value));
            }
        }
    }

    private void add(String hostname, Measurement measurement) {
        NodeMeasurements measurements = db.get(hostname);
        if (measurements == null) { // new node
            Optional<Node> node = nodeRepository.getNode(hostname);
            if (node.isEmpty()) return;
            if (node.get().allocation().isEmpty()) return;
            measurements = new NodeMeasurements(hostname,
                                                node.get().allocation().get().membership().cluster().type(),
                                                new ArrayList<>());
            db.put(hostname, measurements);
        }
        measurements.add(measurement);
    }

    /** Must be called intermittently (as long as any add methods are called) to gc old data */
    public void gc(Clock clock) {
        synchronized (lock) {
            // Each measurement is Object + long + float = 16 + 8 + 4 = 28 bytes
            // 12 hours with 1k nodes and 3 resources and 1 measurement/sec is about 5Gb
            for (Iterator<NodeMeasurements> i = db.values().iterator(); i.hasNext(); ) {
                var measurements = i.next();
                measurements.removeOlderThan(clock.instant().minus(Autoscaler.scalingWindow(measurements.type)).toEpochMilli());
                if (measurements.isEmpty())
                    i.remove();
            }
        }
    }

    /**
     * Returns a list of measurements with one entry for each of the given host names
     * which have any values after startTime, in the same order
     */
    public List<NodeMeasurements> getMeasurements(Instant startTime, List<String> hostnames) {
        synchronized (lock) {
            List<NodeMeasurements> measurementsList = new ArrayList<>(hostnames.size());
            for (String hostname : hostnames) {
                NodeMeasurements measurements = db.get(hostname);
                if (measurements == null) continue;
                measurements = measurements.copyAfter(startTime);
                if (measurements.isEmpty()) continue;
                measurementsList.add(measurements);
            }
            return measurementsList;
        }
    }

    public static class NodeMeasurements {

        private final String hostname;
        private final ClusterSpec.Type type;
        private final List<Measurement> measurements;

        // Note: This transfers ownership of the measurement list to this
        private NodeMeasurements(String hostname, ClusterSpec.Type type, List<Measurement> measurements) {
            this.hostname = hostname;
            this.type = type;
            this.measurements = measurements;
        }

        // Public access

        public boolean isEmpty() { return measurements.isEmpty(); }

        public int size() { return measurements.size(); }

        public Measurement get(int index) { return measurements.get(index); }

        public List<Measurement> asList() { return Collections.unmodifiableList(measurements); }

        public String hostname() { return hostname; }

        public NodeMeasurements copyAfter(Instant oldestTime) {
            long oldestTimestamp = oldestTime.toEpochMilli();
            return new NodeMeasurements(hostname, type,
                                        measurements.stream()
                                                    .filter(measurement -> measurement.timestamp >= oldestTimestamp)
                                                    .collect(Collectors.toList()));
        }

        // Private mutation

        private void add(Measurement measurement) { measurements.add(measurement); }

        private void removeOlderThan(long oldestTimestamp) {
            while (!measurements.isEmpty() && measurements.get(0).timestamp < oldestTimestamp)
                measurements.remove(0);
        }

    }

    /** A single measurement of all values we measure, for one node */
    public static class Measurement {

        // TODO: Order by timestamp
        /** The time of this measurement in epoch millis */
        private final long timestamp;

        private final double cpu;
        private final double memory;
        private final double disk;
        private final long generation;

        public Measurement(MetricsFetcher.NodeMetrics metrics) {
            this.timestamp = metrics.timestampSecond() * 1000;
            this.cpu = Metric.cpu.valueFromMetric(metrics.cpuUtil());
            this.memory = Metric.memory.valueFromMetric(metrics.totalMemUtil());
            this.disk = Metric.disk.valueFromMetric(metrics.diskUtil());
            this.generation = (long)Metric.generation.valueFromMetric(metrics.applicationGeneration());

        }

        public double cpu() { return cpu; }
        public double memopry() { return memory; }
        public double disk() { return disk; }
        public long generation() { return generation; }
        public Instant at() { return Instant.ofEpochMilli(timestamp); }

        @Override
        public String toString() { return "measurement at " + timestamp + ": " +
                                          "cpu: " + cpu +
                                          "memory: " + memory +
                                          "disk: " + disk +
                                          "generation: " + generation; }

    }

}
