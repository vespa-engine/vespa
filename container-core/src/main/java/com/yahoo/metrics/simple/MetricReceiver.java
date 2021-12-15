// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.metrics.simple;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import com.yahoo.api.annotations.Beta;
import com.google.common.collect.ImmutableMap;
import com.yahoo.concurrent.ThreadLocalDirectory;

/**
 * The reception point for measurements. This is the class users should inject
 * in constructors for declaring instances of {@link Counter} and {@link Gauge}
 * for the actual measurement of metrics.
 *
 * @author Steinar Knutsen
 */
@Beta
public class MetricReceiver {

    public static final MetricReceiver nullImplementation = new NullReceiver();
    private final ThreadLocalDirectory<Bucket, Sample> metricsCollection;

    // A reference to the current snapshot. The *reference* is shared with MetricsAggregator and updated from there :-/
    private final AtomicReference<Bucket> currentSnapshot;

    // metricSettings is volatile for reading, the lock is for updates
    private final Object histogramDefinitionsLock = new Object();
    private volatile Map<String, MetricSettings> metricSettings;

    private static final class NullCounter extends Counter {

        NullCounter() {
            super(null, null, null);
        }

        @Override
        public void add() {
        }

        @Override
        public void add(long n) {
        }

        @Override
        public void add(Point p) {
        }

        @Override
        public void add(long n, Point p) {
        }

        @Override
        public PointBuilder builder() {
            return super.builder();
        }
    }

    private static final class NullGauge extends Gauge {
        NullGauge() {
            super(null, null, null);
        }

        @Override
        public void sample(double x) {
        }

        @Override
        public void sample(double x, Point p) {
        }

        @Override
        public PointBuilder builder() {
            return super.builder();
        }

    }

    public static final class MockReceiver extends MetricReceiver {

        private final ThreadLocalDirectory<Bucket, Sample> collection;

        private MockReceiver(ThreadLocalDirectory<Bucket, Sample> collection) {
            super(collection, null);
            this.collection = collection;
        }

        public MockReceiver() {
            this(new ThreadLocalDirectory<>(new MetricUpdater()));
        }

        /** Gathers all data since last snapshot */
        public Bucket getSnapshot() {
            Bucket merged = new Bucket();
            for (Bucket b : collection.fetch()) {
                merged.merge(b, true);
            }
            return merged;
        }

        /** Utility method for testing */
        public Point point(String dim, String val) {
            return pointBuilder().set(dim, val).build();
        }

    }

    private static final class NullReceiver extends MetricReceiver {

        NullReceiver() {
            super(null, null);
        }

        @Override
        public void update(Sample s) {
        }

        @Override
        public Counter declareCounter(String name) {
            return new NullCounter();
        }

        @Override
        public Counter declareCounter(String name, Point boundDimensions) {
            return new NullCounter();
        }

        @Override
        public Gauge declareGauge(String name) {
            return new NullGauge();
        }

        @Override
        public Gauge declareGauge(String name, Point boundDimensions) {
            return new NullGauge();
        }

        @Override
        public Gauge declareGauge(String name, Optional<Point> boundDimensions, MetricSettings customSettings) {
            return null;
        }

        @Override
        public PointBuilder pointBuilder() {
            return null;
        }

        @Override
        public Bucket getSnapshot() {
            return null;
        }

        @Override
        void addMetricDefinition(String metricName, MetricSettings definition) {
        }

        @Override
        MetricSettings getMetricDefinition(String metricName) {
            return null;
        }
    }

    public MetricReceiver(ThreadLocalDirectory<Bucket, Sample> metricsCollection, AtomicReference<Bucket> currentSnapshot) {
        this.metricsCollection = metricsCollection;
        this.currentSnapshot = currentSnapshot;
        metricSettings = new ImmutableMap.Builder<String, MetricSettings>().build();
    }

