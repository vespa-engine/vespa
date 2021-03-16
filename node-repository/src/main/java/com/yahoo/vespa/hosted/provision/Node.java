// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancers;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.Allocation;
import com.yahoo.vespa.hosted.provision.node.Generation;
import com.yahoo.vespa.hosted.provision.node.History;
import com.yahoo.vespa.hosted.provision.node.IP;
import com.yahoo.vespa.hosted.provision.node.NodeAcl;
import com.yahoo.vespa.hosted.provision.node.Reports;
import com.yahoo.vespa.hosted.provision.node.Status;

import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
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
public final class Node implements Nodelike {

    private final String hostname;
    private final IP.Config ipConfig;
    private final String id;
    private final Optional<String> parentHostname;
    private final Flavor flavor;
    private final Status status;
    private final State state;
    private final NodeType type;
    private final Reports reports;
    private final Optional<String> modelName;
    private final Optional<TenantName> reservedTo;
    private final Optional<ApplicationId> exclusiveTo;
    private final Optional<String> switchHostname;

    /** Record of the last event of each type happening to this node */
    private final History history;

    /** The current allocation of this node, if any */
    private final Optional<Allocation> allocation;

    /** Creates a node builder in the initial state (reserved) */
    public static Node.Builder reserve(Set<String> ipAddresses, String hostname, String parentHostname, NodeResources resources, NodeType type) {
        return new Node.Builder("fake-" + hostname, hostname, new Flavor(resources), State.reserved, type)
                .ipConfig(IP.Config.ofEmptyPool(ipAddresses))
                .parentHostname(parentHostname);
    }

    /** Creates a node builder in the initial state (provisioned) */
    public static Node.Builder create(String openStackId, IP.Config ipConfig, String hostname, Flavor flavor, NodeType type) {
        return new Node.Builder(openStackId, hostname, flavor, State.provisioned, type).ipConfig(ipConfig);
    }

    /** Creates a node builder */
    public static Node.Builder create(String openStackId, String hostname, Flavor flavor, Node.State state, NodeType type) {
        return new Node.Builder(openStackId, hostname, flavor, state, type);
    }

    /** DO NOT USE: public for serialization purposes. See {@code create} helper methods. */
    public Node(String id, IP.Config ipConfig, String hostname, Optional<String> parentHostname,
                Flavor flavor, Status status, State state, Optional<Allocation> allocation, History history, NodeType type,
                Reports reports, Optional<String> modelName, Optional<TenantName> reservedTo,
                Optional<ApplicationId> exclusiveTo, Optional<String> switchHostname) {
        this.id = Objects.requireNonNull(id, "A node must have an ID");
        this.hostname = requireNonEmptyString(hostname, "A node must have a hostname");
        this.ipConfig = Objects.requireNonNull(ipConfig, "A node must a have an IP config");
        this.parentHostname = requireNonEmptyString(parentHostname, "A parent host name must be a proper value");
        this.flavor = Objects.requireNonNull(flavor, "A node must have a flavor");
        this.status = Objects.requireNonNull(status, "A node must have a status");
        this.state = Objects.requireNonNull(state, "A null node state is not permitted");
        this.allocation = Objects.requireNonNull(allocation, "A null node allocation is not permitted");
        this.history = Objects.requireNonNull(history, "A null node history is not permitted");
        this.type = Objects.requireNonNull(type, "A null node type is not permitted");
        this.reports = Objects.requireNonNull(reports, "A null reports is not permitted");
        this.modelName = Objects.requireNonNull(modelName, "A null modelName is not permitted");
        this.reservedTo = Objects.requireNonNull(reservedTo, "reservedTo cannot be null");
        this.exclusiveTo = Objects.requireNonNull(exclusiveTo, "exclusiveTo cannot be null");
        this.switchHostname = requireNonEmptyString(switchHostname, "switchHostname cannot be null");

        if (state == State.active)
            requireNonEmpty(ipConfig.primary(), "Active node " + hostname + " must have at least one valid IP address");

        if (parentHostname.isPresent()) {
            if (!ipConfig.pool().getIpSet().isEmpty()) throw new IllegalArgumentException("A child node cannot have an IP address pool");
            if (modelName.isPresent()) throw new IllegalArgumentException("A child node cannot have model name set");
            if (switchHostname.isPresent()) throw new IllegalArgumentException("A child node cannot have switch hostname set");
        }

        if (type != NodeType.host && reservedTo.isPresent())
            throw new IllegalArgumentException("Only tenant hosts can be reserved to a tenant");

        if (type != NodeType.host && exclusiveTo.isPresent())
            throw new IllegalArgumentException("Only tenant hosts can be exclusive to an application");
    }

