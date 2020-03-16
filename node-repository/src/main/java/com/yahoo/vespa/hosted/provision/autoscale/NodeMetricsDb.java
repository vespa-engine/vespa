// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An in-memory time-series "database" of node metrics.
 * Thread model: One writer, many readers.
 *
 * @author bratseth
 */
public class NodeMetricsDb {

    private Logger log = Logger.getLogger(NodeMetricsDb.class.getName());
    private static final Duration dbWindow = Duration.ofHours(24);

    /** Measurements by key. Each list of measurements is sorted by increasing timestamp */
    private Map<MeasurementKey, List<Measurement>> db = new HashMap<>();

    /** Lock all access for now since we modify lists inside a map */
    private final Object lock = new Object();

    /** Add a measurement to this */
    public void add(Collection<NodeMetrics.MetricValue> metricValues) {
        synchronized (lock) {
            for (var value : metricValues) {
                Resource resource =  Resource.fromMetric(value.name());
                List<Measurement> measurements = db.computeIfAbsent(new MeasurementKey(value.hostname(), resource),
                                                                    (__) -> new ArrayList<>());
                measurements.add(new Measurement(value.timestampSecond() * 1000,
                                                 (float)resource.valueFromMetric(value.value())));
            }
        }
    }

    /** Must be called intermittently (as long as add is called) to gc old measurements */
    public void gc(Clock clock) {
        synchronized (lock) {
            // TODO: We may need to do something more complicated to avoid spending too much memory to
            // lower the measurement interval (see NodeRepositoryMaintenance)
            // Each measurement is Object + long + float = 16 + 8 + 4 = 28 bytes
            // 24 hours with 1k nodes and 3 resources and 1 measurement/sec is about 10Gb

            long oldestTimestamp = clock.instant().minus(dbWindow).toEpochMilli();
            for (Iterator<List<Measurement>> i = db.values().iterator(); i.hasNext(); ) {
                List<Measurement> measurements = i.next();
                while (!measurements.isEmpty() && measurements.get(0).timestamp < oldestTimestamp)
                    measurements.remove(0);

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
        private List<MeasurementKey> keys;

        private Window(Instant startTime, Resource resource, List<String> hostnames) {
            this.startTime = startTime.toEpochMilli();
            keys = hostnames.stream().map(hostname -> new MeasurementKey(hostname, resource)).collect(Collectors.toList());
        }

        public int measurementCount() {
            synchronized (lock) {
                return (int) keys.stream()
                                 .flatMap(key -> db.getOrDefault(key, List.of()).stream())
                                 .filter(measurement -> measurement.timestamp >= startTime)
                                 .count();
            }
        }

        /** Returns the count of hostnames which have measurements in this window */
        public int hostnames() {
            synchronized (lock) {
                int count = 0;
                for (MeasurementKey key : keys) {
                    List<Measurement> measurements = db.get(key);
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
                for (MeasurementKey key : keys) {
                    List<Measurement> measurements = db.get(key);
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

    private static class MeasurementKey {

        private final String hostname;
        private final Resource resource;

        public MeasurementKey(String hostname, Resource resource) {
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
            if ( ! (o instanceof MeasurementKey)) return false;
            MeasurementKey other = (MeasurementKey)o;
            if ( ! this.hostname.equals(other.hostname)) return false;
            if ( ! this.resource.equals(other.resource)) return false;
            return true;
        }

        @Override
        public String toString() { return "measurements of " + resource + " for " + hostname; }

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
