// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.noderepository;

import com.fasterxml.jackson.databind.JsonNode;
import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.node.admin.task.util.file.DiskSize;

import java.net.URI;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static com.yahoo.config.provision.NodeResources.DiskSpeed.fast;
import static com.yahoo.config.provision.NodeResources.DiskSpeed.slow;

/**
 * @author stiankri
 */
public class NodeSpec {

    private final String hostname;
    private final Optional<String> id;
    private final NodeState state;
    private final NodeType type;
    private final String flavor;

    private final Optional<DockerImage> wantedDockerImage;
    private final Optional<DockerImage> currentDockerImage;

    private final Optional<Version> wantedVespaVersion;
    private final Optional<Version> currentVespaVersion;

    private final Optional<Version> wantedOsVersion;
    private final Optional<Version> currentOsVersion;

    private final Optional<Long> wantedRestartGeneration;
    private final Optional<Long> currentRestartGeneration;

    private final long wantedRebootGeneration;
    private final long currentRebootGeneration;

    private final Optional<Instant> wantedFirmwareCheck;
    private final Optional<Instant> currentFirmwareCheck;

    private final Optional<String> modelName;

    private final OrchestratorStatus orchestratorStatus;
    private final Optional<ApplicationId> owner;
    private final Optional<NodeMembership> membership;

    private final NodeResources resources;
    private final Set<String> ipAddresses;
    private final Set<String> additionalIpAddresses;

    private final NodeReports reports;

    private final Optional<String> parentHostname;
    private final Optional<URI> archiveUri;

    private final Optional<ApplicationId> exclusiveTo;

    public NodeSpec(
            String hostname,
            Optional<String> id,
            Optional<DockerImage> wantedDockerImage,
            Optional<DockerImage> currentDockerImage,
            NodeState state,
            NodeType type,
            String flavor,
            Optional<Version> wantedVespaVersion,
            Optional<Version> currentVespaVersion,
            Optional<Version> wantedOsVersion,
            Optional<Version> currentOsVersion,
            OrchestratorStatus orchestratorStatus,
            Optional<ApplicationId> owner,
            Optional<NodeMembership> membership,
            Optional<Long> wantedRestartGeneration,
            Optional<Long> currentRestartGeneration,
            long wantedRebootGeneration,
            long currentRebootGeneration,
            Optional<Instant> wantedFirmwareCheck,
            Optional<Instant> currentFirmwareCheck,
            Optional<String> modelName,
            NodeResources resources,
            Set<String> ipAddresses,
            Set<String> additionalIpAddresses,
            NodeReports reports,
            Optional<String> parentHostname,
            Optional<URI> archiveUri,
            Optional<ApplicationId> exclusiveTo) {
        if (state == NodeState.active) {
            requireOptional(owner, "owner");
            requireOptional(membership, "membership");
            requireOptional(wantedVespaVersion, "wantedVespaVersion");
            requireOptional(wantedDockerImage, "wantedDockerImage");
            requireOptional(wantedRestartGeneration, "restartGeneration");
            requireOptional(currentRestartGeneration, "currentRestartGeneration");
        }

        this.hostname = Objects.requireNonNull(hostname);
        this.id = Objects.requireNonNull(id);
        this.wantedDockerImage = Objects.requireNonNull(wantedDockerImage);
        this.currentDockerImage = Objects.requireNonNull(currentDockerImage);
        this.state = Objects.requireNonNull(state);
        this.type = Objects.requireNonNull(type);
        this.flavor = Objects.requireNonNull(flavor);
        this.modelName = Objects.requireNonNull(modelName);
        this.wantedVespaVersion = Objects.requireNonNull(wantedVespaVersion);
        this.currentVespaVersion = Objects.requireNonNull(currentVespaVersion);
        this.wantedOsVersion = Objects.requireNonNull(wantedOsVersion);
        this.currentOsVersion = Objects.requireNonNull(currentOsVersion);
        this.orchestratorStatus = Objects.requireNonNull(orchestratorStatus);
        this.owner = Objects.requireNonNull(owner);
        this.membership = Objects.requireNonNull(membership);
        this.wantedRestartGeneration = wantedRestartGeneration;
        this.currentRestartGeneration = currentRestartGeneration;
        this.wantedRebootGeneration = wantedRebootGeneration;
        this.currentRebootGeneration = currentRebootGeneration;
        this.wantedFirmwareCheck = Objects.requireNonNull(wantedFirmwareCheck);
        this.currentFirmwareCheck = Objects.requireNonNull(currentFirmwareCheck);
        this.resources = Objects.requireNonNull(resources);
        this.ipAddresses = Objects.requireNonNull(ipAddresses);
        this.additionalIpAddresses = Objects.requireNonNull(additionalIpAddresses);
        this.reports = Objects.requireNonNull(reports);
        this.parentHostname = Objects.requireNonNull(parentHostname);
        this.archiveUri = Objects.requireNonNull(archiveUri);
        this.exclusiveTo = Objects.requireNonNull(exclusiveTo);
    }

