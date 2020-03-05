// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.provisioning.HostResourcesCalculator;

import java.util.List;

/**
 * @author bratseth
 */
public class AllocatableClusterResources {

    private final ClusterResources realResources;
    private final ClusterResources advertisedResources;

    public AllocatableClusterResources(List<Node> nodes, HostResourcesCalculator calculator) {
        this.advertisedResources = new ClusterResources(nodes);
        this.realResources = advertisedResources.with(calculator.realResourcesOf(nodes.get(0)));
    }

    public AllocatableClusterResources(ClusterResources realResources, NodeResources advertisedResources) {
        this.realResources = realResources;
        this.advertisedResources = realResources.with(advertisedResources);
    }

    public AllocatableClusterResources(ClusterResources realResources, Flavor flavor, HostResourcesCalculator calculator) {
        this.realResources = realResources;
        this.advertisedResources = realResources.with(calculator.advertisedResourcesOf(flavor));
    }

    /**
     * Returns the resources which will actually be available in this cluster with this allocation.
     * These should be used for reasoning about allocation to meet measured demand.
     */
    public ClusterResources realResources() { return realResources; }

    /**
     * Returns the resources advertised by the cloud provider, which are the basis for charging
     * and which must be used in resource allocation requests
     */
    public ClusterResources advertisedResources() { return advertisedResources; }

    public double cost() { return advertisedResources.nodes() * Autoscaler.costOf(advertisedResources.nodeResources()); }

    @Override
    public String toString() {
        return "$" + cost() + ": " + realResources();
    }

}
