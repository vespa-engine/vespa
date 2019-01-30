// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision;

import com.google.common.collect.ImmutableSet;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.Allocation;
import com.yahoo.vespa.hosted.provision.node.Generation;
import com.yahoo.vespa.hosted.provision.node.History;
import com.yahoo.vespa.hosted.provision.node.IP;
import com.yahoo.vespa.hosted.provision.node.Reports;
import com.yahoo.vespa.hosted.provision.node.Status;

import java.time.Instant;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A node in the node repository. The identity of a node is given by its id.
 * The classes making up the node model are found in the node package.
 * This (and hence all classes referenced from it) is immutable.
 *
 * @author bratseth
 * @author mpolden
 */
public final class Node {

    private final Set<String> ipAddresses;
    private final IP.AddressPool ipAddressPool;
    private final String hostname;
    private final String id;
    private final Optional<String> parentHostname;
    private final Flavor flavor;
    private final Status status;
    private final State state;
    private final NodeType type;
    private final Reports reports;

    /** Record of the last event of each type happening to this node */
    private final History history;

    /** The current allocation of this node, if any */
    private Optional<Allocation> allocation;

    /** Temporary method until we can merge it with the other create method */
    public static Node createDockerNode(Set<String> ipAddresses, Set<String> ipAddressPool, String hostname, String parentHostname, Flavor flavor, NodeType type) {
        return new Builder("fake-" + hostname, ipAddresses, hostname, flavor, type)
                .withIpAddressPool(ipAddressPool)
                .withParentHostname(parentHostname)
                .withState(State.reserved)
                .build();
    }

    /** Creates a node in the initial state (provisioned) */
    public static Node create(String openStackId, Set<String> ipAddresses, Set<String> ipAddressPool, String hostname, Optional<String> parentHostname, Flavor flavor, NodeType type) {
        return new Builder(openStackId, ipAddresses, hostname, flavor, type)
                .withIpAddressPool(ipAddressPool)
                .withParentHostname(parentHostname)
                .build();
    }

    /**
     * Creates a node with all fields specified, necessary for serialization code.
     *
     * Others should use the {@code create} methods to create nodes, or the Builder to modify nodes.
     */
    public Node(String id, Set<String> ipAddresses, Set<String> ipAddressPool, String hostname, Optional<String> parentHostname,
                Flavor flavor, Status status, State state, Optional<Allocation> allocation, History history, NodeType type,
                Reports reports) {
        Objects.requireNonNull(id, "A node must have an ID");
        requireNonEmpty(ipAddresses, "A node must have at least one valid IP address");
        requireNonEmptyString(hostname, "A node must have a hostname");
        requireNonEmptyString(parentHostname, "A parent host name must be a proper value");
        Objects.requireNonNull(flavor, "A node must have a flavor");
        Objects.requireNonNull(status, "A node must have a status");
        Objects.requireNonNull(state, "A null node state is not permitted");
        Objects.requireNonNull(allocation, "A null node allocation is not permitted");
        Objects.requireNonNull(history, "A null node history is not permitted");
        Objects.requireNonNull(type, "A null node type is not permitted");
        Objects.requireNonNull(reports, "A null reports is not permitted");

        this.ipAddresses = ImmutableSet.copyOf(ipAddresses);
        this.ipAddressPool = new IP.AddressPool(this, ipAddressPool);
        this.hostname = hostname;
        this.parentHostname = parentHostname;
        this.id = id;
        this.flavor = flavor;
        this.status = status;
        this.state = state;
        this.allocation = allocation;
        this.history = history;
        this.type = type;
        this.reports = reports;
    }

    /** Helper for creating and mutating node objects. */
    private static class Builder {
        private final String hostname;

        // Required but mutable fields
        private String id;
        private NodeType type;
        private Flavor flavor;
        private Set<String> ipAddresses;

        private Set<String> ipAddressPool = Collections.emptySet();
        private Optional<String> parentHostname = Optional.empty();
        private Status status = Status.initial();
        private State state = State.provisioned;
        private History history = History.empty();
        private Optional<Allocation> allocation = Optional.empty();
        private Reports reports = new Reports();

        /** Creates a builder fairly well suited for a newly provisioned node (but see {@code create} and {@code createDockerNode}). */
        private Builder(String id, Set<String> ipAddresses, String hostname, Flavor flavor, NodeType type) {
            this.id = id;
            this.ipAddresses = ipAddresses;
            this.hostname = hostname;
            this.flavor = flavor;
            this.type = type;
        }

        private Builder(Node node) {
            this(node.id, node.ipAddresses, node.hostname, node.flavor, node.type);
            withIpAddressPool(node.ipAddressPool.asSet());
            node.parentHostname.ifPresent(this::withParentHostname);
            withStatus(node.status);
            withState(node.state);
            withHistory(node.history);
            node.allocation.ifPresent(this::withAllocation);
        }

