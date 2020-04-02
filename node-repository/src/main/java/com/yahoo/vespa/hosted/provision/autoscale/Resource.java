// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.config.provision.NodeResources;

/**
 * A resource subject to autoscaling
 *
 * @author bratseth
 */
public enum Resource {

    /** Cpu utilization ratio */
    cpu {
        public String metricName() { return "cpu.util"; }
        double idealAverageLoad() { return 0.2; }
        double valueFrom(NodeResources resources) { return resources.vcpu(); }
        double valueFromMetric(double metricValue) { return metricValue / 100; } // % to ratio
    },

    /** Memory utilization ratio */
    memory {
        public String metricName() { return "mem_total.util"; }
        double idealAverageLoad() { return 0.7; }
        double valueFrom(NodeResources resources) { return resources.memoryGb(); }
        double valueFromMetric(double metricValue) { return metricValue / 100; } // % to ratio
    },

    /** Disk utilization ratio */
    disk {
        public String metricName() { return "disk.util"; }
        double idealAverageLoad() { return 0.6; }
        double valueFrom(NodeResources resources) { return resources.diskGb(); }
        double valueFromMetric(double metricValue) { return metricValue / 100; } // % to ratio
    };

    public abstract String metricName();

    /** The load we should have of this resource on average, when one node in the cluster is down */
    abstract double idealAverageLoad();

    abstract double valueFrom(NodeResources resources);

    abstract double valueFromMetric(double metricValue);

    public static Resource fromMetric(String metricName) {
        for (Resource resource : values())
            if (resource.metricName().equals(metricName)) return resource;
        throw new IllegalArgumentException("Metric '" + metricName + "' does not map to a resource");
    }

}
