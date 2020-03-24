// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.vespa.hosted.provision.Node;

import java.util.List;
import java.util.Objects;

/** A description of the resources of a cluster */
public class ClusterResources {

    /** The node count in the cluster */
    private final int nodes;

    /** The number of node groups in the cluster */
    private final int groups;

    /** The resources of each node in the cluster */
    private final NodeResources nodeResources;

    /** The kind of cluster these resources are for */
    private final ClusterSpec.Type clusterType;

    public ClusterResources(int nodes, int groups, NodeResources nodeResources, ClusterSpec.Type clusterType) {
        this.nodes = nodes;
        this.groups = groups;
        this.nodeResources = nodeResources;
        this.clusterType = clusterType;
    }

    /** Returns the total number of allocated nodes (over all groups) */
    public int nodes() { return nodes; }
    public int groups() { return groups; }
    public NodeResources nodeResources() { return nodeResources; }
    public ClusterSpec.Type clusterType() { return clusterType; }

    public ClusterResources with(NodeResources resources) {
        return new ClusterResources(nodes, groups, resources, clusterType);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if ( ! (o instanceof ClusterResources)) return false;

        ClusterResources other = (ClusterResources)o;
        if (other.nodes != this.nodes) return false;
        if (other.groups != this.groups) return false;
        if (other.nodeResources != this.nodeResources) return false;
        if (other.clusterType != this.clusterType) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodes, groups, nodeResources, clusterType);
    }

    @Override
    public String toString() {
        return clusterType + " cluster resources: " + nodes + " * " + nodeResources + (groups > 1 ? " in " + groups + " groups" : "");
    }

}
