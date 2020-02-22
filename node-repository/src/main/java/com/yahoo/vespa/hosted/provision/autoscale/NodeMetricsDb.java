// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.vespa.hosted.provision.Node;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * An in-memory time-series "database" of node metrics.
 * Thread model: One writer, many readers.
 *
 * @author bratseth
 */
public class NodeMetricsDb {

    private static final Duration dbWindow = Duration.ofHours(24);

    /** Measurements by key. Each list of measurements is sorted by increasing timestamp */
    private Map<MeasurementKey, List<Measurement>> db = new HashMap<>();

    /** Lock all access for now since we modify lists inside a map */
    private final Object lock = new Object();

    /** Add a measurement to this */
    public void add(String hostname, Resource resource, Instant timestamp, float value) {
        synchronized (lock) {
            List<Measurement> measurements = db.computeIfAbsent(new MeasurementKey(hostname, resource), (__) -> new ArrayList<>());
            measurements.add(new Measurement(timestamp.toEpochMilli(), value));
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
                int count = 0;
                for (MeasurementKey key : keys) {
                    List<Measurement> measurements = db.get(key);
                    if (measurements == null) continue;
                    int measurementsInWindow = measurements.size() - largestIndexOutsideWindow(measurements) + 1;
                    count += measurementsInWindow;
                }
                return count;
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

        private int largestIndexOutsideWindow(List<Measurement> measurements) {
            int index = measurements.size() - 1;
            while (index >= 0 && measurements.get(index).timestamp >= startTime)
                index--;
            return index;
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

    }

}