    /** Returns the IP config of this node */
    public IP.Config ipConfig() { return ipConfig; }

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
     * - Linux containers: fake-[hostname]
     */
    public String id() { return id; }

    @Override
    public Optional<String> parentHostname() { return parentHostname; }

    public boolean hasParent(String hostname) {
        return parentHostname.isPresent() && parentHostname.get().equals(hostname);
    }

    @Override
    public NodeResources resources() { return flavor.resources(); }

    /** Returns the flavor of this node */
    public Flavor flavor() { return flavor; }

    /** Returns the known information about the node's ephemeral status */
    public Status status() { return status; }

    /** Returns the current state of this node (in the node state machine) */
    public State state() { return state; }

    @Override
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

    /** Returns the hardware model of this node, if any */
    public Optional<String> modelName() { return modelName; }

    /**
     * Returns the tenant this node is reserved to, if any. Only hosts can be reserved to a tenant.
     * If this is set, resources on this host cannot be allocated to any other tenant
     */
    public Optional<TenantName> reservedTo() { return reservedTo; }

    /**
     * Returns the application this node is exclusive to, if any. Only hosts can be exclusive to an application.
     * If this is set, resources on this host cannot be allocated to any other application. This is set during
     * provisioning and applies for the entire lifetime of the host
     */
    public Optional<ApplicationId> exclusiveTo() { return exclusiveTo; }

    /** Returns the hostname of the switch this node is connected to, if any */
    public Optional<String> switchHostname() {
        return switchHostname;
    }

    /**
     * Returns a copy of this node with wantToRetire and wantToDeprovision set to the given values and updated history.
     *
     * If both given wantToRetire and wantToDeprovision are equal to the current values, the method is no-op.
     */
    public Node withWantToRetire(boolean wantToRetire, boolean wantToDeprovision, Agent agent, Instant at) {
        if (wantToRetire == status.wantToRetire() &&
            wantToDeprovision == status.wantToDeprovision()) return this;
        Node node = this.with(status.withWantToRetire(wantToRetire, wantToDeprovision));
        if (wantToRetire)
            node = node.with(history.with(new History.Event(History.Event.Type.wantToRetire, agent, at)));
        return node;
    }

    public Node withWantToRetire(boolean wantToRetire, Agent agent, Instant at) {
        return withWantToRetire(wantToRetire, status.wantToDeprovision(), agent, at);
    }

    /** Returns a copy of this node with preferToRetire set to given value and updated history */
    public Node withPreferToRetire(boolean preferToRetire, Agent agent, Instant at) {
        if (preferToRetire == status.preferToRetire()) return this;
        Node node = this.with(status.withPreferToRetire(preferToRetire));
        if (preferToRetire) {
            node = node.with(history.with(new History.Event(History.Event.Type.preferToRetire, agent, at)));
        }
        return node;
    }

    /**
     * Returns a copy of this node which is retired.
     * If the node was already retired it is returned as-is.
     */
    public Node retire(Agent agent, Instant retiredAt) {
        Allocation allocation = requireAllocation("Cannot retire");
        if (allocation.membership().retired()) return this;
        return with(allocation.retire())
                .with(history.with(new History.Event(History.Event.Type.retired, agent, retiredAt)));
    }

    /** Returns a copy of this node which is retired */
    public Node retire(Instant retiredAt) {
        if (status.wantToRetire() || status.preferToRetire())
            return retire(Agent.system, retiredAt);
        else
            return retire(Agent.application, retiredAt);
    }

    /** Returns a copy of this node which is not retired */
    public Node unretire() {
        return with(requireAllocation("Cannot unretire").unretire());
    }

