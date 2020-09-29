// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.Nodelike;
import com.yahoo.vespa.hosted.provision.node.Allocation;
import com.yahoo.vespa.hosted.provision.node.Status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * A node candidate containing the details required to prioritize it for allocation. This is immutable.
 *
 * @author smorgrav
 */
class NodeCandidate implements Nodelike, Comparable<NodeCandidate> {

    /** List of host states ordered by preference (ascending) */
    private static final List<Node.State> HOST_STATE_PRIORITY =
            List.of(Node.State.provisioned, Node.State.ready, Node.State.active);

    private static final NodeResources zeroResources =
            new NodeResources(0, 0, 0, 0, NodeResources.DiskSpeed.any, NodeResources.StorageType.any);

    final Node node;

    /** The free capacity on the parent of this node, before adding this node to it */
    private final NodeResources freeParentCapacity;

    /** The parent host (docker or hypervisor) */
    final Optional<Node> parent;

    /** True if the node is allocated to a host that should be dedicated as a spare */
    final boolean violatesSpares;

    /** True if this node belongs to a group which will not be needed after this deployment */
    final boolean isSurplusNode;

    /** This node does not exist in the node repository yet */
    final boolean isNewNode;

    /** This node can be resized to the new NodeResources */
    final boolean isResizable;

    NodeCandidate(Node node, NodeResources freeParentCapacity, Optional<Node> parent, boolean violatesSpares, boolean isSurplusNode, boolean isNewNode, boolean isResizeable) {
        if (isResizeable && isNewNode)
            throw new IllegalArgumentException("A new node cannot be resizable");

        this.node = node;
        this.freeParentCapacity = freeParentCapacity;
        this.parent = parent;
        this.violatesSpares = violatesSpares;
        this.isSurplusNode = isSurplusNode;
        this.isNewNode = isNewNode;
        this.isResizable = isResizeable;
    }

    @Override
    public NodeResources resources() { return node.resources(); }

    @Override
    public Optional<String> parentHostname() { return node.parentHostname(); }

    @Override
    public NodeType type() { return node.type(); }

    public Optional<Allocation> allocation() { return node.allocation(); }

    public Node.State state() { return node.state(); }

    public Status status() { return node.status(); }

    public Flavor flavor() { return node.flavor(); }

    public NodeCandidate allocate(ApplicationId owner, ClusterMembership membership, NodeResources requestedResources, Instant at) {
        return new NodeCandidate(node.allocate(owner, membership, requestedResources, at),
                                 freeParentCapacity, parent, violatesSpares, isSurplusNode, isNewNode, isResizable);
    }

    /**
     * Compare this candidate to another
     *
     * @return negative if first priority is higher than second node
     */
    @Override
    public int compareTo(NodeCandidate other) {
        // First always pick nodes without violation above nodes with violations
        if (!this.violatesSpares && other.violatesSpares) return -1;
        if (!other.violatesSpares && this.violatesSpares) return 1;

        // Choose active nodes
        if (this.node.state() == Node.State.active && other.node.state() != Node.State.active) return -1;
        if (other.node.state() == Node.State.active && this.node.state() != Node.State.active) return 1;

        // Choose active node that is not retired first (surplus is active but retired)
        if (!this.isSurplusNode && other.isSurplusNode) return -1;
        if (!other.isSurplusNode && this.isSurplusNode) return 1;

        // Choose reserved nodes from a previous allocation attempt (the exist in node repo)
        if (this.isInNodeRepoAndReserved() && ! other.isInNodeRepoAndReserved()) return -1;
        if (other.isInNodeRepoAndReserved() && ! this.isInNodeRepoAndReserved()) return 1;

        // Choose inactive nodes
        if (this.node.state() == Node.State.inactive && other.node.state() != Node.State.inactive) return -1;
        if (other.node.state() == Node.State.inactive && this.node.state() != Node.State.inactive) return 1;

        // Choose ready nodes
        if (this.node.state() == Node.State.ready && other.node.state() != Node.State.ready) return -1;
        if (other.node.state() == Node.State.ready && this.node.state() != Node.State.ready) return 1;

        if (this.node.state() != other.node.state())
            throw new IllegalStateException("Nodes " + this.node + " and " + other.node + " have different states");

        if (this.parent.isPresent() && other.parent.isPresent()) {
            // Prefer reserved hosts (that they are reserved to the right tenant is ensured elsewhere)
            if ( this.parent.get().reservedTo().isPresent() && ! other.parent.get().reservedTo().isPresent()) return -1;
            if ( ! this.parent.get().reservedTo().isPresent() && other.parent.get().reservedTo().isPresent()) return 1;

            int diskCostDifference = NodeResources.DiskSpeed.compare(this.parent.get().flavor().resources().diskSpeed(),
                                                                     other.parent.get().flavor().resources().diskSpeed());
            if (diskCostDifference != 0)
                return diskCostDifference;

            int storageCostDifference = NodeResources.StorageType.compare(this.parent.get().flavor().resources().storageType(),
                                                                          other.parent.get().flavor().resources().storageType());
            if (storageCostDifference != 0)
                return storageCostDifference;

            // Prefer hosts that are at least twice the size of this node
            // (utilization is more even if one application does not dominate the host)
            if ( lessThanHalfTheHost(this) && ! lessThanHalfTheHost(other)) return -1;
            if ( ! lessThanHalfTheHost(this) && lessThanHalfTheHost(other)) return 1;
        }

        int hostPriority = Double.compare(this.skewWithThis() - this.skewWithoutThis(),
                                          other.skewWithThis() - other.skewWithoutThis());
        if (hostPriority != 0) return hostPriority;

        // Choose cheapest node
        if (this.node.flavor().cost() < other.node.flavor().cost()) return -1;
        if (other.node.flavor().cost() < this.node.flavor().cost()) return 1;

        // Choose nodes where host is in more desirable state
        int thisHostStatePri = this.parent.map(host -> HOST_STATE_PRIORITY.indexOf(host.state())).orElse(-2);
        int otherHostStatePri = other.parent.map(host -> HOST_STATE_PRIORITY.indexOf(host.state())).orElse(-2);
        if (thisHostStatePri != otherHostStatePri) return otherHostStatePri - thisHostStatePri;

        // Prefer lower indexes to minimize redistribution
        if (this.node.allocation().isPresent() && other.node.allocation().isPresent())
            return Integer.compare(this.node.allocation().get().membership().index(),
                                   other.node.allocation().get().membership().index());

        // All else equal choose hostname alphabetically
        return this.node.hostname().compareTo(other.node.hostname());
    }

