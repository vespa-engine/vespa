// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.provisioning.HostResourcesCalculator;

import java.util.List;

/**
 * @author bratseth
 */
public class AllocatableClusterResources {

    /** The node count in the cluster */
    private final int nodes;

    /** The number of node groups in the cluster */
    private final int groups;

    private final NodeResources realResources;
    private final NodeResources advertisedResources;

    private final ClusterSpec.Type clusterType;

    private final double fulfilment;

    public AllocatableClusterResources(List<Node> nodes, HostResourcesCalculator calculator) {
        this.advertisedResources = nodes.get(0).flavor().resources();
        this.realResources = calculator.realResourcesOf(nodes.get(0));
        this.nodes = nodes.size();
        this.groups = (int)nodes.stream().map(node -> node.allocation().get().membership().cluster().group()).distinct().count();
        this.clusterType = nodes.get(0).allocation().get().membership().cluster().type();
        this.fulfilment = 1;
    }

    public AllocatableClusterResources(ClusterResources realResources,
                                       NodeResources advertisedResources,
                                       NodeResources idealResources,
                                       ClusterSpec.Type clusterType) {
        this.realResources = realResources.nodeResources();
        this.advertisedResources = advertisedResources;
        this.nodes = realResources.nodes();
        this.groups = realResources.groups();
        this.clusterType = clusterType;
        this.fulfilment = fulfilment(realResources.nodeResources(), idealResources);
    }

    public AllocatableClusterResources(ClusterResources realResources,
                                       Flavor flavor,
                                       NodeResources idealResources,
                                       ClusterSpec.Type clusterType,
                                       HostResourcesCalculator calculator) {
        this.realResources = realResources.nodeResources();
        this.advertisedResources = calculator.advertisedResourcesOf(flavor);
        this.nodes = realResources.nodes();
        this.groups = realResources.groups();
        this.clusterType = clusterType;
        this.fulfilment = fulfilment(realResources.nodeResources(), idealResources);
    }

    /**
     * Returns the resources which will actually be available per node in this cluster with this allocation.
     * These should be used for reasoning about allocation to meet measured demand.
     */
    public NodeResources realResources() { return realResources; }

    /**
     * Returns the resources advertised by the cloud provider, which are the basis for charging
     * and which must be used in resource allocation requests
     */
    public NodeResources advertisedResources() { return advertisedResources; }

    public ClusterResources toAdvertisedClusterResources() {
        return new ClusterResources(nodes, groups, advertisedResources);
    }

    public int nodes() { return nodes; }
    public int groups() { return groups; }
    public ClusterSpec.Type clusterType() { return clusterType; }

    public double cost() { return nodes * Autoscaler.costOf(advertisedResources); }

    /**
     * Returns the fraction measuring how well the real resources fulfils the ideal: 1 means completely fulfiled,
     * 0 means we have zero real resources.
     * The real may be short of the ideal due to resource limits imposed by the system or application.
     */
    public double fulfilment() { return fulfilment; }

    private static double fulfilment(NodeResources realResources, NodeResources idealResources) {
        double vcpuFulfilment     = Math.min(1, realResources.vcpu()     / idealResources.vcpu());
        double memoryGbFulfilment = Math.min(1, realResources.memoryGb() / idealResources.memoryGb());
        double diskGbFulfilment   = Math.min(1, realResources.diskGb()   / idealResources.diskGb());
        return (vcpuFulfilment + memoryGbFulfilment + diskGbFulfilment) / 3;
    }

    public boolean preferableTo(AllocatableClusterResources other) {
        if (this.fulfilment > other.fulfilment) return true; // we always want to fulfil as much as possible
        return this.cost() < other.cost(); // otherwise, prefer lower cost
    }

    @Override
    public String toString() {
        return "$" + cost() + " (fulfilment " + fulfilment + "): " + realResources();
    }

}
