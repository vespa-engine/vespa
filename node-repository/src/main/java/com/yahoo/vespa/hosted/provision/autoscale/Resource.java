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
        double valueFrom(NodeResources resources) { return resources.vcpu(); }
    },

    /** Memory utilization ratio */
    memory {
        double valueFrom(NodeResources resources) { return resources.memoryGb(); }
    },

    /** Disk utilization ratio */
    disk {
        double valueFrom(NodeResources resources) { return resources.diskGb(); }
    };

    abstract double valueFrom(NodeResources resources);

}
