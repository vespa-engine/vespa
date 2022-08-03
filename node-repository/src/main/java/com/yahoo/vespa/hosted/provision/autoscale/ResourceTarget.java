// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.config.provision.NodeResources;
import com.yahoo.vespa.hosted.provision.applications.Application;

import java.time.Clock;
import java.time.Duration;
import java.util.OptionalDouble;

/**
 * A resource target to hit for the allocation optimizer.
 * The target is measured in cpu, memory and disk per node in the current allocation.
 *
 * @author bratseth
 */
public class ResourceTarget {

    /** The target real resources per node, assuming the node assignment where this was decided */
    private final NodeResources resources;

    private ResourceTarget(NodeResources resources) {
        this.resources = resources;
    }

    /** Returns the target resources per node in terms of the current allocation */
    public NodeResources resources() { return resources; }

    @Override
    public String toString() {
        return "target " + resources;
    }

    /** Create a target of achieving ideal load given a current load */
    public static ResourceTarget idealLoad(ClusterModel clusterModel,
                                           AllocatableClusterResources current) {
        return new ResourceTarget(clusterModel.loadAdjustment().scaled(current.realResources().nodeResources()));
    }

    /** Crete a target of preserving a current allocation */
    public static ResourceTarget preserve(ClusterModel clusterModel,
                                          AllocatableClusterResources current) {
        return new ResourceTarget(current.realResources().nodeResources());
    }

}
