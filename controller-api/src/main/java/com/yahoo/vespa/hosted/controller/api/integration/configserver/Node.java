// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.configserver;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.TenantName;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * A node in hosted Vespa.
 *
 * This is immutable and all fields are guaranteed to be non-null. This should never leak any wire format types or
 * types from third-party libraries.
 *
 * Use {@link Node#builder()} or {@link Node#builder(Node)} to create instances of this.
 *
 * @author mpolden
 * @author jonmv
 */
public class Node {

    private final String id;
    private final HostName hostname;
    private final Optional<HostName> parentHostname;
    private final State state;
    private final NodeType type;
    private final NodeResources resources;
    private final Optional<ApplicationId> owner;
    private final Version currentVersion;
    private final Version wantedVersion;
    private final Version currentOsVersion;
    private final Version wantedOsVersion;
    private final boolean deferOsUpgrade;
    private final DockerImage currentDockerImage;
    private final DockerImage wantedDockerImage;
    private final ServiceState serviceState;
    private final Optional<Instant> suspendedSince;
    private final Optional<Instant> currentFirmwareCheck;
    private final Optional<Instant> wantedFirmwareCheck;
    private final long restartGeneration;
    private final long wantedRestartGeneration;
    private final long rebootGeneration;
    private final long wantedRebootGeneration;
    private final int cost;
    private final int failCount;
    private final Optional<String> flavor;
    private final String clusterId;
    private final ClusterType clusterType;
    private final String group;
    private final int index;
    private final boolean retired;
    private final boolean wantToRetire;
    private final boolean wantToDeprovision;
    private final boolean wantToRebuild;
    private final boolean down;
    private final Optional<TenantName> reservedTo;
    private final Optional<ApplicationId> exclusiveTo;
    private final Map<String, String> reports;
    private final List<Event> history;
    private final Set<String> ipAddresses;
    private final Set<String> additionalIpAddresses;
    private final Set<String> additionalHostnames;
    private final Optional<String> switchHostname;
    private final Optional<String> modelName;
    private final Environment environment;

    private Node(String id, HostName hostname, Optional<HostName> parentHostname, State state, NodeType type,
                 NodeResources resources, Optional<ApplicationId> owner, Version currentVersion, Version wantedVersion,
                 Version currentOsVersion, Version wantedOsVersion, boolean deferOsUpgrade, Optional<Instant> currentFirmwareCheck,
                 Optional<Instant> wantedFirmwareCheck, ServiceState serviceState, Optional<Instant> suspendedSince,
                 long restartGeneration, long wantedRestartGeneration, long rebootGeneration,
                 long wantedRebootGeneration, int cost, int failCount, Optional<String> flavor, String clusterId,
                 ClusterType clusterType, String group, int index, boolean retired, boolean wantToRetire, boolean wantToDeprovision,
                 boolean wantToRebuild, boolean down, Optional<TenantName> reservedTo, Optional<ApplicationId> exclusiveTo,
                 DockerImage wantedDockerImage, DockerImage currentDockerImage, Map<String, String> reports,
                 List<Event> history, Set<String> ipAddresses, Set<String> additionalIpAddresses,
                 Set<String> additionalHostnames, Optional<String> switchHostname,
                 Optional<String> modelName, Environment environment) {
        this.id = Objects.requireNonNull(id, "id must be non-null");
        this.hostname = Objects.requireNonNull(hostname, "hostname must be non-null");
        this.parentHostname = Objects.requireNonNull(parentHostname, "parentHostname must be non-null");
        this.state = Objects.requireNonNull(state, "state must be non-null");
        this.type = Objects.requireNonNull(type, "type must be non-null");
        this.resources = Objects.requireNonNull(resources, "resources must be non-null");
        this.owner = Objects.requireNonNull(owner, "owner must be non-null");
        this.currentVersion = Objects.requireNonNull(currentVersion, "currentVersion must be non-null");
        this.wantedVersion = Objects.requireNonNull(wantedVersion, "wantedVersion must be non-null");
        this.currentOsVersion = Objects.requireNonNull(currentOsVersion, "currentOsVersion must be non-null");
        this.wantedOsVersion = Objects.requireNonNull(wantedOsVersion, "wantedOsVersion must be non-null");
        this.deferOsUpgrade = deferOsUpgrade;
        this.currentFirmwareCheck = Objects.requireNonNull(currentFirmwareCheck, "currentFirmwareCheck must be non-null");
        this.wantedFirmwareCheck = Objects.requireNonNull(wantedFirmwareCheck, "wantedFirmwareCheck must be non-null");
        this.serviceState = Objects.requireNonNull(serviceState, "serviceState must be non-null");
        this.suspendedSince = Objects.requireNonNull(suspendedSince, "suspendedSince must be non-null");
        this.restartGeneration = restartGeneration;
        this.wantedRestartGeneration = wantedRestartGeneration;
        this.rebootGeneration = rebootGeneration;
        this.wantedRebootGeneration = wantedRebootGeneration;
        this.cost = cost;
        this.failCount = failCount;
        this.flavor = Objects.requireNonNull(flavor, "flavor must be non-null");
        this.clusterId = Objects.requireNonNull(clusterId, "clusterId must be non-null");
        this.clusterType = Objects.requireNonNull(clusterType, "clusterType must be non-null");
        this.retired = retired;
        this.group = Objects.requireNonNull(group, "group must be non-null");
        this.index = index;
        this.wantToRetire = wantToRetire;
        this.wantToDeprovision = wantToDeprovision;
        this.reservedTo = Objects.requireNonNull(reservedTo, "reservedTo must be non-null");
        this.exclusiveTo = Objects.requireNonNull(exclusiveTo, "exclusiveTo must be non-null");
        this.wantedDockerImage = Objects.requireNonNull(wantedDockerImage, "wantedDockerImage must be non-null");
        this.currentDockerImage = Objects.requireNonNull(currentDockerImage, "currentDockerImage must be non-null");
        this.wantToRebuild = wantToRebuild;
        this.down = down;
        this.reports = Map.copyOf(Objects.requireNonNull(reports, "reports must be non-null"));
        this.history = List.copyOf(Objects.requireNonNull(history, "history must be non-null"));
        this.ipAddresses = Set.copyOf(Objects.requireNonNull(ipAddresses, "ipAddresses must be non-null"));
        this.additionalIpAddresses = Set.copyOf(Objects.requireNonNull(additionalIpAddresses, "additionalIpAddresses must be non-null"));
        this.additionalHostnames = Set.copyOf(Objects.requireNonNull(additionalHostnames, "additionalHostnames must be non-null"));
        this.switchHostname = Objects.requireNonNull(switchHostname, "switchHostname must be non-null");
        this.modelName = Objects.requireNonNull(modelName, "modelName must be non-null");
        this.environment = Objects.requireNonNull(environment, "environment must be non-ull");
    }

    /** The cloud provider's unique ID for this */
    public String id() {
        return id;
    }

    /** The hostname of this */
    public HostName hostname() {
        return hostname;
    }

    /** The parent hostname of this, if any */
    public Optional<HostName> parentHostname() {
        return parentHostname;
    }

    /** Current state of this */
    public State state() { return state; }

    /** The node type of this */
    public NodeType type() {
        return type;
    }

    /** Resources, such as CPU and memory, of this */
    public NodeResources resources() {
        return resources;
    }

    /** The application owning this, if any */
    public Optional<ApplicationId> owner() {
        return owner;
    }

    /** The Vespa version this is currently running */
    public Version currentVersion() {
        return currentVersion;
    }

    /** The wanted Vespa version */
    public Version wantedVersion() {
        return wantedVersion;
    }

    /** The OS version this is currently running */
    public Version currentOsVersion() {
        return currentOsVersion;
    }

    /** The wanted OS version */
    public Version wantedOsVersion() {
        return wantedOsVersion;
    }

    /** Returns whether the node is currently deferring any OS upgrade */
    public boolean deferOsUpgrade() {
        return deferOsUpgrade;
    }

    /** The container image of this is currently running */
    public DockerImage currentDockerImage() {
        return currentDockerImage;
    }

    /** The wanted Docker image */
    public DockerImage wantedDockerImage() {
        return wantedDockerImage;
    }

    /** The last time this checked for a firmware update */
    public Optional<Instant> currentFirmwareCheck() {
        return currentFirmwareCheck;
    }

    /** The wanted time this should check for a firmware update */
    public Optional<Instant> wantedFirmwareCheck() {
        return wantedFirmwareCheck;
    }

    /** The current service state of this */
    public ServiceState serviceState() {
        return serviceState;
    }

    /** The most recent time this suspended, if any */
    public Optional<Instant> suspendedSince() {
        return suspendedSince;
    }

    /** The current restart generation */
    public long restartGeneration() {
        return restartGeneration;
    }

    /** The wanted restart generation */
    public long wantedRestartGeneration() {
        return wantedRestartGeneration;
    }

    /** The current reboot generation */
    public long rebootGeneration() {
        return rebootGeneration;
    }

    /** The wanted reboot generation */
    public long wantedRebootGeneration() {
        return wantedRebootGeneration;
    }

    /** A number representing the cost of this */
    public int cost() {
        return cost;
    }

    /** How many times this has failed */
    public int failCount() {
        return failCount;
    }

    /** The flavor of this */
    public Optional<String> flavor() {
        return flavor;
    }

    /** The cluster ID of this, empty string if unallocated */
    public String clusterId() {
        return clusterId;
    }

    /** The cluster type of this */
    public ClusterType clusterType() {
        return clusterType;
    }

    /** Whether this is retired */
    public boolean retired() {
        return retired;
    }

    /** The group of this node, empty string if unallocated */
    public String group() { return group; }

    /** The membership index of this node */
    public int index() { return index; }

    /** Whether this node has been requested to retire */
    public boolean wantToRetire() {
        return wantToRetire;
    }

    /** Whether this node has been requested to deprovision */
    public boolean wantToDeprovision() {
        return wantToDeprovision;
    }

    /** Whether this node has been requested to rebuild */
    public boolean wantToRebuild() {
        return wantToRebuild;
    }

    /** Whether this node is currently down */
    public boolean down() { return down; }

    /** The tenant this has been reserved to, if any */
    public Optional<TenantName> reservedTo() { return reservedTo; }

    /** The application this has been provisioned exclusively for, if any */
    public Optional<ApplicationId> exclusiveTo() { return exclusiveTo; }

    /** Returns the reports of this node. Key is the report ID. Value is untyped, but is typically a JSON string */
    public Map<String, String> reports() {
        return reports;
    }

    /** History of events affecting this */
    public List<Event> history() {
        return history;
    }

    /** IP addresses of this */
    public Set<String> ipAddresses() {
        return ipAddresses;
    }

    /** Additional IP addresses available on this, usable by child nodes */
    public Set<String> additionalIpAddresses() {
        return additionalIpAddresses;
    }

    /** Additional hostnames available on this, usable by child nodes */
    public Set<String> additionalHostnames() {
        return additionalHostnames;
    }

    /** Hostname of the switch this is connected to, if any */
    public Optional<String> switchHostname() {
        return switchHostname;
    }

    /** The server model of this, if any */
    public Optional<String> modelName() { return modelName; }

    /** The environment this runs in */
    public Environment environment() {
        return environment;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Node node = (Node) o;
        return Objects.equals(hostname, node.hostname);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hostname);
    }

    /** Known node states */
    public enum State {
        provisioned,
        ready,
        reserved,
        active,
        inactive,
        dirty,
        failed,
        parked,
        breakfixed,
        deprovisioned,
        unknown
    }

    /** Known node states with regards to service orchestration */
    public enum ServiceState {
        expectedUp,
        allowedDown,
        permanentlyDown,
        unorchestrated,
        unknown
    }

    /** Known cluster types. */
    public enum ClusterType {
        admin,
        container,
        content,
        combined,
        unknown
    }

    /** Known nope environments */
    public enum Environment {
        bareMetal,
        virtualMachine,
        dockerContainer,
        unknown,
    }

    /** A node event */
    public static class Event {

        private final Instant at;
        private final String agent;
        private final String name;

        public Event(Instant at, String agent, String name) {
            this.at = Objects.requireNonNull(at);
            this.agent = Objects.requireNonNull(agent);
            this.name = Objects.requireNonNull(name);
        }

        /** The time this occurred */
        public Instant at() {
            return at;
        }

        /** The agent responsible for this */
        public String agent() {
            return agent;
        }

        /** Name of the event */
        public String name() {
            return name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Event event = (Event) o;
            return at.equals(event.at) && agent.equals(event.agent) && name.equals(event.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(at, agent, name);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(Node node) {
        return new Builder(node);
    }

    /**
     * Builder for a {@link Node}.
     *
     * The appropriate builder method must be called for any field that does not have a default value.
     */
    public static class Builder {

        private HostName hostname;

        private String id = UUID.randomUUID().toString();
        private Optional<HostName> parentHostname = Optional.empty();
        private State state = State.active;
        private NodeType type = NodeType.host;
        private NodeResources resources = NodeResources.unspecified();
        private Optional<ApplicationId> owner = Optional.empty();
        private Version currentVersion = Version.emptyVersion;
        private Version wantedVersion = Version.emptyVersion;
        private Version currentOsVersion = Version.emptyVersion;
        private Version wantedOsVersion = Version.emptyVersion;
        private boolean deferOsUpgrade = false;
        private DockerImage currentDockerImage = DockerImage.EMPTY;
        private DockerImage wantedDockerImage = DockerImage.EMPTY;
        private Optional<Instant> currentFirmwareCheck = Optional.empty();
        private Optional<Instant> wantedFirmwareCheck = Optional.empty();
        private ServiceState serviceState = ServiceState.expectedUp;
        private Optional<Instant> suspendedSince = Optional.empty();
        private long restartGeneration = 0;
        private long wantedRestartGeneration = 0;
        private long rebootGeneration = 0;
        private long wantedRebootGeneration = 0;
        private int cost = 0;
        private int failCount = 0;
        private Optional<String> flavor = Optional.empty();
        private String clusterId = "";
        private ClusterType clusterType = ClusterType.unknown;
        private String group = "";
        private int index = 0;
        private boolean retired = false;
        private boolean wantToRetire = false;
        private boolean wantToDeprovision = false;
        private boolean wantToRebuild = false;
        private boolean down = false;
        private Optional<TenantName> reservedTo = Optional.empty();
        private Optional<ApplicationId> exclusiveTo = Optional.empty();
        private Map<String, String> reports = Map.of();
        private List<Event> history = List.of();
        private Set<String> ipAddresses = Set.of();
        private Set<String> additionalIpAddresses = Set.of();
        private Set<String> additionalHostnames = Set.of();
        private Optional<String> switchHostname = Optional.empty();
        private Optional<String> modelName = Optional.empty();
        private Environment environment = Environment.unknown;

        private Builder() {}

        private Builder(Node node) {
            this.id = node.id;
            this.hostname = node.hostname;
            this.parentHostname = node.parentHostname;
            this.state = node.state;
            this.type = node.type;
            this.resources = node.resources;
            this.owner = node.owner;
            this.currentVersion = node.currentVersion;
            this.wantedVersion = node.wantedVersion;
            this.currentOsVersion = node.currentOsVersion;
            this.wantedOsVersion = node.wantedOsVersion;
            this.deferOsUpgrade = node.deferOsUpgrade;
            this.currentDockerImage = node.currentDockerImage;
            this.wantedDockerImage = node.wantedDockerImage;
            this.serviceState = node.serviceState;
            this.suspendedSince = node.suspendedSince;
            this.currentFirmwareCheck = node.currentFirmwareCheck;
            this.wantedFirmwareCheck = node.wantedFirmwareCheck;
            this.restartGeneration = node.restartGeneration;
            this.wantedRestartGeneration = node.wantedRestartGeneration;
            this.rebootGeneration = node.rebootGeneration;
            this.wantedRebootGeneration = node.wantedRebootGeneration;
            this.cost = node.cost;
            this.failCount = node.failCount;
            this.flavor = node.flavor;
            this.clusterId = node.clusterId;
            this.clusterType = node.clusterType;
            this.group = node.group;
            this.index = node.index;
            this.retired = node.retired;
            this.wantToRetire = node.wantToRetire;
            this.wantToDeprovision = node.wantToDeprovision;
            this.wantToRebuild = node.wantToRebuild;
            this.down = node.down;
            this.reservedTo = node.reservedTo;
            this.exclusiveTo = node.exclusiveTo;
            this.reports = node.reports;
            this.history = node.history;
            this.ipAddresses = node.ipAddresses;
            this.additionalIpAddresses = node.additionalIpAddresses;
            this.additionalHostnames = node.additionalHostnames;
            this.switchHostname = node.switchHostname;
            this.modelName = node.modelName;
            this.environment = node.environment;
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder hostname(String hostname) {
            return hostname(HostName.of(hostname));
        }

        public Builder hostname(HostName hostname) {
            this.hostname = hostname;
            return this;
        }

        public Builder parentHostname(String parentHostname) {
            return parentHostname(HostName.of(parentHostname));
        }

        public Builder parentHostname(HostName parentHostname) {
            this.parentHostname = Optional.ofNullable(parentHostname);
            return this;
        }

        public Builder state(State state) {
            this.state = state;
            return this;
        }

        public Builder type(NodeType type) {
            this.type = type;
            return this;
        }

        public Builder resources(NodeResources resources) {
            this.resources = resources;
            return this;
        }

        public Builder owner(ApplicationId owner) {
            this.owner = Optional.ofNullable(owner);
            return this;
        }

        public Builder currentVersion(Version currentVersion) {
            this.currentVersion = currentVersion;
            return this;
        }

        public Builder wantedVersion(Version wantedVersion) {
            this.wantedVersion = wantedVersion;
            return this;
        }

        public Builder currentOsVersion(Version currentOsVersion) {
            this.currentOsVersion = currentOsVersion;
            return this;
        }

        public Builder wantedOsVersion(Version wantedOsVersion) {
            this.wantedOsVersion = wantedOsVersion;
            return this;
        }

        public Builder deferOsUpgrade(boolean deferOsUpgrade) {
            this.deferOsUpgrade = deferOsUpgrade;
            return this;
        }

        public Builder currentDockerImage(DockerImage currentDockerImage) {
            this.currentDockerImage = currentDockerImage;
            return this;
        }

        public Builder wantedDockerImage(DockerImage wantedDockerImage) {
            this.wantedDockerImage = wantedDockerImage;
            return this;
        }

        public Builder currentFirmwareCheck(Instant currentFirmwareCheck) {
            this.currentFirmwareCheck = Optional.ofNullable(currentFirmwareCheck);
            return this;
        }

        public Builder wantedFirmwareCheck(Instant wantedFirmwareCheck) {
            this.wantedFirmwareCheck = Optional.ofNullable(wantedFirmwareCheck);
            return this;
        }

        public Builder serviceState(ServiceState serviceState) {
            this.serviceState = serviceState;
            return this;
        }

        public Builder suspendedSince(Instant suspendedSince) {
            this.suspendedSince = Optional.ofNullable(suspendedSince);
            return this;
        }

        public Builder restartGeneration(long restartGeneration) {
            this.restartGeneration = restartGeneration;
            return this;
        }

        public Builder wantedRestartGeneration(long wantedRestartGeneration) {
            this.wantedRestartGeneration = wantedRestartGeneration;
            return this;
        }

        public Builder rebootGeneration(long rebootGeneration) {
            this.rebootGeneration = rebootGeneration;
            return this;
        }

        public Builder wantedRebootGeneration(long wantedRebootGeneration) {
            this.wantedRebootGeneration = wantedRebootGeneration;
            return this;
        }

        public Builder cost(int cost) {
            this.cost = cost;
            return this;
        }

        public Builder failCount(int failCount) {
            this.failCount = failCount;
            return this;
        }

        public Builder flavor(String flavor) {
            this.flavor = Optional.of(flavor);
            return this;
        }

        public Builder clusterId(String clusterId) {
            this.clusterId = clusterId;
            return this;
        }

        public Builder clusterType(ClusterType clusterType) {
            this.clusterType = clusterType;
            return this;
        }

        public Builder group(String group) {
            this.group = group;
            return this;
        }

        public Builder index(int index) {
            this.index = index;
            return this;
        }

        public Builder retired(boolean retired) {
            this.retired = retired;
            return this;
        }

        public Builder wantToRetire(boolean wantToRetire) {
            this.wantToRetire = wantToRetire;
            return this;
        }

        public Builder wantToDeprovision(boolean wantToDeprovision) {
            this.wantToDeprovision = wantToDeprovision;
            return this;
        }

        public Builder wantToRebuild(boolean wantToRebuild) {
            this.wantToRebuild = wantToRebuild;
            return this;
        }

        public Builder down(boolean down) {
            this.down = down;
            return this;
        }

        public Builder reservedTo(TenantName tenant) {
            this.reservedTo = Optional.of(tenant);
            return this;
        }

        public Builder exclusiveTo(ApplicationId exclusiveTo) {
            this.exclusiveTo = Optional.of(exclusiveTo);
            return this;
        }

        public Builder history(List<Event> history) {
            this.history = history;
            return this;
        }

        public Builder ipAddresses(Set<String> ipAdresses) {
            this.ipAddresses = ipAdresses;
            return this;
        }

        public Builder additionalIpAddresses(Set<String> additionalIpAddresses) {
            this.additionalIpAddresses = additionalIpAddresses;
            return this;
        }

        public Builder additionalHostnames(Set<String> additionalHostnames) {
            this.additionalHostnames = additionalHostnames;
            return this;
        }

        public Builder switchHostname(String switchHostname) {
            this.switchHostname = Optional.ofNullable(switchHostname);
            return this;
        }

        public Builder modelName(String modelName) {
            this.modelName = Optional.ofNullable(modelName);
            return this;
        }

        public Builder reports(Map<String, String> reports) {
            this.reports = reports;
            return this;
        }

        public Builder environment(Environment environment) {
            this.environment = environment;
            return this;
        }

        public Node build() {
            return new Node(id, hostname, parentHostname, state, type, resources, owner, currentVersion, wantedVersion,
                            currentOsVersion, wantedOsVersion, deferOsUpgrade, currentFirmwareCheck, wantedFirmwareCheck, serviceState,
                            suspendedSince, restartGeneration, wantedRestartGeneration, rebootGeneration,
                            wantedRebootGeneration, cost, failCount, flavor, clusterId, clusterType, group, index, retired,
                            wantToRetire, wantToDeprovision, wantToRebuild, down, reservedTo, exclusiveTo, wantedDockerImage,
                            currentDockerImage, reports, history, ipAddresses, additionalIpAddresses,
                            additionalHostnames, switchHostname, modelName, environment);
        }

    }

}
