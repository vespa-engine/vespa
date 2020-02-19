// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

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
    private final NodeResources resources;

    public ClusterResources(List<Node> nodes) {
        this(nodes.size(),
             (int)nodes.stream().map(node -> node.allocation().get().membership().cluster().group()).distinct().count(),
             nodes.get(0).flavor().resources());
    }

    public ClusterResources(int nodes, int groups, NodeResources resources) {
        this.nodes = nodes;
        this.groups = groups;
        this.resources = resources;
    }

    /** Returns the total number of allocated nodes (over all groups) */
    public int nodes() { return nodes; }
    public int groups() { return groups; }
    public NodeResources resources() { return resources; }

    public ClusterResources with(NodeResources resources) {
        return new ClusterResources(nodes, groups, resources);
    }

    public double cost() {
        return Autoscaler.costOf(resources) * nodes;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if ( ! (o instanceof ClusterResources)) return false;

        ClusterResources other = (ClusterResources)o;
        if (other.nodes != this.nodes) return false;
        if (other.groups != this.groups) return false;
        if (other.resources != this.resources) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodes, groups, resources);
    }

    @Override
    public String toString() {
        return "cluster resources: " + nodes + " * " + resources + (groups > 1 ? " in " + groups + " groups" : "");
    }

}
