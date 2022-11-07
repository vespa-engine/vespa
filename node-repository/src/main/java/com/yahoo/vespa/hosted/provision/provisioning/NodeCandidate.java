// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.LockedNodeList;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.Nodelike;
import com.yahoo.vespa.hosted.provision.node.Allocation;
import com.yahoo.vespa.hosted.provision.node.IP;
import com.yahoo.vespa.hosted.provision.persistence.NameResolver;
import com.yahoo.yolean.Exceptions;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * A node candidate containing the details required to prioritize it for allocation. This is immutable.
 *
 * @author smorgrav
 */
public abstract class NodeCandidate implements Nodelike, Comparable<NodeCandidate> {

    private static final Logger log = Logger.getLogger(NodeCandidate.class.getName());

    /** List of host states ordered by preference (ascending) */
    private static final List<Node.State> HOST_STATE_PRIORITY =
            List.of(Node.State.provisioned, Node.State.ready, Node.State.active);

    private static final NodeResources zeroResources =
            new NodeResources(0, 0, 0, 0, NodeResources.DiskSpeed.any, NodeResources.StorageType.any);

    /** The free capacity on the parent of this node, before adding this node to it */
    protected final NodeResources freeParentCapacity;

    /** The parent host */
    final Optional<Node> parent;

    /** True if this node is allocated on a host that should be dedicated as a spare */
    final boolean violatesSpares;

    /** True if this node is allocated on an exclusive network switch in its cluster */
    final boolean exclusiveSwitch;

    /** True if this node belongs to a group which will not be needed after this deployment */
    final boolean isSurplus;

    /** This node does not exist in the node repository yet */
    final boolean isNew;

    /** This node can be resized to the new NodeResources */
    final boolean isResizable;

    private NodeCandidate(NodeResources freeParentCapacity, Optional<Node> parent, boolean violatesSpares, boolean exclusiveSwitch, boolean isSurplus, boolean isNew, boolean isResizeable) {
        if (isResizeable && isNew)
            throw new IllegalArgumentException("A new node cannot be resizable");

        this.freeParentCapacity = freeParentCapacity;
        this.parent = parent;
        this.violatesSpares = violatesSpares;
        this.exclusiveSwitch = exclusiveSwitch;
        this.isSurplus = isSurplus;
        this.isNew = isNew;
        this.isResizable = isResizeable;
    }

    public abstract Optional<Allocation> allocation();

    public abstract Node.State state();

    public abstract boolean wantToRetire();

    public abstract boolean preferToRetire();

    public abstract boolean wantToFail();

    public abstract Flavor flavor();

    public abstract NodeCandidate allocate(ApplicationId owner, ClusterMembership membership, NodeResources requestedResources, Instant at);

    /** Called when the node described by this candidate must be created */
    public abstract NodeCandidate withNode();

    /** Returns a copy of this with exclusive switch set to given value */
    public abstract NodeCandidate withExclusiveSwitch(boolean exclusiveSwitch);

    /**
     * Returns the node instance of this candidate, allocating it if necessary.
     *
     * @throws IllegalStateException if the node candidate is invalid
     */
    public abstract Node toNode();

    /** Returns whether this node can - as far as we know - be used to run the application workload */
    public abstract boolean isValid();

    /** Returns whether this can be replaced by any of the reserved candidates */
    public boolean replaceableBy(List<NodeCandidate> candidates) {
        return candidates.stream()
                         .filter(candidate -> candidate.state() == Node.State.reserved)
                         .anyMatch(candidate -> {
                             int switchPriority = candidate.switchPriority(this);
                             if (switchPriority < 0) {
                                 return true;
                             } else if (switchPriority > 0) {
                                 return false;
                             }
                             return candidate.hostPriority(this) < 0;
                         });
    }

