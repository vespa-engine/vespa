// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

/**
 * A kind of measurement we are making for autoscaling purposes
 *
 * @author bratseth
 */
public enum Metric {

    cpu { // a node resource
        public String fullName() { return "cpu.util"; }
        float valueFromMetric(double metricValue) { return (float)metricValue / 100; } // % to ratio
    },
    memory { // a node resource
        public String fullName() { return "mem_total.util"; }
        float valueFromMetric(double metricValue) { return (float)metricValue / 100; } // % to ratio
    },
    disk { // a node resource
        public String fullName() { return "disk.util"; }
        float valueFromMetric(double metricValue) { return (float)metricValue / 100; } // % to ratio
    },
    generation { // application config generation active on the node
        public String fullName() { return "application_generation"; }
        float valueFromMetric(double metricValue) { return (float)metricValue; } // Really an integer, ok up to 16M gens
    };

    /** The name of this metric as emitted from its source */
    public abstract String fullName();

    /** Convert from the emitted value of this metric to the value we want to use here */
    abstract float valueFromMetric(double metricValue);

    public static Metric fromFullName(String name) {
        for (Metric metric : values())
            if (metric.fullName().equals(name)) return metric;
        throw new IllegalArgumentException("Metric '" + name + "' has no mapping");
    }

    public static Metric from(Resource resource) {
        for (Metric metric : values())
            if (metric.name().equals(resource.name())) return metric;
        throw new IllegalArgumentException("Resource '" + resource + "' does not map to a metric");
    }

}
