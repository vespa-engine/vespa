// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.LockedNodeList;
import com.yahoo.vespa.hosted.provision.Node;

import java.util.Objects;

/**
 * Capacity calculation for docker hosts.
 * <p>
 * The calculations is based on an immutable copy of nodes that represents
 * all capacities in the system - i.e. all nodes in the node repo give or take.
 *
 * @author smorgrav
 */
public class DockerHostCapacity {

    private final LockedNodeList allNodes;

    public DockerHostCapacity(LockedNodeList allNodes) {
        this.allNodes = Objects.requireNonNull(allNodes, "allNodes must be non-null");
    }

    /**
     * Compare hosts on free capacity.
     * Used in prioritizing hosts for allocation in <b>descending</b> order.
     */
    int compare(Node hostA, Node hostB) {
        int result = compare(freeCapacityOf(hostB, false), freeCapacityOf(hostA, false));
        if (result != 0) return result;

        // If resources are equal we want to assign to the one with the most IPaddresses free
        return freeIPs(hostB) - freeIPs(hostA);
    }

    int compareWithoutInactive(Node hostA, Node hostB) {
        int result = compare(freeCapacityOf(hostB,  true), freeCapacityOf(hostA, true));
        if (result != 0) return result;

        // If resources are equal we want to assign to the one with the most IPaddresses free
        return freeIPs(hostB) - freeIPs(hostA);
    }

    private int compare(NodeResources a, NodeResources b) {
        return NodeResourceComparator.defaultOrder().compare(a, b);
    }

    /**
     * Checks the node capacity and free ip addresses to see
     * if we could allocate a flavor on the docker host.
     */
    boolean hasCapacity(Node dockerHost, NodeResources requestedCapacity) {
        return freeCapacityOf(dockerHost, false).satisfies(requestedCapacity) && freeIPs(dockerHost) > 0;
    }

    /**
     * Number of free (not allocated) IP addresses assigned to the dockerhost.
     */
    int freeIPs(Node dockerHost) {
        return dockerHost.ipAddressPool().findUnused(allNodes).size();
    }

    /** Return total free capacity for a given disk speed (or for any disk speed) */
    public NodeResources getFreeCapacityTotal(NodeResources.DiskSpeed speed) {
        return allNodes.asList().stream()
                       .filter(n -> n.type().equals(NodeType.host))
                       .filter(n -> speed == NodeResources.DiskSpeed.any || n.flavor().resources().diskSpeed() == speed)
                       .map(n -> freeCapacityOf(n, false))
                       .map(resources -> resources.withDiskSpeed(speed)) // Set speed to 'any' if necessary
                       .reduce(new NodeResources(0, 0, 0, speed), NodeResources::add);
    }

    /** Return total capacity for a given disk speed (or for any disk speed) */
    public NodeResources getCapacityTotal(NodeResources.DiskSpeed speed) {
        return allNodes.asList().stream()
                       .filter(n -> n.type().equals(NodeType.host))
                       .filter(n -> speed == NodeResources.DiskSpeed.any || n.flavor().resources().diskSpeed() == speed)
                       .map(host -> host.flavor().resources())
                       .map(resources -> resources.withDiskSpeed(speed)) // Set speed to 'any' if necessary
                       .reduce(new NodeResources(0, 0, 0, speed), NodeResources::add);
    }

    public int freeCapacityInFlavorEquivalence(Flavor flavor) {
        return allNodes.asList().stream()
                       .filter(n -> n.type().equals(NodeType.host))
                       .map(n -> canFitNumberOf(n, flavor))
                       .reduce(0, (a, b) -> a + b);
    }

    public long getNofHostsAvailableFor(Flavor flavor) {
        return allNodes.asList().stream()
                       .filter(n -> n.type().equals(NodeType.host))
                       .filter(n -> hasCapacity(n, flavor.resources()))
                       .count();
    }

    private int canFitNumberOf(Node node, Flavor flavor) {
        NodeResources freeCapacity = freeCapacityOf(node, false);
        int capacityFactor = freeCapacityInFlavorEquivalence(freeCapacity, flavor);
        int ips = freeIPs(node);
        return Math.min(capacityFactor, ips);
    }

    int freeCapacityInFlavorEquivalence(NodeResources freeCapacity, Flavor flavor) {
        if ( ! freeCapacity.satisfies(flavor.resources())) return 0;

        double cpuFactor = Math.floor(freeCapacity.vcpu() / flavor.getMinCpuCores());
        double memoryFactor = Math.floor(freeCapacity.memoryGb() / flavor.getMinMainMemoryAvailableGb());
        double diskFactor =  Math.floor(freeCapacity.diskGb() / flavor.getMinDiskAvailableGb());

        return (int) Math.min(Math.min(memoryFactor, cpuFactor), diskFactor);
    }

    /**
     * Calculate the remaining capacity for the dockerHost.
     *
     * @param dockerHost The host to find free capacity of.
     * @return A default (empty) capacity if not a docker host, otherwise the free/unallocated/rest capacity
     */
    public NodeResources freeCapacityOf(Node dockerHost, boolean includeInactive) {
        // Only hosts have free capacity
        if ( ! dockerHost.type().equals(NodeType.host)) return new NodeResources(0, 0, 0);

        // Subtract used resources without taking disk speed into account since existing allocations grandfathered in
        // may not use reflect the actual disk speed (as of May 2019). This (the 3 diskSpeed assignments below)
        // can be removed when all node allocations accurately reflect the true host disk speed
        return allNodes.childrenOf(dockerHost).asList().stream()
                .filter(node -> !(includeInactive && isInactiveOrRetired(node)))
                .map(node -> node.flavor().resources().withDiskSpeed(NodeResources.DiskSpeed.any))
                .reduce(dockerHost.flavor().resources().withDiskSpeed(NodeResources.DiskSpeed.any), NodeResources::subtract)
                .withDiskSpeed(dockerHost.flavor().resources().diskSpeed());
    }

    private boolean isInactiveOrRetired(Node node) {
        if (node.state().equals(Node.State.inactive)) return true;
        if (node.allocation().isPresent() && node.allocation().get().membership().retired()) return true;
        return false;
    }

}
