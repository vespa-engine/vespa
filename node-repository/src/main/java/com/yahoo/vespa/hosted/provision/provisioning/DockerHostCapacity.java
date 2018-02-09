// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Capacity calculation for docker hosts.
 * <p>
 * The calculations is based on an immutable copy of nodes that represents
 * all capacities in the system - i.e. all nodes in the node repo give or take.
 *
 * @author smorgrav
 */
public class DockerHostCapacity {

    /** Tenant name for headroom nodes - only used internally */
    public static final String HEADROOM_TENANT = "-__!@#$$%THISisHEADroom";

    /**
     * An immutable list of nodes
     */
    private final NodeList allNodes;

    public DockerHostCapacity(List<Node> allNodes) {
        this.allNodes = new NodeList(allNodes);
    }

    /**
     * Compare hosts on free capacity.
     * <p>
     * Used in prioritizing hosts for allocation in <b>descending</b> order.
     */
    int compare(Node hostA, Node hostB) {
        int comp = freeCapacityOf(hostB, false).compare(freeCapacityOf(hostA, false));
        if (comp == 0) {
            comp = freeCapacityOf(hostB, false).compare(freeCapacityOf(hostA, false));
            if (comp == 0) {
                // If resources are equal - we want to assign to the one with the most IPaddresses free
                comp = freeIPs(hostB) - freeIPs(hostA);
            }
        }
        return comp;
    }

    int compareWithoutInactive(Node hostA, Node hostB) {
        int comp = freeCapacityOf(hostB,  true).compare(freeCapacityOf(hostA, true));
        if (comp == 0) {
            comp = freeCapacityOf(hostB, true).compare(freeCapacityOf(hostA, true));
            if (comp == 0) {
                // If resources are equal - we want to assign to the one with the most IPaddresses free
                comp = freeIPs(hostB) - freeIPs(hostA);
            }
        }
        return comp;
    }

    /**
     * Checks the node capacity and free ip addresses to see
     * if we could allocate a flavor on the docker host.
     */
    boolean hasCapacity(Node dockerHost, ResourceCapacity requestedCapacity) {
        return freeCapacityOf(dockerHost, false).hasCapacityFor(requestedCapacity) && freeIPs(dockerHost) > 0;
    }

    boolean hasCapacityWhenRetiredAndInactiveNodesAreGone(Node dockerHost, ResourceCapacity requestedCapacity) {
        return freeCapacityOf(dockerHost, true).hasCapacityFor(requestedCapacity) && freeIPs(dockerHost) > 0;
    }

    /**
     * Number of free (not allocated) IP addresses assigned to the dockerhost.
     */
    int freeIPs(Node dockerHost) {
        return findFreeIps(dockerHost, allNodes.asList()).size();
    }

    public ResourceCapacity getFreeCapacityTotal() {
        return allNodes.asList().stream()
                .filter(n -> n.type().equals(NodeType.host))
                .map(n -> freeCapacityOf(n, false))
                .reduce(new ResourceCapacity(), ResourceCapacity::add);
    }

    public ResourceCapacity getCapacityTotal() {
        return allNodes.asList().stream()
                .filter(n -> n.type().equals(NodeType.host))
                .map(ResourceCapacity::new)
                .reduce(new ResourceCapacity(), ResourceCapacity::add);
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
                .filter(n -> hasCapacity(n, ResourceCapacity.of(flavor)))
                .count();
    }

    private int canFitNumberOf(Node node, Flavor flavor) {
        int capacityFactor = freeCapacityOf(node, false).freeCapacityInFlavorEquivalence(flavor);
        int ips = freeIPs(node);
        return Math.min(capacityFactor, ips);
    }

    /**
     * Calculate the remaining capacity for the dockerHost.
     * @param dockerHost The host to find free capacity of.
     *
     * @return A default (empty) capacity if not a docker host, otherwise the free/unallocated/rest capacity
     */
    public ResourceCapacity freeCapacityOf(Node dockerHost, boolean treatInactiveOrRetiredAsUnusedCapacity) {
        // Only hosts have free capacity
        if (!dockerHost.type().equals(NodeType.host)) return new ResourceCapacity();

        ResourceCapacity hostCapacity = new ResourceCapacity(dockerHost);
        for (Node container : allNodes.childrenOf(dockerHost).asList()) {
            boolean isUsedCapacity = !(treatInactiveOrRetiredAsUnusedCapacity && isInactiveOrRetired(container));
            if (isUsedCapacity) {
                hostCapacity.subtract(container);
            }
        }
        return hostCapacity;
    }

    private boolean isInactiveOrRetired(Node node) {
        boolean isInactive = node.state().equals(Node.State.inactive);
        boolean isRetired = false;
        if (node.allocation().isPresent()) {
            isRetired = node.allocation().get().membership().retired();
        }

        return isInactive || isRetired;
    }

    /**
     * Compare the additional ip addresses against the set of used addresses on
     * child nodes.
     */
    static Set<String> findFreeIps(Node dockerHost, List<Node> allNodes) {
        Set<String> freeIPAddresses = new HashSet<>(dockerHost.additionalIpAddresses());
        for (Node child : new NodeList(allNodes).childrenOf(dockerHost).asList()) {
            freeIPAddresses.removeAll(child.ipAddresses());
        }
        return freeIPAddresses;
    }
}
