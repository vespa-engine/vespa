// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.config.provision.NodeResources;

import java.util.Objects;

/** A description of the resources of a cluster */
public class ClusterResources {

    /** The node count in the cluster */
    private final int count;

    /** The resources of each node in the cluster */
    private final NodeResources resources;

    public ClusterResources(int count, NodeResources resources) {
        this.count = count;
        this.resources = resources;
    }

    public int count() { return count; }
    public NodeResources resources() { return resources; }

    public double cost() {
        return Autoscaler.costOf(resources) * count;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if ( ! (o instanceof ClusterResources)) return false;

        ClusterResources other = (ClusterResources)o;
        if (other.count != this.count) return false;
        if (other.resources != this.resources) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(count, resources);
    }

    @Override
    public String toString() {
        return "cluster resources: " + count + " * " + resources;
    }

}
