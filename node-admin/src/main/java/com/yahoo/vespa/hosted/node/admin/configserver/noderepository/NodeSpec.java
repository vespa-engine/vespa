// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.noderepository;

import com.fasterxml.jackson.databind.JsonNode;
import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;

import java.time.Instant;
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
    private final NodeState state;
    private final NodeType type;
    private final String flavor;
    private final Optional<Double> cpuCores;

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

    private final Optional<Boolean> allowedToBeDown;
    private final Optional<Boolean> wantToDeprovision;
    private final Optional<ApplicationId> owner;
    private final Optional<NodeMembership> membership;

    private final NodeResources resources;
    private final Set<String> ipAddresses;
    private final Set<String> additionalIpAddresses;

    private final NodeReports reports;

    private final Optional<String> parentHostname;

    public NodeSpec(
            String hostname,
            Optional<DockerImage> wantedDockerImage,
            Optional<DockerImage> currentDockerImage,
            NodeState state,
            NodeType type,
            String flavor,
            Optional<Double> cpuCores,
            Optional<Version> wantedVespaVersion,
            Optional<Version> currentVespaVersion,
            Optional<Version> wantedOsVersion,
            Optional<Version> currentOsVersion,
            Optional<Boolean> allowedToBeDown,
            Optional<Boolean> wantToDeprovision,
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
            Optional<String> parentHostname) {
        if (state == NodeState.active) {
            Objects.requireNonNull(wantedVespaVersion, "Unknown vespa version for active node");
            Objects.requireNonNull(wantedDockerImage, "Unknown docker image for active node");
            Objects.requireNonNull(wantedRestartGeneration, "Unknown restartGeneration for active node");
            Objects.requireNonNull(currentRestartGeneration, "Unknown currentRestartGeneration for active node");
        }

        this.hostname = Objects.requireNonNull(hostname);
        this.wantedDockerImage = Objects.requireNonNull(wantedDockerImage);
        this.currentDockerImage = Objects.requireNonNull(currentDockerImage);
        this.state = Objects.requireNonNull(state);
        this.type = Objects.requireNonNull(type);
        this.flavor = Objects.requireNonNull(flavor);
        this.cpuCores = Objects.requireNonNull(cpuCores);
        this.modelName = Objects.requireNonNull(modelName);
        this.wantedVespaVersion = Objects.requireNonNull(wantedVespaVersion);
        this.currentVespaVersion = Objects.requireNonNull(currentVespaVersion);
        this.wantedOsVersion = Objects.requireNonNull(wantedOsVersion);
        this.currentOsVersion = Objects.requireNonNull(currentOsVersion);
        this.allowedToBeDown = Objects.requireNonNull(allowedToBeDown);
        this.wantToDeprovision = Objects.requireNonNull(wantToDeprovision);
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
    }

    public String hostname() {
        return hostname;
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

    public Optional<Double> cpuCores() {
        return cpuCores;
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

    public Optional<Boolean> allowedToBeDown() {
        return allowedToBeDown;
    }

    public Optional<Boolean> wantToDeprovision() {
        return wantToDeprovision;
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

    public double vcpus() {
        return resources.vcpu();
    }

    public double memoryGb() {
        return resources.memoryGb();
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NodeSpec)) return false;

        NodeSpec that = (NodeSpec) o;

        return Objects.equals(hostname, that.hostname) &&
                Objects.equals(wantedDockerImage, that.wantedDockerImage) &&
                Objects.equals(currentDockerImage, that.currentDockerImage) &&
                Objects.equals(state, that.state) &&
                Objects.equals(type, that.type) &&
                Objects.equals(flavor, that.flavor) &&
                Objects.equals(cpuCores, that.cpuCores) &&
                Objects.equals(wantedVespaVersion, that.wantedVespaVersion) &&
                Objects.equals(currentVespaVersion, that.currentVespaVersion) &&
                Objects.equals(wantedOsVersion, that.wantedOsVersion) &&
                Objects.equals(currentOsVersion, that.currentOsVersion) &&
                Objects.equals(allowedToBeDown, that.allowedToBeDown) &&
                Objects.equals(wantToDeprovision, that.wantToDeprovision) &&
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
                Objects.equals(parentHostname, that.parentHostname);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                hostname,
                wantedDockerImage,
                currentDockerImage,
                state,
                type,
                flavor,
                cpuCores,
                wantedVespaVersion,
                currentVespaVersion,
                wantedOsVersion,
                currentOsVersion,
                allowedToBeDown,
                wantToDeprovision,
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
                parentHostname);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " {"
                + " hostname=" + hostname
                + " wantedDockerImage=" + wantedDockerImage
                + " currentDockerImage=" + currentDockerImage
                + " state=" + state
                + " type=" + type
                + " flavor=" + flavor
                + " cpuCores=" + cpuCores
                + " wantedVespaVersion=" + wantedVespaVersion
                + " currentVespaVersion=" + currentVespaVersion
                + " wantedOsVersion=" + wantedOsVersion
                + " currentOsVersion=" + currentOsVersion
                + " allowedToBeDown=" + allowedToBeDown
                + " wantToDeprovision=" + wantToDeprovision
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
                + " }";
    }

    public static class Builder {
        private String hostname;
        private NodeState state;
        private NodeType type;
        private String flavor;
        private Optional<Double> cpuCores = Optional.empty();
        private Optional<DockerImage> wantedDockerImage = Optional.empty();
        private Optional<DockerImage> currentDockerImage = Optional.empty();
        private Optional<Version> wantedVespaVersion = Optional.empty();
        private Optional<Version> currentVespaVersion = Optional.empty();
        private Optional<Version> wantedOsVersion = Optional.empty();
        private Optional<Version> currentOsVersion = Optional.empty();
        private Optional<Boolean> allowedToBeDown = Optional.empty();
        private Optional<Boolean> wantToDeprovision = Optional.empty();
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
            reports(new NodeReports(node.reports));
            node.cpuCores.ifPresent(this::cpuCores);
            node.wantedDockerImage.ifPresent(this::wantedDockerImage);
            node.currentDockerImage.ifPresent(this::currentDockerImage);
            node.wantedVespaVersion.ifPresent(this::wantedVespaVersion);
            node.currentVespaVersion.ifPresent(this::currentVespaVersion);
            node.wantedOsVersion.ifPresent(this::wantedOsVersion);
            node.currentOsVersion.ifPresent(this::currentOsVersion);
            node.allowedToBeDown.ifPresent(this::allowedToBeDown);
            node.wantToDeprovision.ifPresent(this::wantToDeprovision);
            node.owner.ifPresent(this::owner);
            node.membership.ifPresent(this::membership);
            node.wantedRestartGeneration.ifPresent(this::wantedRestartGeneration);
            node.currentRestartGeneration.ifPresent(this::currentRestartGeneration);
            node.wantedFirmwareCheck.ifPresent(this::wantedFirmwareCheck);
            node.currentFirmwareCheck.ifPresent(this::currentFirmwareCheck);
            node.parentHostname.ifPresent(this::parentHostname);
        }

        public Builder hostname(String hostname) {
            this.hostname = hostname;
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

        public Builder cpuCores(double cpuCores) {
            this.cpuCores = Optional.of(cpuCores);
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

        public Builder allowedToBeDown(boolean allowedToBeDown) {
            this.allowedToBeDown = Optional.of(allowedToBeDown);
            return this;
        }

        public Builder wantToDeprovision(boolean wantToDeprovision) {
            this.wantToDeprovision = Optional.of(wantToDeprovision);
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

        public Builder vcpus(double vcpus) {
            return resources(resources.withVcpu(vcpus));
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

        public Builder updateFromNodeAttributes(NodeAttributes attributes) {
            attributes.getDockerImage().ifPresent(this::currentDockerImage);
            attributes.getCurrentOsVersion().ifPresent(this::currentOsVersion);
            attributes.getRebootGeneration().ifPresent(this::currentRebootGeneration);
            attributes.getRestartGeneration().ifPresent(this::currentRestartGeneration);
            attributes.getWantToDeprovision().ifPresent(this::wantToDeprovision);
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

        public Optional<Boolean> allowedToBeDown() {
            return allowedToBeDown;
        }

        public Optional<Boolean> wantToDeprovision() {
            return wantToDeprovision;
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

        public NodeSpec build() {
            return new NodeSpec(hostname, wantedDockerImage, currentDockerImage, state, type, flavor, cpuCores,
                    wantedVespaVersion, currentVespaVersion, wantedOsVersion, currentOsVersion, allowedToBeDown, wantToDeprovision,
                    owner, membership,
                    wantedRestartGeneration, currentRestartGeneration,
                    wantedRebootGeneration, currentRebootGeneration,
                    wantedFirmwareCheck, currentFirmwareCheck, modelName,
                    resources, ipAddresses, additionalIpAddresses,
                    reports, parentHostname);
        }

    }
}
