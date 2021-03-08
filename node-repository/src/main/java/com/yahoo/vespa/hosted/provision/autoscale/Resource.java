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
        public double idealAverageLoad() { return 0.8; }
        double valueFrom(NodeResources resources) { return resources.vcpu(); }
    },

    /** Memory utilization ratio */
    memory {
        public double idealAverageLoad() { return 0.7; }
        double valueFrom(NodeResources resources) { return resources.memoryGb(); }
    },

    /** Disk utilization ratio */
    disk {
        public double idealAverageLoad() { return 0.6; }
        double valueFrom(NodeResources resources) { return resources.diskGb(); }
    };

    /** The load we should have of this resource on average, when one node in the cluster is down */
    public abstract double idealAverageLoad();

    abstract double valueFrom(NodeResources resources);

}