    /** Returns a copy of this with removable set to the given value */
    public Node removable(boolean removable) {
        return with(requireAllocation("Cannot set removable").removable(removable));
    }

    /** Returns a copy of this with the restart generation set to generation */
    public Node withRestart(Generation generation) {
        Allocation allocation = requireAllocation("Cannot set restart generation");
        return with(allocation.withRestart(generation));
    }

    /** Returns a node with the status assigned to the given value */
    public Node with(Status status) {
        return new Node(id, ipConfig, hostname, parentHostname, flavor, status, state, allocation, history, type,
                        reports, modelName, reservedTo, exclusiveTo, switchHostname);
    }

    /** Returns a node with the type assigned to the given value */
    public Node with(NodeType type) {
        return new Node(id, ipConfig, hostname, parentHostname, flavor, status, state, allocation, history, type,
                        reports, modelName, reservedTo, exclusiveTo, switchHostname);
    }

    /** Returns a node with the flavor assigned to the given value */
    public Node with(Flavor flavor) {
        return new Node(id, ipConfig, hostname, parentHostname, flavor, status, state, allocation, history, type,
                        reports, modelName, reservedTo, exclusiveTo, switchHostname);
    }

    /** Returns a copy of this with the reboot generation set to generation */
    public Node withReboot(Generation generation) {
        return new Node(id, ipConfig, hostname, parentHostname, flavor, status.withReboot(generation), state,
                        allocation, history, type, reports, modelName, reservedTo, exclusiveTo, switchHostname);
    }

    /** Returns a copy of this with the openStackId set */
    public Node withOpenStackId(String openStackId) {
        return new Node(openStackId, ipConfig, hostname, parentHostname, flavor, status, state,
                        allocation, history, type, reports, modelName, reservedTo, exclusiveTo, switchHostname);
    }

    /** Returns a copy of this with model name set to given value */
    public Node withModelName(String modelName) {
        return new Node(id, ipConfig, hostname, parentHostname, flavor, status, state,
                        allocation, history, type, reports, Optional.of(modelName), reservedTo, exclusiveTo, switchHostname);
    }

    /** Returns a copy of this with model name cleared */
    public Node withoutModelName() {
        return new Node(id, ipConfig, hostname, parentHostname, flavor, status, state,
                        allocation, history, type, reports, Optional.empty(), reservedTo, exclusiveTo, switchHostname);
    }

    /** Returns a copy of this with a history record saying it was detected to be down at this instant */
    public Node downAt(Instant instant, Agent agent) {
        return with(history.with(new History.Event(History.Event.Type.down, agent, instant)));
    }

    /** Returns a copy of this with any history record saying it has been detected down removed */
    public Node up() {
        return with(history.without(History.Event.Type.down));
    }

    /** Returns whether this node has a record of being down */
    public boolean isDown() {
        return history().event(History.Event.Type.down).isPresent();
    }

    /** Returns a copy of this with allocation set as specified. <code>node.state</code> is *not* changed. */
    public Node allocate(ApplicationId owner, ClusterMembership membership, NodeResources requestedResources, Instant at) {
        return this
                .with(new Allocation(owner, membership, requestedResources, new Generation(0, 0), false))
                .with(history.with(new History.Event(History.Event.Type.reserved, Agent.application, at)));
    }

    /**
     * Returns a copy of this node with the allocation assigned to the given allocation.
     * Do not use this to allocate a node.
     */
    public Node with(Allocation allocation) {
        return new Node(id, ipConfig, hostname, parentHostname, flavor, status, state,
                        Optional.of(allocation), history, type, reports, modelName, reservedTo, exclusiveTo, switchHostname);
    }

    /** Returns a new Node without an allocation. */
    public Node withoutAllocation() {
        return new Node(id, ipConfig, hostname, parentHostname, flavor, status, state,
                        Optional.empty(), history, type, reports, modelName, reservedTo, exclusiveTo, switchHostname);
    }


    /** Returns a copy of this node with IP config set to the given value. */
    public Node with(IP.Config ipConfig) {
        return new Node(id, ipConfig, hostname, parentHostname, flavor, status, state,
                        allocation, history, type, reports, modelName, reservedTo, exclusiveTo, switchHostname);
    }