    /**
     * Compare this candidate to another
     *
     * @return negative if this should be preferred over other
     */
    @Override
    public int compareTo(NodeCandidate other) {
        // First always pick nodes without violation above nodes with violations
        if (!this.violatesSpares && other.violatesSpares) return -1;
        if (!other.violatesSpares && this.violatesSpares) return 1;

        // Choose active nodes
        if (this.state() == Node.State.active && other.state() != Node.State.active) return -1;
        if (other.state() == Node.State.active && this.state() != Node.State.active) return 1;

        // Choose active node that is not retired first (surplus is active but retired)
        if (!this.isSurplus && other.isSurplus) return -1;
        if (!other.isSurplus && this.isSurplus) return 1;

        // Choose reserved nodes from a previous allocation attempt (which exist in node repo)
        if (this.isInNodeRepoAndReserved() && ! other.isInNodeRepoAndReserved()) return -1;
        if (other.isInNodeRepoAndReserved() && ! this.isInNodeRepoAndReserved()) return 1;

        // Choose nodes that are not preferred to retire
        if (!this.preferToRetire() && other.preferToRetire()) return -1;
        if (!other.preferToRetire() && this.preferToRetire()) return 1;

        // Choose inactive nodes
        if (this.state() == Node.State.inactive && other.state() != Node.State.inactive) return -1;
        if (other.state() == Node.State.inactive && this.state() != Node.State.inactive) return 1;

        // Choose ready nodes
        if (this.state() == Node.State.ready && other.state() != Node.State.ready) return -1;
        if (other.state() == Node.State.ready && this.state() != Node.State.ready) return 1;

        if (this.state() != other.state())
            throw new IllegalStateException("Nodes " + this + " and " + other + " have different states");

        if (this.parent.isPresent() && other.parent.isPresent()) {
            // Prefer reserved hosts (that they are reserved to the right tenant is ensured elsewhere)
            if ( this.parent.get().reservedTo().isPresent() && ! other.parent.get().reservedTo().isPresent()) return -1;
            if ( ! this.parent.get().reservedTo().isPresent() && other.parent.get().reservedTo().isPresent()) return 1;

            // Prefer node on exclusive switch
            int switchPriority = switchPriority(other);
            if (switchPriority != 0) return switchPriority;

            // Prefer node with cheapest storage
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

        // Prefer host with least skew
        int hostPriority = hostPriority(other);
        if (hostPriority != 0) return hostPriority;

        // Prefer node with cheapest flavor
        if (this.flavor().cost() < other.flavor().cost()) return -1;
        if (other.flavor().cost() < this.flavor().cost()) return 1;

        // Prefer node where host is in more desirable state
        int thisHostStatePri = this.parent.map(host -> HOST_STATE_PRIORITY.indexOf(host.state())).orElse(-2);
        int otherHostStatePri = other.parent.map(host -> HOST_STATE_PRIORITY.indexOf(host.state())).orElse(-2);
        if (thisHostStatePri != otherHostStatePri) return otherHostStatePri - thisHostStatePri;

        // Prefer lower indexes to minimize redistribution
        if (this.allocation().isPresent() && other.allocation().isPresent())
            return Integer.compare(this.allocation().get().membership().index(),
                                   other.allocation().get().membership().index());

        // Prefer host with latest OS version
        Version thisHostOsVersion = this.parent.flatMap(host -> host.status().osVersion().current()).orElse(Version.emptyVersion);
        Version otherHostOsVersion = other.parent.flatMap(host -> host.status().osVersion().current()).orElse(Version.emptyVersion);
        if (thisHostOsVersion.isAfter(otherHostOsVersion)) return -1;
        if (otherHostOsVersion.isAfter(thisHostOsVersion)) return 1;

        return 0;
    }

    /** Returns the allocation skew of the parent of this before adding this node to it */
    double skewWithoutThis() { return skewWith(zeroResources); }

    /** Returns the allocation skew of the parent of this after adding this node to it */
    double skewWithThis() { return skewWith(resources()); }

    /** Returns a copy of this with node set to given value */
    NodeCandidate withNode(Node node) {
        return new ConcreteNodeCandidate(node, freeParentCapacity, parent, violatesSpares, exclusiveSwitch, isSurplus, isNew, isResizable);
    }

    /** Returns the switch priority, based on switch exclusivity, of this compared to other */
    private int switchPriority(NodeCandidate other) {
        if (this.exclusiveSwitch && !other.exclusiveSwitch) return -1;
        if (other.exclusiveSwitch && !this.exclusiveSwitch) return 1;
        return 0;
    }

    /** Returns the host priority, based on allocation skew, of this compared to other */
    private int hostPriority(NodeCandidate other) {
        return Double.compare(this.skewWithThis() - this.skewWithoutThis(),
                              other.skewWithThis() - other.skewWithoutThis());
    }

    private boolean lessThanHalfTheHost(NodeCandidate node) {
        var n = node.resources();
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
        if (isNew) return false;
        return state().equals(Node.State.reserved);
    }

    public static NodeCandidate createChild(Node node,
                                            NodeResources freeParentCapacity,
                                            Node parent,
                                            boolean violatesSpares,
                                            boolean isSurplus,
                                            boolean isNew,
                                            boolean isResizeable) {
        return new ConcreteNodeCandidate(node, freeParentCapacity, Optional.of(parent), violatesSpares, true, isSurplus, isNew, isResizeable);
    }

    public static NodeCandidate createNewChild(NodeResources resources,
                                               NodeResources freeParentCapacity,
                                               Node parent,
                                               boolean violatesSpares,
                                               LockedNodeList allNodes,
                                               NameResolver nameResolver) {
        return new VirtualNodeCandidate(resources, freeParentCapacity, parent, violatesSpares, true, allNodes, nameResolver);
    }

    public static NodeCandidate createNewExclusiveChild(Node node, Node parent) {
        return new ConcreteNodeCandidate(node, node.resources(), Optional.of(parent), false, true, false, true, false);
    }

    public static NodeCandidate createStandalone(Node node, boolean isSurplus, boolean isNew) {
        return new ConcreteNodeCandidate(node, node.resources(), Optional.empty(), false, true, isSurplus, isNew, false);
    }

    /** A candidate backed by a node */
    static class ConcreteNodeCandidate extends NodeCandidate {

        private final Node node;

        ConcreteNodeCandidate(Node node, NodeResources freeParentCapacity, Optional<Node> parent,
                              boolean violatesSpares, boolean exclusiveSwitch,
                              boolean isSurplus, boolean isNew, boolean isResizeable) {
            super(freeParentCapacity, parent, violatesSpares, exclusiveSwitch, isSurplus, isNew, isResizeable);
            this.node = Objects.requireNonNull(node, "Node cannot be null");
        }

        @Override
        public NodeResources resources() { return node.resources(); }

        @Override
        public Optional<String> parentHostname() { return node.parentHostname(); }

        @Override
        public NodeType type() { return node.type(); }

        @Override
        public Optional<Allocation> allocation() { return node.allocation(); }

        @Override
        public Node.State state() { return node.state(); }

        @Override
        public boolean wantToRetire() { return node.status().wantToRetire(); }

        @Override
        public boolean preferToRetire() { return node.status().preferToRetire(); }

        @Override
        public boolean wantToFail() { return node.status().wantToFail(); }

        @Override
        public Flavor flavor() { return node.flavor(); }

        @Override
        public NodeCandidate allocate(ApplicationId owner, ClusterMembership membership, NodeResources requestedResources, Instant at) {
            return new ConcreteNodeCandidate(node.allocate(owner, membership, requestedResources, at),
                                             freeParentCapacity, parent, violatesSpares, exclusiveSwitch, isSurplus, isNew, isResizable);
        }

        /** Called when the node described by this candidate must be created */
        @Override
        public NodeCandidate withNode() { return this; }

        @Override
        public NodeCandidate withExclusiveSwitch(boolean exclusiveSwitch) {
            return new ConcreteNodeCandidate(node, freeParentCapacity, parent, violatesSpares, exclusiveSwitch,
                                             isSurplus, isNew, isResizable);
        }

        @Override
        public Node toNode() { return node; }

        @Override
        public boolean isValid() { return true; }

        @Override
        public int compareTo(NodeCandidate other) {
            int comparison = super.compareTo(other);
            if (comparison != 0) return comparison;

            // Unimportant tie-breaking:
            if ( ! (other instanceof ConcreteNodeCandidate)) return -1;
            return this.node.hostname().compareTo(((ConcreteNodeCandidate)other).node.hostname());
        }

        @Override
        public String toString() {
            return node.id();
        }

    }

    /** A candidate for which no actual node has been created yet */
    static class VirtualNodeCandidate extends NodeCandidate {

        /** The resources this node must have if created */
        private final NodeResources resources;

        /** Needed to construct the node */
        private final LockedNodeList allNodes;
        private final NameResolver nameResolver;

        private VirtualNodeCandidate(NodeResources resources,
                                     NodeResources freeParentCapacity,
                                     Node parent,
                                     boolean violatesSpares,
                                     boolean exclusiveSwitch,
                                     LockedNodeList allNodes,
                                     NameResolver nameResolver) {
            super(freeParentCapacity, Optional.of(parent), violatesSpares, exclusiveSwitch, false, true, false);
            this.resources = resources;
            this.allNodes = allNodes;
            this.nameResolver = nameResolver;
        }

        @Override
        public NodeResources resources() { return resources; }

        @Override
        public Optional<String> parentHostname() { return Optional.of(parent.get().hostname()); }

        @Override
        public NodeType type() { return NodeType.tenant; }

        @Override
        public Optional<Allocation> allocation() { return Optional.empty(); }

        @Override
        public Node.State state() { return Node.State.reserved; }

        @Override
        public boolean wantToRetire() { return false; }

        @Override
        public boolean preferToRetire() { return false; }

        @Override
        public boolean wantToFail() { return false; }

        @Override
        public Flavor flavor() { return new Flavor(resources); }

        @Override
        public NodeCandidate allocate(ApplicationId owner, ClusterMembership membership, NodeResources requestedResources, Instant at) {
            return withNode().allocate(owner, membership, requestedResources, at);
        }

        @Override
        public NodeCandidate withNode() {
            Optional<IP.Allocation> allocation;
            try {
                allocation = parent.get().ipConfig().pool().findAllocation(allNodes, nameResolver);
                if (allocation.isEmpty()) return new InvalidNodeCandidate(resources, freeParentCapacity, parent.get(),
                                                                          "No addresses available on parent host");
            } catch (Exception e) {
                log.warning("Failed allocating address on " + parent.get() +": " + Exceptions.toMessageString(e));
                return new InvalidNodeCandidate(resources, freeParentCapacity, parent.get(),
                                                "Failed when allocating address on host");
            }

            Node node = Node.reserve(allocation.get().addresses(),
                                     allocation.get().hostname(),
                                     parentHostname().get(),
                                     resources.with(parent.get().resources().diskSpeed())
                                              .with(parent.get().resources().storageType())
                                              .with(parent.get().resources().architecture()),
                                     NodeType.tenant)
                            .cloudAccount(parent.get().cloudAccount())
                            .build();
            return new ConcreteNodeCandidate(node, freeParentCapacity, parent, violatesSpares, exclusiveSwitch, isSurplus, isNew, isResizable);

        }

        @Override
        public NodeCandidate withExclusiveSwitch(boolean exclusiveSwitch) {
            return new VirtualNodeCandidate(resources, freeParentCapacity, parent.get(), violatesSpares, exclusiveSwitch, allNodes, nameResolver);
        }

        @Override
        public Node toNode() { return withNode().toNode(); }

        @Override
        public boolean isValid() { return true; }

        @Override
        public int compareTo(NodeCandidate other) {
            int comparison = super.compareTo(other);
            if (comparison != 0) return comparison;

            // Unimportant tie-breaking:
            if ( ! (other instanceof VirtualNodeCandidate)) return 1;
            return this.parentHostname().get().compareTo(other.parentHostname().get());
        }

        @Override
        public String toString() {
            return "candidate node with " + resources + " on " + parent.get();
        }

    }

    /**
     * A candidate which failed to transition from virtual to concrete.
     * It will silently stay invalid no matter which method is called on it.
     */
    static class InvalidNodeCandidate extends NodeCandidate {

        private final NodeResources resources;
        private final String invalidReason;

        private InvalidNodeCandidate(NodeResources resources, NodeResources freeParentCapacity, Node parent,
                                     String invalidReason) {
            super(freeParentCapacity, Optional.of(parent), false, false, false, true, false);
            this.resources = resources;
            this.invalidReason = invalidReason;
        }

        @Override
        public NodeResources resources() { return resources; }

        @Override
        public Optional<String> parentHostname() { return Optional.of(parent.get().hostname()); }

        @Override
        public NodeType type() { return NodeType.tenant; }

        @Override
        public Optional<Allocation> allocation() { return Optional.empty(); }

        @Override
        public Node.State state() { return Node.State.reserved; }

        @Override
        public boolean wantToRetire() { return false; }

        @Override
        public boolean preferToRetire() { return false; }

        @Override
        public boolean wantToFail() { return false; }

        @Override
        public Flavor flavor() { return new Flavor(resources); }

        @Override
        public NodeCandidate allocate(ApplicationId owner, ClusterMembership membership, NodeResources requestedResources, Instant at) {
            return this;
        }

        @Override
        public NodeCandidate withNode() {
            return this;
        }

        @Override
        public NodeCandidate withExclusiveSwitch(boolean exclusiveSwitch) {
            return this;
        }

        @Override
        public Node toNode() {
            throw new IllegalStateException("Candidate node on " + parent.get() + " is invalid: " + invalidReason);
        }

        @Override
        public boolean isValid() { return false; }

        @Override
        public int compareTo(NodeCandidate other) {
            return 1;
        }

        @Override
        public String toString() {
            return "invalid candidate node with " + resources + " on " + parent.get();
        }

    }

}
