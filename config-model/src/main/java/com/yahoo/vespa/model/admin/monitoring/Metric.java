// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin.monitoring;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Helper class to model a metric.
 *
 * @author trygve
 * @author gjoranv
 */
public class Metric {

    public final String name;
    public final String outputName;
    public final String description;
    public final Map<String, String> dimensions;

    public Metric(String name, String outputName, String description, Map<String, String> dimensions) {
        this.name = name;
        this.outputName = outputName;
        this.description = description;
        this.dimensions = Map.copyOf(dimensions);
    }

    public Metric(String name, String outputName, String description) {
        this(name, outputName, description, new LinkedHashMap<>());
    }

    /**
     * Creates a metric with empty description
     */
    public Metric(String name, String outputName) {
        this(name, outputName, "");
    }

    /**
     * Creates a metric with same outputname as metricname
     *
     * @param name The name of the metric, same name used for output name
     */
    public Metric(String name) {
        this(name, name);
    }

    /**
     * Returns a new Metric that is a combination of this and the given metric.
     * New dimensions from the given metric are added, but already existing
     * dimensions will be kept unchanged.
     *
     * @param other The metric to add dimensions from.
     * @return A new metric with dimensions from this and the other.
     */
    public Metric addDimensionsFrom(Metric other) {
        Map<String, String> combined = new LinkedHashMap<>(dimensions);
        other.dimensions.forEach(
                (k, v) -> {
                    if (!combined.containsKey(k)) combined.put(k, v);
                });

        return new Metric(name, outputName, description, combined);
    }


    @Override
    public String toString() {
        return "Metric{" +
                "name='" + name + '\'' +
                ", outputName='" + outputName + '\'' +
                ", dimensions=" + dimensions +
                '}';
    }


    /**
     * Two metrics are considered equal if they have the same name.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Metric metric = (Metric) o;

        return name.equals(metric.name);

    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

}