    /** Returns a copy of this node with the parent hostname assigned to the given value. */
    public Node withParentHostname(String parentHostname) {
        return new Node(id, ipConfig, hostname, Optional.of(parentHostname), flavor, status, state,
                        allocation, history, type, reports, modelName, reservedTo, exclusiveTo, switchHostname);
    }

    public Node withReservedTo(TenantName tenant) {
        if (type != NodeType.host)
            throw new IllegalArgumentException("Only host nodes can be reserved, " + hostname + " has type " + type);
        return new Node(id, ipConfig, hostname, parentHostname, flavor, status, state,
                        allocation, history, type, reports, modelName, Optional.of(tenant), exclusiveTo, switchHostname);
    }

    /** Returns a copy of this node which is not reserved to a tenant */
    public Node withoutReservedTo() {
        return new Node(id, ipConfig, hostname, parentHostname, flavor, status, state,
                        allocation, history, type, reports, modelName, Optional.empty(), exclusiveTo, switchHostname);
    }

    public Node withExclusiveTo(ApplicationId exclusiveTo) {
        return new Node(id, ipConfig, hostname, parentHostname, flavor, status, state,
                allocation, history, type, reports, modelName, reservedTo, Optional.ofNullable(exclusiveTo), switchHostname);
    }

    /** Returns a copy of this node with switch hostname set to given value */
    public Node withSwitchHostname(String switchHostname) {
        return new Node(id, ipConfig, hostname, parentHostname, flavor, status, state,
                        allocation, history, type, reports, modelName, reservedTo, exclusiveTo, Optional.ofNullable(switchHostname));
    }

    /** Returns a copy of this node with switch hostname unset */
    public Node withoutSwitchHostname() {
        return withSwitchHostname(null);
    }

    /** Returns a copy of this node with the current reboot generation set to the given number at the given instant */
    public Node withCurrentRebootGeneration(long generation, Instant instant) {
        Status newStatus = status().withReboot(status().reboot().withCurrent(generation));
        History newHistory = history();
        if (generation > status().reboot().current())
            newHistory = history.with(new History.Event(History.Event.Type.rebooted, Agent.system, instant));
        return this.with(newStatus).with(newHistory);
    }

    /** Returns a copy of this node with the current OS version set to the given version at the given instant */
    public Node withCurrentOsVersion(Version version, Instant instant) {
        var newStatus = status.withOsVersion(status.osVersion().withCurrent(Optional.of(version)));
        var newHistory = history();
        // Only update history if version has changed
        if (status.osVersion().current().isEmpty() || !status.osVersion().current().get().equals(version)) {
            newHistory = history.with(new History.Event(History.Event.Type.osUpgraded, Agent.system, instant));
        }
        return this.with(newStatus).with(newHistory);
    }

    /** Returns a copy of this node with firmware verified at the given instant */
    public Node withFirmwareVerifiedAt(Instant instant) {
        var newStatus = status.withFirmwareVerifiedAt(instant);
        var newHistory = history.with(new History.Event(History.Event.Type.firmwareVerified, Agent.system, instant));
        return this.with(newStatus).with(newHistory);
    }

    /** Returns a copy of this node with the given history. */
    public Node with(History history) {
        return new Node(id, ipConfig, hostname, parentHostname, flavor, status, state,
                        allocation, history, type, reports, modelName, reservedTo, exclusiveTo, switchHostname);
    }

    public Node with(Reports reports) {
        return new Node(id, ipConfig, hostname, parentHostname, flavor, status, state,
                        allocation, history, type, reports, modelName, reservedTo, exclusiveTo, switchHostname);
    }

    private static Optional<String> requireNonEmptyString(Optional<String> value, String message) {
        Objects.requireNonNull(value, message);
        value.ifPresent(v -> requireNonEmptyString(v, message));
        return value;
    }

    private static String requireNonEmptyString(String value, String message) {
        Objects.requireNonNull(value, message);
        if (value.trim().isEmpty())
            throw new IllegalArgumentException(message + ", but was '" + value + "'");
        return value;
    }

    private static Set<String> requireNonEmpty(Set<String> values, String message) {
        if (values == null || values.isEmpty())
            throw new IllegalArgumentException(message);
        return values;
    }

