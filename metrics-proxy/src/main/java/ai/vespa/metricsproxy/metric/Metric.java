// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.metric;

import ai.vespa.metricsproxy.metric.model.ConsumerId;
import ai.vespa.metricsproxy.metric.model.DimensionId;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author Jo Kristian Bergum
 */
public class Metric {

    private final long time;
    private final Number value;
    private final String description;
    private String name;
    private Map<DimensionId, String> dimensions;
    private Set<ConsumerId> consumers;

    /**
     * Creates a new metric instance
     *
     * @param name  The metric name. E.g 'documents'
     * @param value The numeric value
     * @param time  The timestamp of this metric in seconds
     */
    public Metric(String name, Number value, long time, Map<DimensionId, String> dimensions, String description) {
        this.time = time;
        this.value = value;
        this.name = name;
        this.dimensions = dimensions;
        this.description = description;
    }

    public Metric(String name, Number value, long timestamp) {
        this(name, value, timestamp, Collections.emptyMap(), "");
    }

    public Metric(String name, Number value) {
        this(name, value, System.currentTimeMillis() / 1000);
    }

    public void setDimensions(Map<DimensionId, String> dimensions) {
        this.dimensions = dimensions;
    }

    /**
     * @return A map of the dimensions registered for this metric
     */
    public Map<DimensionId, String> getDimensions() { return dimensions; }

    public void setConsumers(Set<ConsumerId> consumers) { this.consumers = consumers; }

    /**
     * @return The consumers this metric should be routed to.
     */
    public Set<ConsumerId> getConsumers() { return consumers; }

    /**
     * @return The number that this metric name represent
     */
    public Number getValue() {
        return value;
    }

    /**
     * Set the name of this metric
     *
     * @param name The name to use for this metric
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return The name of the metric
     */
    public String getName() {
        return name;
    }

    /**
     * @return The UTC timestamp for when this metric was collected
     */
    public long getTimeStamp() {
        return this.time;
    }

    @Override
    public String toString() {
        return "Metric{" +
                "time=" + time +
                ", name=" + name +
                ", value='" + value + '\'' +
                ", dimensions=" + dimensions +
                '}';
    }

    @Override
    public Metric clone() {
        return new Metric(name, value, time, new LinkedHashMap<>(dimensions), getDescription());
    }

    /**
     * @return the description of this metric
     */
    public String getDescription() {
        return this.description;
    }

    /** Return an adjusted (rounded up) time if necessary */
    public static long adjustTime(long timestamp, long now) {
        if ((now == (timestamp+1)) && ((now % 60) == 0)) {
            return now;
        }
        return timestamp;
    }
}