        public Builder withId(String id) {
            this.id = id;
            return this;
        }

        public Builder withType(NodeType nodeType) {
            this.type = nodeType;
            return this;
        }

        public Builder withFlavor(Flavor flavor) {
            this.flavor = flavor;
            return this;
        }

        public Builder withIpAddresses(Set<String> ipAddresses) {
            this.ipAddresses = ipAddresses;
            return this;
        }

        public Builder withIpAddressPool(Set<String> ipAddressPool) {
            this.ipAddressPool = ipAddressPool;
            return this;
        }

        public Builder withParentHostname(String parentHostname) {
            this.parentHostname = Optional.of(parentHostname);
            return this;
        }

        public Builder withParentHostname(Optional<String> parentHostname) {
            parentHostname.ifPresent(this::withParentHostname);
            return this;
        }

        public Builder withStatus(Status status) {
            this.status = status;
            return this;
        }

        public Builder withState(State state) {
            this.state = state;
            return this;
        }

        public Builder withHistory(History history) {
            this.history = history;
            return this;
        }

        public Builder withHistoryEvent(History.Event.Type type, Agent agent, Instant at) {
            return withHistory(history.with(new History.Event(type, agent, at)));
        }

        public Builder withAllocation(Allocation allocation) {
            this.allocation = Optional.of(allocation);
            return this;
        }

        public Builder withReports(Reports reports) {
            this.reports = reports;
            return this;
        }

        public Node build() {
            return new Node(id, ipAddresses, ipAddressPool, hostname, parentHostname, flavor, status,
                    state, allocation, history, type, reports);
        }
    }

    /** Returns the IP addresses of this node */
    public Set<String> ipAddresses() { return ipAddresses; }

    /** Returns the IP address pool available on this node. These IP addresses are available for use by containers
     * running on this node */
    public IP.AddressPool ipAddressPool() { return ipAddressPool; }

    /** Returns the host name of this node */
    public String hostname() { return hostname; }

    /**
     * Unique identifier for this node. Code should not depend on this as its main purpose is to aid human operators in
     * mapping a node to the corresponding cloud instance. No particular format is enforced.
     *
     * Formats used vary between the underlying cloud providers:
     *
     * - OpenStack: UUID
     * - AWS: Instance ID
     * - Docker containers: fake-[hostname]
     */
    public String id() { return id; }

    /** Returns the parent hostname for this node if this node is a docker container or a VM (i.e. it has a parent host). Otherwise, empty **/
    public Optional<String> parentHostname() { return parentHostname; }

    /** Returns the flavor of this node */
    public Flavor flavor() { return flavor; }

    /** Returns the known information about the node's ephemeral status */
    public Status status() { return status; }

    /** Returns the current state of this node (in the node state machine) */
    public State state() { return state; }

    /** Returns the type of this node */
    public NodeType type() { return type; }

    /** Returns the current allocation of this, if any */
    public Optional<Allocation> allocation() { return allocation; }

    /** Returns the current allocation when it must exist, or throw exception there is not allocation. */
    private Allocation requireAllocation(String message) {
        final Optional<Allocation> allocation = this.allocation;
        if ( ! allocation.isPresent())
            throw new IllegalStateException(message + " for  " + hostname() + ": The node is unallocated");

        return allocation.get();
    }

    /** Returns a history of the last events happening to this node */
    public History history() { return history; }

    /** Returns all the reports on this node. */
    public Reports reports() { return reports; }

    /**
     * Returns a copy of this node with wantToRetire set to the given value and updated history.
     * If given wantToRetire is equal to the current, the method is no-op.
     */
    public Node withWantToRetire(boolean wantToRetire, Instant at) {
        if (wantToRetire == status.wantToRetire()) return this;
        return new Builder(this)
                .withStatus(status.withWantToRetire(wantToRetire))
                // Also update history when we un-wantToRetire so the OperatorChangeApplicationMaintainer picks it
                // up quickly
                .withHistoryEvent(History.Event.Type.wantToRetire, Agent.operator, at)
                .build();
    }

    /**
     * Returns a copy of this node which is retired.
     * If the node was already retired it is returned as-is.
     */
    public Node retire(Agent agent, Instant retiredAt) {
        Allocation allocation = requireAllocation("Cannot retire");
        if (allocation.membership().retired()) return this;
        return new Builder(this)
                .withAllocation(allocation.retire())
                .withHistoryEvent(History.Event.Type.retired, agent, retiredAt)
                .build();
    }

    /** Returns a copy of this node which is retired */
    public Node retire(Instant retiredAt) {
        if (flavor.isRetired() || status.wantToRetire())
            return retire(Agent.system, retiredAt);
        else
            return retire(Agent.application, retiredAt);
    }

    /** Returns a copy of this node which is not retired */
    public Node unretire() {
        return with(requireAllocation("Cannot unretire").unretire());
    }

