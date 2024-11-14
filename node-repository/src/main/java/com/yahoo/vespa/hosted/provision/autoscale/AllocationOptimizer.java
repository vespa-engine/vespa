// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.IntRange;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.vespa.hosted.provision.NodeRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

import static com.yahoo.vespa.hosted.provision.autoscale.Autoscaler.headroomRequiredToScaleDown;

/**
 * A searcher of the space of possible allocation
 *
 * @author bratseth
 */
public class AllocationOptimizer {

    private static final Logger log = Logger.getLogger(AllocationOptimizer.class.getName());

    // The min and max nodes to consider when not using application supplied limits
    private static final int minimumNodes = 2; // Since this number includes redundancy it cannot be lower than 2
    private static final int maximumNodes = 150;

    // A lower bound on the number of vCPUs to suggest. Flavors with fewer vCPUs than this are rarely good for
    // anything but testing.
    private static final int minimumSuggestedVcpus = 2;

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
    public Optional<AllocatableResources> findBestAllocation(Load loadAdjustment, ClusterModel model, Limits limits, boolean logDetails) {
        return findBestAllocations(loadAdjustment, model, limits, logDetails).stream().findFirst();
    }

    /**
     * Searches the space of possible allocations given a target relative load
     * and (optionally) cluster limits and returns the best alternative.
     *
     * @return the best allocations, if there are any possible legal allocations, fulfilling the target
     *         fully or partially, within the limits. The list contains the three best allocations, sorted from most to least preferred.
     */
    public List<AllocatableResources> findBestAllocations(Load loadAdjustment, ClusterModel model, Limits limits, boolean logDetails) {
        if (limits.isEmpty()) {
            int maximumGroups = model.clusterSpec().type() == ClusterSpec.Type.container ? 1 : maximumNodes;
            limits = Limits.of(new ClusterResources(minimumNodes, 1, NodeResources.unspecified()),
                               new ClusterResources(maximumNodes, maximumGroups, NodeResources.unspecified()),
                               IntRange.empty());
        }
        else {
            limits = atLeast(minimumNodes, limits).fullySpecified(model.current().clusterSpec(), nodeRepository, model.application().id());
        }
        List<AllocatableResources> bestAllocations = new ArrayList<>();
        List<NodeResources> availableRealHostResources = availableRealHostResources(model);
        for (int groups = limits.min().groups(); groups <= limits.max().groups(); groups++) {
            for (int nodes = limits.min().nodes(); nodes <= limits.max().nodes(); nodes++) {
                if (nodes % groups != 0) continue;
                if ( ! limits.groupSize().includes(nodes / groups)) continue;
                var resources = new ClusterResources(nodes,
                                                     groups,
                                                     nodeResourcesWith(nodes, groups,
                                                                       limits, loadAdjustment, model));
                var allocatableResources = AllocatableResources.from(resources,
                                                                     model.application().id(),
                                                                     model.current().clusterSpec(),
                                                                     limits,
                                                                     availableRealHostResources,
                                                                     model,
                                                                     nodeRepository);
                if (allocatableResources.isEmpty()) continue;
                bestAllocations.add(allocatableResources.get());
                if (logDetails) {
                    log.info("Adding allocatableResources to list for " + model.application().id() + " in " + model.current().clusterSpec().id() + ": "
                             + "\n\t" + allocatableResources.get());
                }
            }
        }
        if (logDetails) {
            log.info("Found " + bestAllocations.size() + " legal allocations for " + model.application().id() + " in " + model.current().clusterSpec().id());
        }
        return bestAllocations.stream()
                .sorted((one, other) -> {
                    if (one.preferableTo(other, model))
                        return -1;
                    else if (other.preferableTo(one, model)) {
                        return 1;
                    }
                    return 0;
                })
                .limit(3)
                .toList();
    }

    private List<NodeResources> availableRealHostResources(ClusterModel model) {
        if (nodeRepository.zone().cloud().dynamicProvisioning()) {
            return nodeRepository.flavors().getFlavors().stream().map(Flavor::resources)
                                 .filter(r -> r.vcpu() >= minimumSuggestedVcpus)
                                 .toList();
        }
        return nodeRepository.nodes().list().hosts().stream().map(host -> host.flavor().resources())
                             .map(hostResources -> maxResourcesOf(hostResources, model))
                             .toList();
    }

    /** Returns the max resources of a host one node may allocate. */
    private NodeResources maxResourcesOf(NodeResources hostResources, ClusterModel model) {
        if (nodeRepository.exclusivity().allocation(model.clusterSpec())) return hostResources;
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
                                            ClusterModel model) {
        Instant now = nodeRepository.clock().instant();
        var loadWithTarget = model.loadAdjustmentWith(nodes, groups, loadAdjustment, now);

        // Leave some headroom above the ideal allocation to avoid immediately needing to scale back up
        if (loadAdjustment.cpu() < 1 && (1.0 - loadWithTarget.cpu()) < headroomRequiredToScaleDown)
            loadAdjustment = loadAdjustment.withCpu(Math.min(1.0, loadAdjustment.cpu() * (1.0 + headroomRequiredToScaleDown)));
        if (loadAdjustment.memory() < 1 && (1.0 - loadWithTarget.memory()) < headroomRequiredToScaleDown)
            loadAdjustment = loadAdjustment.withMemory(Math.min(1.0, loadAdjustment.memory() * (1.0 + headroomRequiredToScaleDown)));
        if (loadAdjustment.disk() < 1 && (1.0 - loadWithTarget.disk()) < headroomRequiredToScaleDown)
            loadAdjustment = loadAdjustment.withDisk(Math.min(1.0, loadAdjustment.disk() * (1.0 + headroomRequiredToScaleDown)));

        loadWithTarget = model.loadAdjustmentWith(nodes, groups, loadAdjustment, now);

        var scaled = loadWithTarget.scaled(model.current().realResources().nodeResources());
        var nonScaled = limits.isEmpty() || limits.min().nodeResources().isUnspecified()
                        ? model.current().advertisedResources().nodeResources()
                        : limits.min().nodeResources(); // min=max for non-scaled
        return nonScaled.withVcpu(scaled.vcpu()).withMemoryGiB(scaled.memoryGiB()).withDiskGb(scaled.diskGb());
    }

    /** Returns a copy of the given limits where the minimum nodes are at least the given value when allowed */
    private Limits atLeast(int min, Limits limits) {
        if (limits.max().nodes() < min) return limits; // not allowed
        return limits.withMin(limits.min().withNodes(Math.max(min, limits.min().nodes())));
    }

}