    public String hostname() {
        return hostname;
    }

    /** Returns the cloud-specific ID of the host. */
    public Optional<String> id() {
        return id;
    }

    public NodeState state() {
        return state;
    }

    public NodeType type() {
        return type;
    }

    public String flavor() {
        return flavor;
    }

    public Optional<DockerImage> wantedDockerImage() {
        return wantedDockerImage;
    }

    public Optional<DockerImage> currentDockerImage() {
        return currentDockerImage;
    }

    public Optional<Version> wantedVespaVersion() {
        return wantedVespaVersion;
    }

    public Optional<Version> currentVespaVersion() {
        return currentVespaVersion;
    }

    public Optional<Version> currentOsVersion() {
        return currentOsVersion;
    }

    public Optional<Version> wantedOsVersion() {
        return wantedOsVersion;
    }

    public Optional<Long> wantedRestartGeneration() {
        return wantedRestartGeneration;
    }

    public Optional<Long> currentRestartGeneration() {
        return currentRestartGeneration;
    }

    public long wantedRebootGeneration() {
        return wantedRebootGeneration;
    }

    public long currentRebootGeneration() {
        return currentRebootGeneration;
    }

    public Optional<Instant> wantedFirmwareCheck() {
        return wantedFirmwareCheck;
    }

    public Optional<Instant> currentFirmwareCheck() {
        return currentFirmwareCheck;
    }

    public Optional<String> modelName() {
        return modelName;
    }

    public OrchestratorStatus orchestratorStatus() {
        return orchestratorStatus;
    }

    public Optional<ApplicationId> owner() {
        return owner;
    }

    public Optional<NodeMembership> membership() {
        return membership;
    }

    public NodeResources resources() {
        return resources;
    }

    public double vcpu() {
        return resources.vcpu();
    }

    public double memoryGb() {
        return resources.memoryGb();
    }

    public DiskSize diskSize() {
        return DiskSize.of(resources.diskGb(), DiskSize.Unit.GB);
    }

    public double diskGb() {
        return resources.diskGb();
    }

    public boolean isFastDisk() {
        return resources.diskSpeed() == fast;
    }

    public double bandwidthGbps() {
        return resources.bandwidthGbps();
    }

    public Set<String> ipAddresses() {
        return ipAddresses;
    }

    public Set<String> additionalIpAddresses() {
        return additionalIpAddresses;
    }

    public NodeReports reports() { return reports; }

    public Optional<String> parentHostname() {
        return parentHostname;
    }

    public Optional<URI> archiveUri() {
        return archiveUri;
    }