    /** Returns a copy of this with the restart generation set to generation */
    public Node withRestart(Generation generation) {
        Allocation allocation = requireAllocation("Cannot set restart generation");
        return new Builder(this).withAllocation(allocation.withRestart(generation)).build();
    }

    /** Returns a node with the status assigned to the given value */
    public Node with(Status status) {
        return new Builder(this).withStatus(status).build();
    }

    /** Returns a node with the type assigned to the given value */
    public Node with(NodeType type) {
        return new Builder(this).withType(type).build();
    }

    /** Returns a node with the flavor assigned to the given value */
    public Node with(Flavor flavor) {
        return new Builder(this).withFlavor(flavor).build();
    }

    /** Returns a copy of this with the reboot generation set to generation */
    public Node withReboot(Generation generation) {
        return new Builder(this).withStatus(status.withReboot(generation)).build();
    }

    /** Returns a copy of this with the openStackId set */
    public Node withOpenStackId(String openStackId) {
        return new Builder(this).withId(openStackId).build();
    }

    /** Returns a copy of this with a history record saying it was detected to be down at this instant */
    public Node downAt(Instant instant) {
        return new Builder(this).withHistoryEvent(History.Event.Type.down, Agent.system, instant).build();
    }

    /** Returns a copy of this with any history record saying it has been detected down removed */
    public Node up() {
        return new Builder(this).withHistory(history.without(History.Event.Type.down)).build();
    }

    /** Returns a copy of this with allocation set as specified. <code>node.state</code> is *not* changed. */
    public Node allocate(ApplicationId owner, ClusterMembership membership, Instant at) {
        return new Builder(this)
                .withAllocation(new Allocation(owner, membership, new Generation(0, 0), false))
                .withHistoryEvent(History.Event.Type.reserved, Agent.application, at)
                .build();
    }

    /**
     * Returns a copy of this node with the allocation assigned to the given allocation.
     * Do not use this to allocate a node.
     */
    public Node with(Allocation allocation) {
        return new Builder(this).withAllocation(allocation).build();
    }

    /** Returns a copy of this node with the IP addresses set to the given value. */
    public Node withIpAddresses(Set<String> ipAddresses) {
        return new Builder(this).withIpAddresses(ipAddresses).build();
    }

    /** Returns a copy of this node with IP address pool set to the given value. */
    public Node withIpAddressPool(Set<String> ipAddressPool) {
        return new Builder(this).withIpAddressPool(ipAddressPool).build();
    }

    /** Returns a copy of this node with the parent hostname assigned to the given value. */
    public Node withParentHostname(String parentHostname) {
        return new Builder(this).withParentHostname(parentHostname).build();
    }

    /** Returns a copy of this node with the current reboot generation set to the given number at the given instant */
    public Node withCurrentRebootGeneration(long generation, Instant instant) {
        Builder builder = new Builder(this)
                .withStatus(status().withReboot(status().reboot().withCurrent(generation)));
        if (generation > status().reboot().current())
            builder.withHistoryEvent(History.Event.Type.rebooted, Agent.system, instant);
        return builder.build();
    }

    /** Returns a copy of this node with the given history. */
    public Node with(History history) {
        return new Builder(this).withHistory(history).build();
    }

    public Node with(Reports reports) {
        return new Builder(this).withReports(reports).build();
    }

    private static void requireNonEmptyString(Optional<String> value, String message) {
        Objects.requireNonNull(value, message);
        value.ifPresent(v -> requireNonEmptyString(v, message));
    }

    private static void requireNonEmptyString(String value, String message) {
        Objects.requireNonNull(value, message);
        if (value.trim().isEmpty())
            throw new IllegalArgumentException(message + ", but was '" + value + "'");
    }

    private static void requireNonEmpty(Set<String> values, String message) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Node node = (Node) o;
        return hostname.equals(node.hostname);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hostname);
    }

    @Override
    public String toString() {
        return state + " node " +
               hostname +
               (allocation.map(allocation1 -> " " + allocation1).orElse("")) +
               (parentHostname.map(parent -> " [on: " + parent + "]").orElse(""));
    }

    public enum State {

        /** This node has been requested (from OpenStack) but is not yet ready for use */
        provisioned,

        /** This node is free and ready for use */
        ready,

        /** This node has been reserved by an application but is not yet used by it */
        reserved,

        /** This node is in active use by an application */
        active,

        /** This node has been used by an application, is still allocated to it and retains the data needed for its allocated role */
        inactive,

        /** This node is not allocated to an application but may contain data which must be cleaned before it is ready */
        dirty,

        /** This node has failed and must be repaired or removed. The node retains any allocation data for diagnosis. */
        failed,

        /**
         * This node should not currently be used.
         * This state follows the same rules as failed except that it will never be automatically moved out of
         * this state.
         */
        parked;

        /** Returns whether this is a state where the node is assigned to an application */
        public boolean isAllocated() {
            return this == reserved || this == active || this == inactive || this == failed || this == parked;
        }
    }

}
