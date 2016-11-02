// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin.monitoring;

import java.util.HashMap;
import java.util.Map;

/**
 * Helper class to model a metric.
 *
 * @author trygve
 */
public class Metric {

    private final String name;
    private String outputName;
    private final Map<String, String> dimensions = new HashMap<>();
    private final String description;

    /**
     * @param name        The metric name
     * @param outputName  The name of the metric in yamas
     * @param description The description of this metric
     */
    public Metric(String name, String outputName, String description) {
        this.name = name;
        this.outputName = outputName;
        this.description = description;
    }

    /**
     * Creates a metric with empty dimensions and consumers containing the default consumer
     *
     * @param name       the metric name
     * @param outputName name tp be used in yamas
     */
    public Metric(String name, String outputName) {
        this(name, outputName, "");
    }

    /**
     * Creates a metric with same outputname as metricname and  empty dimensions and consumers containing the default consumer and
     *
     * @param name The name of the metric, same name used for outputname
     */
    public Metric(String name) {
        this(name, name);
    }

    public String getDescription() {
        return this.description;
    }


    public String getOutputName() {
        return outputName;
    }

    public void setOutputName(String outputName) {
        this.outputName = outputName;
    }

    public String getName() {
        return name;
    }

    public Map<String, String> getDimensions() {
        return dimensions;
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