    /**
     * Update a metric. This API is not intended for clients for the
     * simplemetrics API, declare a Counter or a Gauge using
     * {@link #declareCounter(String)}, {@link #declareCounter(String, Point)},
     * {@link #declareGauge(String)}, or {@link #declareGauge(String, Point)}
     * instead.
     *
     * @param sample a single simple containing all meta data necessary to update a metric
     */
    public void update(Sample sample) {
        // pass around the receiver instead of histogram settings to avoid reading any volatile if unnecessary
        sample.setReceiver(this);
        metricsCollection.update(sample);
    }

    /**
     * Declare a counter metric without setting any default position.
     *
     * @param name the name of the metric
     * @return a thread-safe counter
     */
    public Counter declareCounter(String name) {
        return declareCounter(name, null);
    }

    /**
     * Declare a counter metric, with default dimension values as given. Create
     * the point argument by using a builder from {@link #pointBuilder()}.
     *
     * @param name the name of the metric
     * @param boundDimensions dimensions which have a fixed value in the life cycle of the metric object or null
     * @return a thread-safe counter with given default values
     */
    public Counter declareCounter(String name, Point boundDimensions) {
        return new Counter(name, boundDimensions, this);
    }

    /**
     * Declare a gauge metric with any default position.
     *
     * @param name the name of the metric
     * @return a thread-safe gauge instance
     */
    public Gauge declareGauge(String name) {
        return declareGauge(name, null);
    }

    /**
     * Declare a gauge metric, with default dimension values as given. Create
     * the point argument by using a builder from {@link #pointBuilder()}.
     *
     * @param name the name of the metric
     * @param boundDimensions dimensions which have a fixed value in the life cycle of the metric object or null
     * @return a thread-safe gauge metric
     */
    public Gauge declareGauge(String name, Point boundDimensions) {
        return declareGauge(name, Optional.ofNullable(boundDimensions), null);
    }

    /**
     * Declare a gauge metric, with default dimension values as given. Create
     * the point argument by using a builder from {@link #pointBuilder()}.
     * MetricSettings instances are built using
     * {@link MetricSettings.Builder}.
     *
     * @param name the name of the metric
     * @param boundDimensions an optional of dimensions which have a fixed value in the life cycle of the metric object
     * @param customSettings any optional settings
     * @return a thread-safe gauge metric
     */
    public Gauge declareGauge(String name, Optional<Point> boundDimensions, MetricSettings customSettings) {
        if (customSettings != null) {
            addMetricDefinition(name, customSettings);
        }
        Point defaultDimensions = null;
        if (boundDimensions.isPresent()) {
            defaultDimensions = boundDimensions.get();
        }
        return new Gauge(name, defaultDimensions, this);
    }

    /**
     * Create a PointBuilder instance with no default settings. PointBuilder
     * instances are not thread-safe.
     *
     * @return an "empty" point builder instance
     */
    public PointBuilder pointBuilder() {
        return new PointBuilder();
    }

    /**
     * Fetch the latest metric values, aggregated over all threads for the
     * configured sample history (by default five minutes). The values will be
     * less than 1 second old, and this method has only a memory barrier as side
     * effect.
     *
     * @return the latest five minutes of metrics
     */
    public Bucket getSnapshot() {
        return currentSnapshot.get();
    }

    /**
     * Add how to build a histogram for a given metric.
     *
     * @param metricName the metric where samples should be put in a histogram
     * @param definition settings for a histogram
     */
    void addMetricDefinition(String metricName, MetricSettings definition) {
        synchronized (histogramDefinitionsLock) {
            // read the volatile _after_ acquiring the lock
            Map<String, MetricSettings> oldMetricDefinitions = metricSettings;
            Map<String, MetricSettings> builderMap = new HashMap<>(oldMetricDefinitions.size() + 1);
            builderMap.putAll(oldMetricDefinitions);
            builderMap.put(metricName, definition);
            metricSettings = ImmutableMap.copyOf(builderMap);
        }
    }

    /**
     * Get how to build a histogram for a given metric, or null if no histogram should be created.
     *
     * @param metricName the name of an arbitrary metric
     * @return the corresponding histogram definition or null
     */
    MetricSettings getMetricDefinition(String metricName) {
        return metricSettings.get(metricName);
    }
}
