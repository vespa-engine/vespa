// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodesAndHosts;

import java.util.ArrayList;
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

    private final NodesAndHosts<? extends NodeList> allNodes;
    private final HostResourcesCalculator hostResourcesCalculator;

    public HostCapacity(NodeList allNodes, HostResourcesCalculator hostResourcesCalculator) {
        this(NodesAndHosts.create(Objects.requireNonNull(allNodes, "allNodes must be non-null")), hostResourcesCalculator);
    }
    public HostCapacity(NodesAndHosts<? extends NodeList> allNodes, HostResourcesCalculator hostResourcesCalculator) {
        this.allNodes = Objects.requireNonNull(allNodes, "allNodes must be non-null");
        this.hostResourcesCalculator = Objects.requireNonNull(hostResourcesCalculator, "hostResourcesCalculator must be non-null");
    }

    public NodeList allNodes() { return allNodes.nodes(); }

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
        ArrayList<NodeWithHostResources> nodesWithIp = new ArrayList<>(candidates.size());
        for (Node node : candidates) {
            if (node.type().canRun(NodeType.tenant) && (node.state() == Node.State.active) && node.reservedTo().isEmpty()) {
                int numFreeIps = freeIps(node);
                if (numFreeIps > 0) {
                    nodesWithIp.add(new NodeWithHostResources(node, availableCapacityOf(node, true, false), numFreeIps));
                }
            }
        }
        return nodesWithIp.stream().sorted().limit(count).map(n -> n.node).collect(Collectors.toSet());
    }
    private static class NodeWithHostResources implements Comparable<NodeWithHostResources> {
        private final Node node;
        private final NodeResources hostResources;
        private final int numFreeIps;
        NodeWithHostResources(Node node, NodeResources hostResources, int freeIps) {
            this.node = node;
            this.hostResources = hostResources;
            this.numFreeIps = freeIps;
        }

        @Override
        public int compareTo(HostCapacity.NodeWithHostResources b) {
            int result = compare(b.hostResources, hostResources);
            if (result != 0) return result;
            return b.numFreeIps - numFreeIps;
        }
    }

    public Set<Node> findSpareHostsInDynamicallyProvisionedZones(List<Node> candidates) {
        return candidates.stream()
                .filter(node -> node.type() == NodeType.host)
                .filter(host -> host.state() == Node.State.active)
                .filter(host -> host.reservedTo().isEmpty())
                .filter(host -> allNodes.childrenOf(host).isEmpty())
                .collect(Collectors.toSet());
    }

    private static int compare(NodeResources a, NodeResources b) {
        return NodeResourceComparator.defaultOrder().compare(a, b);
    }

    /** Returns whether host can allocate a node with requested capacity */
    public boolean hasCapacity(Node host, NodeResources requestedCapacity) {
        return availableCapacityOf(host).satisfies(requestedCapacity);
    }

    /** Returns the number of available IP addresses on given host */
    int freeIps(Node host) {
        if (host.type() == NodeType.host) {
            return (allNodes.eventuallyUnusedIpAddressCount(host));
        }
        return host.ipConfig().pool().findUnusedIpAddresses(allNodes.nodes()).size();
    }

    /** Returns the capacity of given host that is both free and usable */
    public NodeResources availableCapacityOf(Node host) {
        return availableCapacityOf(host, false, true);
    }

    /**
     * Calculate the unused capacity of given host.
     *
     * Note that unlike {@link #availableCapacityOf(Node)}, this only considers resources and returns any unused
     * capacity even if the host does not have available IP addresses.
     *
     * @param host the host to find free capacity of
     * @return a default (empty) capacity if not host, otherwise the free capacity
     */
    public NodeResources unusedCapacityOf(Node host) {
        return availableCapacityOf(host, false, false);
    }

    private NodeResources availableCapacityOf(Node host, boolean excludeInactive, boolean requireIps) {
        // Only hosts have free capacity
        if ( ! host.type().canRun(NodeType.tenant)) return NodeResources.zero();
        if (   requireIps && freeIps(host) == 0) return NodeResources.zero();

        NodeResources hostResources = hostResourcesCalculator.advertisedResourcesOf(host.flavor());
        return allNodes.childrenOf(host).stream()
                       .filter(node -> !(excludeInactive && inactiveOrRetired(node)))
                       .map(node -> node.flavor().resources().justNumbers())
                       .reduce(hostResources.justNumbers(), NodeResources::subtract)
                       .with(host.flavor().resources().diskSpeed())
                       .with(host.flavor().resources().storageType())
                       .with(host.flavor().resources().architecture());
    }

    private static boolean inactiveOrRetired(Node node) {
        if (node.state() == Node.State.inactive) return true;
        if (node.allocation().isPresent() && node.allocation().get().membership().retired()) return true;
        return false;
    }

}
