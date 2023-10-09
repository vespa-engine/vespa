// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.metrics.simple;

import com.yahoo.metrics.simple.UntypedMetric.AssumedType;

/**
 * A single metric measurement and all the meta data needed to route it
 * correctly.
 *
 * @author Steinar Knutsen
 */
public class Sample {

    private final Identifier identifier;
    private final Measurement measurement;
    private final AssumedType metricType;
    private MetricReceiver metricReceiver = null;

    public Sample(Measurement measurement, Identifier id, AssumedType t) {
        this.identifier = id;
        this.measurement = measurement;
        this.metricType = t;
    }

    Identifier getIdentifier() {
        return identifier;
    }

    Measurement getMeasurement() {
        return measurement;
    }

    AssumedType getMetricType() {
        return metricType;
    }

    void setReceiver(MetricReceiver metricReceiver) {
        this.metricReceiver = metricReceiver;
    }

    /**
     * Get histogram definition for an arbitrary metric. Caveat emptor: This
     * involves reading a volatile.
     *
     * @param metricName name of the metric to get histogram definition for
     * @return how to define a new histogram or null
     */
    MetricSettings getHistogramDefinition(String metricName) {
        if (metricReceiver == null) {
            return null;
        } else {
            return metricReceiver.getMetricDefinition(metricName);
        }
    }

}
