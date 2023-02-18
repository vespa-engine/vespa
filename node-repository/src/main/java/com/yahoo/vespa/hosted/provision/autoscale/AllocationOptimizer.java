// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.IntRange;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.vespa.hosted.provision.NodeRepository;

import java.util.Optional;

/**
 * A searcher of the space of possible allocation
 *
 * @author bratseth
 */
public class AllocationOptimizer {

    // The min and max nodes to consider when not using application supplied limits
    private static final int minimumNodes = 2; // Since this number includes redundancy it cannot be lower than 2
    private static final int maximumNodes = 150;

    private final NodeRepository nodeRepository;

    public AllocationOptimizer(NodeRepository nodeRepository) {
        this.nodeRepository = nodeRepository;
    }

    /**
     * Searches the space of possible allocations given a target relative load
     * and (optionally) cluster limits and returns the best alternative.
     *
     * @return the best allocation, if there are any possible legal allocations, fulfilling the target
     *         fully or partially, within the limits
     */
    public Optional<AllocatableClusterResources> findBestAllocation(Load loadAdjustment,
                                                                    AllocatableClusterResources current,
                                                                    ClusterModel clusterModel,
                                                                    Limits limits) {
        if (limits.isEmpty())
            limits = Limits.of(new ClusterResources(minimumNodes,    1, NodeResources.unspecified()),
                               new ClusterResources(maximumNodes, maximumNodes, NodeResources.unspecified()),
                               IntRange.empty());
        else
            limits = atLeast(minimumNodes, limits).fullySpecified(current.clusterSpec(), nodeRepository, clusterModel.application().id());
        Optional<AllocatableClusterResources> bestAllocation = Optional.empty();
        var availableRealHostResources = nodeRepository.zone().cloud().dynamicProvisioning()
                                         ? nodeRepository.flavors().getFlavors().stream().map(flavor -> flavor.resources()).toList()
                                         : nodeRepository.nodes().list().hosts().stream().map(host -> host.flavor().resources())
                                                         .map(hostResources -> maxResourcesOf(hostResources, clusterModel))
                                                         .toList();
        for (int groups = limits.min().groups(); groups <= limits.max().groups(); groups++) {
            for (int nodes = limits.min().nodes(); nodes <= limits.max().nodes(); nodes++) {
                if (nodes % groups != 0) continue;
                if ( ! limits.groupSize().includes(nodes / groups)) continue;
                var resources = new ClusterResources(nodes,
                                                     groups,
                                                     nodeResourcesWith(nodes, groups,
                                                                       limits, loadAdjustment, current, clusterModel));
                var allocatableResources = AllocatableClusterResources.from(resources,
                                                                            clusterModel.application().id(),
                                                                            current.clusterSpec(),
                                                                            limits,
                                                                            availableRealHostResources,
                                                                            nodeRepository);
                if (allocatableResources.isEmpty()) continue;
                if (bestAllocation.isEmpty() || allocatableResources.get().preferableTo(bestAllocation.get())) {
                    bestAllocation = allocatableResources;
                }
            }
        }
        return bestAllocation;
    }

    /** Returns the max resources of a host one node may allocate. */
    private NodeResources maxResourcesOf(NodeResources hostResources, ClusterModel clusterModel) {
        if (nodeRepository.exclusiveAllocation(clusterModel.clusterSpec())) return hostResources;
        // static, shared hosts: Allocate at most half of the host cpu to simplify management
        return hostResources.withVcpu(hostResources.vcpu() / 2);
    }

    /**
     * For the observed load this instance is initialized with, returns the resources needed per node to be at
     * the target relative load, given a target node and group count.
     */
    private NodeResources nodeResourcesWith(int nodes,
                                            int groups,
                                            Limits limits,
                                            Load loadAdjustment,
                                            AllocatableClusterResources current,
                                            ClusterModel clusterModel) {
        var scaled = loadAdjustment                                  // redundancy aware target relative to current load
                     .multiply(clusterModel.loadWith(nodes, groups)) // redundancy aware adjustment with these counts
                     .divide(clusterModel.redundancyAdjustment())    // correct for double redundancy adjustment
                     .scaled(current.realResources().nodeResources());
        // Combine the scaled resource values computed here
        // with the currently configured non-scaled values, given in the limits, if any
        var nonScaled = limits.isEmpty() || limits.min().nodeResources().isUnspecified()
                        ? current.advertisedResources().nodeResources()
                        : limits.min().nodeResources(); // min=max for non-scaled
        return nonScaled.withVcpu(scaled.vcpu()).withMemoryGb(scaled.memoryGb()).withDiskGb(scaled.diskGb());
    }

    /** Returns a copy of the given limits where the minimum nodes are at least the given value when allowed */
    private Limits atLeast(int min, Limits limits) {
        if (limits.max().nodes() < min) return limits; // not allowed
        return limits.withMin(limits.min().withNodes(Math.max(min, limits.min().nodes())));
    }

}
