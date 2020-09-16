// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    /** Measurements by key. Each list of measurements is sorted by increasing timestamp */
    private final Map<NodeMeasurementsKey, NodeMeasurements> db = new HashMap<>();

    /** Lock all access for now since we modify lists inside a map */
    private final Object lock = new Object();

    public NodeMetricsDb(NodeRepository nodeRepository) {
        this.nodeRepository = nodeRepository;
    }

    /** Add measurements to this */
    public void add(Collection<NodeMetrics.MetricValue> metricValues) {
        synchronized (lock) {
            for (var value : metricValues) {
                Resource resource =  Resource.fromMetric(value.name());
                NodeMeasurementsKey key = new NodeMeasurementsKey(value.hostname(), resource);
                NodeMeasurements measurements = db.get(key);
                if (measurements == null) { // new node
                    Optional<Node> node = nodeRepository.getNode(value.hostname());
                    if (node.isEmpty()) continue;
                    if (node.get().allocation().isEmpty()) continue;
                    measurements = new NodeMeasurements(value.hostname(),
                                                        resource,
                                                        node.get().allocation().get().membership().cluster().type());
                    db.put(key, measurements);
                }
                measurements.add(new Measurement(value.timestampSecond() * 1000,
                                                 (float)resource.valueFromMetric(value.value())));
            }
        }
    }

    /** Must be called intermittently (as long as add is called) to gc old measurements */
    public void gc(Clock clock) {
        synchronized (lock) {
            // Each measurement is Object + long + float = 16 + 8 + 4 = 28 bytes
            // 24 hours with 1k nodes and 3 resources and 1 measurement/sec is about 10Gb

            for (Iterator<NodeMeasurements> i = db.values().iterator(); i.hasNext(); ) {
                var measurements = i.next();
                measurements.removeOlderThan(clock.instant().minus(Autoscaler.scalingWindow(measurements.type)).toEpochMilli());
                if (measurements.isEmpty())
                    i.remove();
            }
        }
    }

    /** Returns a window within which we can ask for specific information from this db */
    public Window getWindow(Instant startTime, Resource resource, List<String> hostnames) {
        return new Window(startTime, resource, hostnames);
    }

    public class Window {

        private final long startTime;
        private final List<NodeMeasurementsKey> keys;

        private Window(Instant startTime, Resource resource, List<String> hostnames) {
            this.startTime = startTime.toEpochMilli();
            keys = hostnames.stream().map(hostname -> new NodeMeasurementsKey(hostname, resource)).collect(Collectors.toList());
        }

        public int measurementCount() {
            synchronized (lock) {
                int count = 0;
                for (NodeMeasurementsKey key : keys) {
                    NodeMeasurements measurements = db.get(key);
                    if (measurements == null) continue;
                    count += measurements.after(startTime).size();
                }
                return count;
            }
        }

        /** Returns the count of hostnames which have measurements in this window */
        public int hostnames() {
            synchronized (lock) {
                int count = 0;
                for (NodeMeasurementsKey key : keys) {
                    NodeMeasurements measurements = db.get(key);
                    if (measurements == null || measurements.isEmpty()) continue;

                    if (measurements.get(measurements.size() - 1).timestamp >= startTime)
                        count++;
                }
                return count;
            }
        }

        public double average() {
            synchronized (lock) {
                double sum = 0;
                int count = 0;
                for (NodeMeasurementsKey key : keys) {
                    NodeMeasurements measurements = db.get(key);
                    if (measurements == null) continue;

                    int index = measurements.size() - 1;
                    while (index >= 0 && measurements.get(index).timestamp >= startTime) {
                        sum += measurements.get(index).value;
                        count++;

                        index--;
                    }
                }
                return sum / count;
            }
        }

    }

    private static class NodeMeasurementsKey {

        private final String hostname;
        private final Resource resource;

        public NodeMeasurementsKey(String hostname, Resource resource) {
            this.hostname = hostname;
            this.resource = resource;
        }

        @Override
        public int hashCode() {
            return Objects.hash(hostname, resource);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if ( ! (o instanceof NodeMeasurementsKey)) return false;
            NodeMeasurementsKey other = (NodeMeasurementsKey)o;
            if ( ! this.hostname.equals(other.hostname)) return false;
            if ( ! this.resource.equals(other.resource)) return false;
            return true;
        }

        @Override
        public String toString() { return "key to measurements of " + resource + " for " + hostname; }

    }

    private static class NodeMeasurements {

        private final String hostname;
        private final Resource resource;
        private final ClusterSpec.Type type;
        private final List<Measurement> measurements = new ArrayList<>();

        public NodeMeasurements(String hostname, Resource resource, ClusterSpec.Type type) {
            this.hostname = hostname;
            this.resource = resource;
            this.type = type;
        }

        void removeOlderThan(long oldestTimestamp) {
            while (!measurements.isEmpty() && measurements.get(0).timestamp < oldestTimestamp)
                measurements.remove(0);
        }

        boolean isEmpty() { return measurements.isEmpty(); }

        int size() { return measurements.size(); }

        Measurement get(int index) { return measurements.get(index); }

        void add(Measurement measurement) { measurements.add(measurement); }

        public List<Measurement> after(long oldestTimestamp) {
            return measurements.stream()
                               .filter(measurement -> measurement.timestamp >= oldestTimestamp)
                               .collect(Collectors.toList());
        }

    }

    private static class Measurement {

        /** The time of this measurement in epoch millis */
        private final long timestamp;

        /** The measured value */
        private final float value;

        public Measurement(long timestamp, float value) {
            this.timestamp = timestamp;
            this.value = value;
        }

        @Override
        public String toString() { return "measurement at " + timestamp + ": " + value; }

    }

}