    /** Returns the allocation skew of the parent of this before adding this node to it */
    double skewWithoutThis() { return skewWith(zeroResources); }

    /** Returns the allocation skew of the parent of this after adding this node to it */
    double skewWithThis() { return skewWith(node.resources()); }

    /** Returns a copy of this with node set to given value */
    NodeCandidate withNode(Node node) {
        return new NodeCandidate(node, freeParentCapacity, parent, violatesSpares, isSurplusNode, isNewNode, isResizable);
    }

    private boolean lessThanHalfTheHost(NodeCandidate node) {
        var n = node.node.resources();
        var h = node.parent.get().resources();
        if (h.vcpu()     < n.vcpu()     * 2) return false;
        if (h.memoryGb() < n.memoryGb() * 2) return false;
        if (h.diskGb()   < n.diskGb()   * 2) return false;
        return true;
    }

    private double skewWith(NodeResources resources) {
        if (parent.isEmpty()) return 0;

        NodeResources free = freeParentCapacity.justNumbers().subtract(resources.justNumbers());
        return Node.skew(parent.get().flavor().resources(), free);
    }

    private boolean isInNodeRepoAndReserved() {
        if (isNewNode) return false;
        return node.state().equals(Node.State.reserved);
    }

    @Override
    public String toString() {
        return node.id();
    }

    @Override
    public int hashCode() {
        return node.hashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) return true;
        if ( ! (other instanceof NodeCandidate)) return false;
        return this.node.equals(((NodeCandidate)other).node);
    }

    static class Builder {

        public final Node node;
        private NodeResources freeParentCapacity;
        private Optional<Node> parent = Optional.empty();
        private boolean violatesSpares;
        private boolean isSurplusNode;
        private boolean isNewNode;
        private boolean isResizable;

        Builder(Node node) {
            this.node = node;
            this.freeParentCapacity = node.flavor().resources();
        }

        /** The free capacity of the parent, before adding this node to it */
        Builder freeParentCapacity(NodeResources freeParentCapacity) {
            this.freeParentCapacity = freeParentCapacity;
            return this;
        }

        Builder parent(Node parent) {
            this.parent = Optional.of(parent);
            return this;
        }

        Builder violatesSpares(boolean violatesSpares) {
            this.violatesSpares = violatesSpares;
            return this;
        }

        Builder surplusNode(boolean surplusNode) {
            isSurplusNode = surplusNode;
            return this;
        }

        Builder newNode(boolean newNode) {
            isNewNode = newNode;
            return this;
        }

        Builder resizable(boolean resizable) {
            isResizable = resizable;
            return this;
        }
        
        NodeCandidate build() {
            return new NodeCandidate(node, freeParentCapacity, parent, violatesSpares, isSurplusNode, isNewNode, isResizable);
        }
    }

}
