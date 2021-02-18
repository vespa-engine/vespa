// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Capacity calculation for hosts.
 *
 * The calculations are based on an immutable copy of nodes that represents
 * all capacities in the system - i.e. all nodes in the node repo.
 *
 * @author smorgrav
 */
public class HostCapacity {

    private final NodeList allNodes;
    private final HostResourcesCalculator hostResourcesCalculator;

    public HostCapacity(NodeList allNodes, HostResourcesCalculator hostResourcesCalculator) {
        this.allNodes = Objects.requireNonNull(allNodes, "allNodes must be non-null");
        this.hostResourcesCalculator = Objects.requireNonNull(hostResourcesCalculator, "hostResourcesCalculator must be non-null");
    }

    public NodeList allNodes() { return allNodes; }

    /**
     * Spare hosts are the hosts in the system with the most free capacity. A zone may reserve a minimum number of spare
     * hosts to increase the chances of having replacements for failed nodes.
     *
     * We do not count retired or inactive nodes as used capacity (as they could have been
     * moved to create space for the spare node in the first place).
     *
     * @param candidates the candidates to consider. This list may contain all kinds of nodes.
     * @param count the max number of spare hosts to return
     */
    public Set<Node> findSpareHosts(List<Node> candidates, int count) {
        return candidates.stream()
                         .filter(node -> node.type().canRun(NodeType.tenant))
                         .filter(host -> host.state() == Node.State.active)
                         .filter(host -> freeIps(host) > 0)
                         .sorted(this::compareWithoutInactive)
                         .limit(count)
                         .collect(Collectors.toSet());
    }

    public Set<Node> findSpareHostsInDynamicallyProvisionedZones(List<Node> candidates) {
        return candidates.stream()
                .filter(node -> node.type() == NodeType.host)
                .filter(host -> host.state() == Node.State.active)
                .filter(host -> allNodes.childrenOf(host).isEmpty())
                .collect(Collectors.toSet());
    }

    private int compareWithoutInactive(Node a, Node b) {
        int result = compare(freeCapacityOf(b, true), freeCapacityOf(a, true));
        if (result != 0) return result;

        // If resources are equal we want to assign to the one with the most IP addresses free
        return freeIps(b) - freeIps(a);
    }

    private int compare(NodeResources a, NodeResources b) {
        return NodeResourceComparator.defaultOrder().compare(a, b);
    }

    /** Returns whether host has room for requested capacity */
    boolean hasCapacity(Node host, NodeResources requestedCapacity) {
        return freeCapacityOf(host, false).satisfies(requestedCapacity) && freeIps(host) > 0;
    }

    /** Returns the number of available IP addresses on given host */
    int freeIps(Node host) {
        if (host.type() == NodeType.host) {
            return host.ipConfig().pool().eventuallyUnusedAddressCount(allNodes);
        } else {
            return host.ipConfig().pool().findUnusedIpAddresses(allNodes).size();
        }
    }

    /**
     * Calculate the remaining capacity of a host.
     *
     * @param host the host to find free capacity of.
     * @return a default (empty) capacity if not host, otherwise the free/unallocated/rest capacity
     */
    public NodeResources freeCapacityOf(Node host) {
        return freeCapacityOf(host, false);
    }

    NodeResources freeCapacityOf(Node host, boolean excludeInactive) {
        // Only hosts have free capacity
        if ( ! host.type().canRun(NodeType.tenant)) return new NodeResources(0, 0, 0, 0);

        NodeResources hostResources = hostResourcesCalculator.advertisedResourcesOf(host.flavor());
        return allNodes.childrenOf(host).asList().stream()
                       .filter(node -> !(excludeInactive && inactiveOrRetired(node)))
                       .map(node -> node.flavor().resources().justNumbers())
                       .reduce(hostResources.justNumbers(), NodeResources::subtract)
                       .with(host.flavor().resources().diskSpeed())
                       .with(host.flavor().resources().storageType());
    }

    private static boolean inactiveOrRetired(Node node) {
        if (node.state() == Node.State.inactive) return true;
        if (node.allocation().isPresent() && node.allocation().get().membership().retired()) return true;
        return false;
    }

}