    /** Computes the allocation skew of a host node */
    public static double skew(NodeResources totalHostCapacity, NodeResources freeHostCapacity) {
        NodeResources all = totalHostCapacity.justNumbers();
        NodeResources allocated = all.subtract(freeHostCapacity.justNumbers());

        return new Mean(allocated.vcpu() / all.vcpu(),
                        allocated.memoryGb() / all.memoryGb(),
                        allocated.diskGb() / all.diskGb())
                       .deviation();
    }

    /** Returns the ACL for the node (trusted nodes, networks and ports) */
    public NodeAcl acl(NodeList allNodes, LoadBalancers loadBalancers) {
        return NodeAcl.from(this, allNodes, loadBalancers);
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
        return state +
               ( parentHostname.isPresent() ? " child node " : " host " ) +
               hostname +
               ( allocation.isPresent() ? " " + allocation.get() : "");
    }

    public enum State {

        /** This node has been requested, but is not yet ready for use */
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
        parked,

        /** This host has previously been in use but is now removed. */
        deprovisioned,

        /** This host is currently undergoing repair. */
        breakfixed;

        /** Returns whether this is a state where the node is assigned to an application */
        public boolean isAllocated() {
            return allocatedStates().contains(this);
        }

        public static Set<State> allocatedStates() {
            return EnumSet.of(reserved, active, inactive, dirty, failed, parked);
        }

    }

    /** The mean and mean deviation (squared difference) of a bunch of numbers */
    private static class Mean {

        private final double mean;
        private final double deviation;

        private Mean(double ... numbers) {
            mean = Arrays.stream(numbers).sum() / numbers.length;
            deviation = Arrays.stream(numbers).map(n -> Math.pow(mean - n, 2)).sum() / numbers.length;
        }

        public double deviation() {  return deviation; }

    }

    public static class Builder {
        private final String id;
        private final String hostname;
        private final Flavor flavor;
        private final State state;
        private final NodeType type;

        private String parentHostname;
        private String modelName;
        private TenantName reservedTo;
        private ApplicationId exclusiveTo;
        private String switchHostname;
        private Allocation allocation;
        private IP.Config ipConfig;
        private Status status;
        private Reports reports;
        private History history;

        private Builder(String id, String hostname, Flavor flavor, State state, NodeType type) {
            this.id = id;
            this.hostname = hostname;
            this.flavor = flavor;
            this.state = state;
            this.type = type;
        }

        public Builder parentHostname(String parentHostname) {
            this.parentHostname = parentHostname;
            return this;
        }

        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public Builder reservedTo(TenantName reservedTo) {
            this.reservedTo = reservedTo;
            return this;
        }

        public Builder exclusiveTo(ApplicationId exclusiveTo) {
            this.exclusiveTo = exclusiveTo;
            return this;
        }

        public Builder switchHostname(String switchHostname) {
            this.switchHostname = switchHostname;
            return this;
        }

        public Builder allocation(Allocation allocation) {
            this.allocation = allocation;
            return this;
        }

        public Builder ipConfig(IP.Config ipConfig) {
            this.ipConfig = ipConfig;
            return this;
        }

        public Builder ipConfigWithEmptyPool(Set<String> primary) {
            this.ipConfig = IP.Config.ofEmptyPool(primary);
            return this;
        }

        public Builder status(Status status) {
            this.status = status;
            return this;
        }

        public Builder reports(Reports reports) {
            this.reports = reports;
            return this;
        }

        public Builder history(History history) {
            this.history = history;
            return this;
        }

        public Node build() {
            return new Node(id, Optional.ofNullable(ipConfig).orElse(IP.Config.EMPTY), hostname, Optional.ofNullable(parentHostname),
                    flavor, Optional.ofNullable(status).orElseGet(Status::initial), state, Optional.ofNullable(allocation),
                    Optional.ofNullable(history).orElseGet(History::empty), type, Optional.ofNullable(reports).orElseGet(Reports::new),
                    Optional.ofNullable(modelName), Optional.ofNullable(reservedTo), Optional.ofNullable(exclusiveTo),
                    Optional.ofNullable(switchHostname));
        }
    }

}