    public Optional<ApplicationId> exclusiveTo() {
        return exclusiveTo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NodeSpec)) return false;

        NodeSpec that = (NodeSpec) o;

        return Objects.equals(hostname, that.hostname) &&
                Objects.equals(id, that.id) &&
                Objects.equals(wantedDockerImage, that.wantedDockerImage) &&
                Objects.equals(currentDockerImage, that.currentDockerImage) &&
                Objects.equals(state, that.state) &&
                Objects.equals(type, that.type) &&
                Objects.equals(flavor, that.flavor) &&
                Objects.equals(modelName, that.modelName) &&
                Objects.equals(wantedVespaVersion, that.wantedVespaVersion) &&
                Objects.equals(currentVespaVersion, that.currentVespaVersion) &&
                Objects.equals(wantedOsVersion, that.wantedOsVersion) &&
                Objects.equals(currentOsVersion, that.currentOsVersion) &&
                Objects.equals(orchestratorStatus, that.orchestratorStatus) &&
                Objects.equals(owner, that.owner) &&
                Objects.equals(membership, that.membership) &&
                Objects.equals(wantedRestartGeneration, that.wantedRestartGeneration) &&
                Objects.equals(currentRestartGeneration, that.currentRestartGeneration) &&
                Objects.equals(wantedRebootGeneration, that.wantedRebootGeneration) &&
                Objects.equals(currentRebootGeneration, that.currentRebootGeneration) &&
                Objects.equals(wantedFirmwareCheck, that.wantedFirmwareCheck) &&
                Objects.equals(currentFirmwareCheck, that.currentFirmwareCheck) &&
                Objects.equals(resources, that.resources) &&
                Objects.equals(ipAddresses, that.ipAddresses) &&
                Objects.equals(additionalIpAddresses, that.additionalIpAddresses) &&
                Objects.equals(reports, that.reports) &&
                Objects.equals(parentHostname, that.parentHostname) &&
                Objects.equals(archiveUri, that.archiveUri) &&
                Objects.equals(exclusiveTo, that.exclusiveTo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                hostname,
                id,
                wantedDockerImage,
                currentDockerImage,
                state,
                type,
                flavor,
                modelName,
                wantedVespaVersion,
                currentVespaVersion,
                wantedOsVersion,
                currentOsVersion,
                orchestratorStatus,
                owner,
                membership,
                wantedRestartGeneration,
                currentRestartGeneration,
                wantedRebootGeneration,
                currentRebootGeneration,
                wantedFirmwareCheck,
                currentFirmwareCheck,
                resources,
                ipAddresses,
                additionalIpAddresses,
                reports,
                parentHostname,
                archiveUri,
                exclusiveTo);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " {"
                + " hostname=" + hostname
                + " id=" + id
                + " wantedDockerImage=" + wantedDockerImage
                + " currentDockerImage=" + currentDockerImage
                + " state=" + state
                + " type=" + type
                + " flavor=" + flavor
                + " modelName=" + modelName
                + " wantedVespaVersion=" + wantedVespaVersion
                + " currentVespaVersion=" + currentVespaVersion
                + " wantedOsVersion=" + wantedOsVersion
                + " currentOsVersion=" + currentOsVersion
                + " orchestratorStatus=" + orchestratorStatus
                + " owner=" + owner
                + " membership=" + membership
                + " wantedRestartGeneration=" + wantedRestartGeneration
                + " currentRestartGeneration=" + currentRestartGeneration
                + " wantedRebootGeneration=" + wantedRebootGeneration
                + " currentRebootGeneration=" + currentRebootGeneration
                + " wantedFirmwareCheck=" + wantedFirmwareCheck
                + " currentFirmwareCheck=" + currentFirmwareCheck
                + " resources=" + resources
                + " ipAddresses=" + ipAddresses
                + " additionalIpAddresses=" + additionalIpAddresses
                + " reports=" + reports
                + " parentHostname=" + parentHostname
                + " archiveUri=" + archiveUri
                + " exclusiveTo=" + exclusiveTo
                + " }";
    }

    public static class Builder {
        private String hostname;
        private Optional<String> id = Optional.empty();
        private NodeState state;
        private NodeType type;
        private String flavor;
        private Optional<DockerImage> wantedDockerImage = Optional.empty();
        private Optional<DockerImage> currentDockerImage = Optional.empty();
        private Optional<Version> wantedVespaVersion = Optional.empty();
        private Optional<Version> currentVespaVersion = Optional.empty();
        private Optional<Version> wantedOsVersion = Optional.empty();
        private Optional<Version> currentOsVersion = Optional.empty();
        private OrchestratorStatus orchestratorStatus = OrchestratorStatus.NO_REMARKS;
        private Optional<ApplicationId> owner = Optional.empty();
        private Optional<NodeMembership> membership = Optional.empty();
        private Optional<Long> wantedRestartGeneration = Optional.empty();
        private Optional<Long> currentRestartGeneration = Optional.empty();
        private long wantedRebootGeneration;
        private long currentRebootGeneration;
        private Optional<Instant> wantedFirmwareCheck = Optional.empty();
        private Optional<Instant> currentFirmwareCheck = Optional.empty();
        private Optional<String> modelName = Optional.empty();
        private NodeResources resources = new NodeResources(0, 0, 0, 0, slow);
        private Set<String> ipAddresses = Set.of();
        private Set<String> additionalIpAddresses = Set.of();
        private NodeReports reports = new NodeReports();
        private Optional<String> parentHostname = Optional.empty();
        private Optional<URI> archiveUri = Optional.empty();
        private Optional<ApplicationId> exclusiveTo = Optional.empty();

        public Builder() {}

        public Builder(NodeSpec node) {
            hostname(node.hostname);
            state(node.state);
            type(node.type);
            flavor(node.flavor);
            resources(node.resources);
            ipAddresses(node.ipAddresses);
            additionalIpAddresses(node.additionalIpAddresses);
            wantedRebootGeneration(node.wantedRebootGeneration);
            currentRebootGeneration(node.currentRebootGeneration);
            orchestratorStatus(node.orchestratorStatus);
            reports(new NodeReports(node.reports));
            node.wantedDockerImage.ifPresent(this::wantedDockerImage);
            node.currentDockerImage.ifPresent(this::currentDockerImage);
            node.wantedVespaVersion.ifPresent(this::wantedVespaVersion);
            node.currentVespaVersion.ifPresent(this::currentVespaVersion);
            node.wantedOsVersion.ifPresent(this::wantedOsVersion);
            node.currentOsVersion.ifPresent(this::currentOsVersion);
            node.owner.ifPresent(this::owner);
            node.membership.ifPresent(this::membership);
            node.wantedRestartGeneration.ifPresent(this::wantedRestartGeneration);
            node.currentRestartGeneration.ifPresent(this::currentRestartGeneration);
            node.wantedFirmwareCheck.ifPresent(this::wantedFirmwareCheck);
            node.currentFirmwareCheck.ifPresent(this::currentFirmwareCheck);
            node.parentHostname.ifPresent(this::parentHostname);
            node.archiveUri.ifPresent(this::archiveUri);
            node.exclusiveTo.ifPresent(this::exclusiveTo);
        }

        public Builder hostname(String hostname) {
            this.hostname = hostname;
            return this;
        }

        public Builder id(String id) {
            this.id = Optional.of(id);
            return this;
        }

        public Builder wantedDockerImage(DockerImage wantedDockerImage) {
            this.wantedDockerImage = Optional.of(wantedDockerImage);
            return this;
        }

        public Builder currentDockerImage(DockerImage currentDockerImage) {
            this.currentDockerImage = Optional.of(currentDockerImage);
            return this;
        }

        public Builder state(NodeState state) {
            this.state = state;
            return this;
        }

        public Builder type(NodeType nodeType) {
            this.type = nodeType;
            return this;
        }

        public Builder flavor(String flavor) {
            this.flavor = flavor;
            return this;
        }

        public Builder wantedVespaVersion(Version wantedVespaVersion) {
            this.wantedVespaVersion = Optional.of(wantedVespaVersion);
            return this;
        }

        public Builder currentVespaVersion(Version vespaVersion) {
            this.currentVespaVersion = Optional.of(vespaVersion);
            return this;
        }

        public Builder wantedOsVersion(Version wantedOsVersion) {
            this.wantedOsVersion = Optional.of(wantedOsVersion);
            return this;
        }

        public Builder currentOsVersion(Version currentOsVersion) {
            this.currentOsVersion = Optional.of(currentOsVersion);
            return this;
        }

        public Builder orchestratorStatus(OrchestratorStatus orchestratorStatus) {
            this.orchestratorStatus = orchestratorStatus;
            return this;
        }

        public Builder owner(ApplicationId owner) {
            this.owner = Optional.of(owner);
            return this;
        }

        public Builder membership(NodeMembership membership) {
            this.membership = Optional.of(membership);
            return this;
        }

        public Builder wantedRestartGeneration(long wantedRestartGeneration) {
            this.wantedRestartGeneration = Optional.of(wantedRestartGeneration);
            return this;
        }

        public Builder currentRestartGeneration(long currentRestartGeneration) {
            this.currentRestartGeneration = Optional.of(currentRestartGeneration);
            return this;
        }

        public Builder wantedRebootGeneration(long wantedRebootGeneration) {
            this.wantedRebootGeneration = wantedRebootGeneration;
            return this;
        }

        public Builder currentRebootGeneration(long currentRebootGeneration) {
            this.currentRebootGeneration = currentRebootGeneration;
            return this;
        }

        public Builder wantedFirmwareCheck(Instant wantedFirmwareCheck) {
            this.wantedFirmwareCheck = Optional.of(wantedFirmwareCheck);
            return this;
        }

        public Builder currentFirmwareCheck(Instant currentFirmwareCheck) {
            this.currentFirmwareCheck = Optional.of(currentFirmwareCheck);
            return this;
        }

        public Builder resources(NodeResources resources) {
            this.resources = resources;
            return this;
        }

        public Builder vcpu(double vcpu) {
            return resources(resources.withVcpu(vcpu));
        }

        public Builder memoryGb(double memoryGb) {
            return resources(resources.withMemoryGb(memoryGb));
        }

        public Builder diskGb(double diskGb) {
            return resources(resources.withDiskGb(diskGb));
        }

        public Builder fastDisk(boolean fastDisk) {
            return resources(resources.with(fastDisk ? fast : slow));
        }

        public Builder bandwidthGbps(double bandwidthGbps) {
            return resources(resources.withBandwidthGbps(bandwidthGbps));
        }

        public Builder ipAddresses(Set<String> ipAddresses) {
            this.ipAddresses = ipAddresses;
            return this;
        }

        public Builder additionalIpAddresses(Set<String> additionalIpAddresses) {
            this.additionalIpAddresses = additionalIpAddresses;
            return this;
        }

        public Builder reports(NodeReports reports) {
            this.reports = reports;
            return this;
        }

        public Builder report(String reportId, JsonNode report) {
            this.reports.setReport(reportId, report);
            return this;
        }

        public Builder removeReport(String reportId) {
            reports.removeReport(reportId);
            return this;
        }

        public Builder parentHostname(String parentHostname) {
            this.parentHostname = Optional.of(parentHostname);
            return this;
        }

        public Builder archiveUri(URI archiveUri) {
            this.archiveUri = Optional.of(archiveUri);
            return this;
        }

        public Builder exclusiveTo(ApplicationId applicationId) {
            this.exclusiveTo = Optional.of(applicationId);
            return this;
        }

        public Builder updateFromNodeAttributes(NodeAttributes attributes) {
            attributes.getHostId().ifPresent(this::id);
            attributes.getDockerImage().ifPresent(this::currentDockerImage);
            attributes.getCurrentOsVersion().ifPresent(this::currentOsVersion);
            attributes.getRebootGeneration().ifPresent(this::currentRebootGeneration);
            attributes.getRestartGeneration().ifPresent(this::currentRestartGeneration);
            NodeReports.fromMap(attributes.getReports());

            return this;
        }

        public String hostname() {
            return hostname;
        }

        public Optional<DockerImage> wantedDockerImage() {
            return wantedDockerImage;
        }

        public Optional<DockerImage> currentDockerImage() {
            return currentDockerImage;
        }

        public NodeState state() {
            return state;
        }

        public NodeType type() {
            return type;
        }

        public String flavor() {
            return flavor;
        }

        public Optional<Version> wantedVespaVersion() {
            return wantedVespaVersion;
        }

        public Optional<Version> currentVespaVersion() {
            return currentVespaVersion;
        }

        public Optional<Version> wantedOsVersion() {
            return wantedOsVersion;
        }

        public Optional<Version> currentOsVersion() {
            return currentOsVersion;
        }

        public OrchestratorStatus orchestratorStatus() {
            return orchestratorStatus;
        }

        public Optional<ApplicationId> owner() {
            return owner;
        }

        public Optional<NodeMembership> membership() {
            return membership;
        }

        public Optional<Long> wantedRestartGeneration() {
            return wantedRestartGeneration;
        }

        public Optional<Long> currentRestartGeneration() {
            return currentRestartGeneration;
        }

        public long wantedRebootGeneration() {
            return wantedRebootGeneration;
        }

        public long currentRebootGeneration() {
            return currentRebootGeneration;
        }

        public NodeResources resources() {
            return resources;
        }

        public Set<String> ipAddresses() {
            return ipAddresses;
        }

        public Set<String> additionalIpAddresses() {
            return additionalIpAddresses;
        }

        public NodeReports reports() {
            return reports;
        }

        public Optional<String> parentHostname() {
            return parentHostname;
        }

        public Optional<URI> archiveUri() {
            return archiveUri;
        }

        public NodeSpec build() {
            return new NodeSpec(hostname, id, wantedDockerImage, currentDockerImage, state, type, flavor,
                    wantedVespaVersion, currentVespaVersion, wantedOsVersion, currentOsVersion, orchestratorStatus,
                    owner, membership,
                    wantedRestartGeneration, currentRestartGeneration,
                    wantedRebootGeneration, currentRebootGeneration,
                    wantedFirmwareCheck, currentFirmwareCheck, modelName,
                    resources, ipAddresses, additionalIpAddresses,
                    reports, parentHostname, archiveUri, exclusiveTo);
        }


        public static Builder testSpec(String hostname) {
            return testSpec(hostname, NodeState.active);
        }

        /**
         * Creates a NodeSpec.Builder that has the given hostname, in a given state, and some
         * reasonable values for the remaining required NodeSpec fields.
         */
        public static Builder testSpec(String hostname, NodeState state) {
            Builder builder = new Builder()
                    .hostname(hostname)
                    .state(state)
                    .type(NodeType.tenant)
                    .flavor("d-2-8-50")
                    .resources(new NodeResources(2, 8, 50, 10));

            // Set the required allocated fields
            if (EnumSet.of(NodeState.active, NodeState.inactive, NodeState.reserved).contains(state)) {
                builder .owner(ApplicationId.defaultId())
                        .membership(new NodeMembership("container", "my-id", "group", 0, false))
                        .wantedVespaVersion(Version.fromString("7.1.1"))
                        .wantedDockerImage(DockerImage.fromString("docker.domain.tld/repo/image:7.1.1"))
                        .currentRestartGeneration(0)
                        .wantedRestartGeneration(0);
            }

            return builder;
        }
    }

    private static void requireOptional(Optional<?> optional, String name) {
        if (optional == null || optional.isEmpty())
            throw new IllegalArgumentException(name + " must be set, was " + optional);
    }
}
