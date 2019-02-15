// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.noderepository;

import com.fasterxml.jackson.databind.JsonNode;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.dockerapi.DockerImage;
import com.yahoo.vespa.hosted.provision.Node;

import java.time.Instant;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * @author stiankri
 */
public class NodeSpec {
    private final String hostname;
    private final Node.State state;
    private final NodeType nodeType;
    private final String flavor;
    private final String canonicalFlavor;

    private final Optional<DockerImage> wantedDockerImage;
    private final Optional<DockerImage> currentDockerImage;

    private final Optional<String> wantedVespaVersion;
    private final Optional<String> vespaVersion;

    private final Optional<String> wantedOsVersion;
    private final Optional<String> currentOsVersion;

    private final Optional<Long> wantedRestartGeneration;
    private final Optional<Long> currentRestartGeneration;

    private final long wantedRebootGeneration;
    private final long currentRebootGeneration;

    private final Optional<Instant> wantedFirmwareCheck;
    private final Optional<Instant> currentFirmwareCheck;

    private final Optional<String> modelName;

    private final Optional<Boolean> allowedToBeDown;
    private final Optional<Boolean> wantToDeprovision;
    private final Optional<Owner> owner;
    private final Optional<Membership> membership;

    private final double minCpuCores;
    private final double minMainMemoryAvailableGb;
    private final double minDiskAvailableGb;

    private final boolean fastDisk;
    private final double bandwidth;
    private final Set<String> ipAddresses;

    private final Optional<String> hardwareDivergence;
    private final Optional<String> hardwareFailureDescription;
    private final NodeReports reports;

    private final Optional<String> parentHostname;

    public NodeSpec(
            String hostname,
            Optional<DockerImage> wantedDockerImage,
            Optional<DockerImage> currentDockerImage,
            Node.State state,
            NodeType nodeType,
            String flavor,
            String canonicalFlavor,
            Optional<String> wantedVespaVersion,
            Optional<String> vespaVersion,
            Optional<String> wantedOsVersion,
            Optional<String> currentOsVersion,
            Optional<Boolean> allowedToBeDown,
            Optional<Boolean> wantToDeprovision,
            Optional<Owner> owner,
            Optional<Membership> membership,
            Optional<Long> wantedRestartGeneration,
            Optional<Long> currentRestartGeneration,
            long wantedRebootGeneration,
            long currentRebootGeneration,
            Optional<Instant> wantedFirmwareCheck,
            Optional<Instant> currentFirmwareCheck,
            Optional<String> modelName,
            double minCpuCores,
            double minMainMemoryAvailableGb,
            double minDiskAvailableGb,
            boolean fastDisk,
            double bandwidth,
            Set<String> ipAddresses,
            Optional<String> hardwareDivergence,
            Optional<String> hardwareFailureDescription,
            NodeReports reports,
            Optional<String> parentHostname) {
        this.hostname = Objects.requireNonNull(hostname);
        this.wantedDockerImage = Objects.requireNonNull(wantedDockerImage);
        this.currentDockerImage = Objects.requireNonNull(currentDockerImage);
        this.state = Objects.requireNonNull(state);
        this.nodeType = Objects.requireNonNull(nodeType);
        this.flavor = Objects.requireNonNull(flavor);
        this.canonicalFlavor = canonicalFlavor;
        this.modelName = modelName;
        this.wantedVespaVersion = Objects.requireNonNull(wantedVespaVersion);
        this.vespaVersion = Objects.requireNonNull(vespaVersion);
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
        this.minCpuCores = minCpuCores;
        this.minMainMemoryAvailableGb = minMainMemoryAvailableGb;
        this.minDiskAvailableGb = minDiskAvailableGb;
        this.fastDisk = fastDisk;
        this.bandwidth = bandwidth;
        this.ipAddresses = Objects.requireNonNull(ipAddresses);
        this.hardwareDivergence = Objects.requireNonNull(hardwareDivergence);
        this.hardwareFailureDescription = Objects.requireNonNull(hardwareFailureDescription);
        this.reports = Objects.requireNonNull(reports);
        this.parentHostname = Objects.requireNonNull(parentHostname);
    }

    public String getHostname() {
        return hostname;
    }

    public Node.State getState() {
        return state;
    }

    public NodeType getNodeType() {
        return nodeType;
    }

    public String getFlavor() {
        return flavor;
    }

    public String getCanonicalFlavor() {
        return canonicalFlavor;
    }

    public Optional<DockerImage> getWantedDockerImage() {
        return wantedDockerImage;
    }

    public Optional<DockerImage> getCurrentDockerImage() {
        return currentDockerImage;
    }

    public Optional<String> getWantedVespaVersion() {
        return wantedVespaVersion;
    }

    public Optional<String> getVespaVersion() {
        return vespaVersion;
    }

    public Optional<String> getCurrentOsVersion() {
        return currentOsVersion;
    }

    public Optional<String> getWantedOsVersion() {
        return wantedOsVersion;
    }

    public Optional<Long> getWantedRestartGeneration() {
        return wantedRestartGeneration;
    }

    public Optional<Long> getCurrentRestartGeneration() {
        return currentRestartGeneration;
    }

    public long getWantedRebootGeneration() {
        return wantedRebootGeneration;
    }

    public long getCurrentRebootGeneration() {
        return currentRebootGeneration;
    }

    public Optional<Instant> getWantedFirmwareCheck() {
        return wantedFirmwareCheck;
    }

    public Optional<Instant> getCurrentFirmwareCheck() {
        return currentFirmwareCheck;
    }

    public Optional<String> getModelName() {
        return modelName;
    }

    public Optional<Boolean> getAllowedToBeDown() {
        return allowedToBeDown;
    }

    public Optional<Boolean> getWantToDeprovision() {
        return wantToDeprovision;
    }

    public Optional<Owner> getOwner() {
        return owner;
    }

    public Optional<Membership> getMembership() {
        return membership;
    }

    public double getMinCpuCores() {
        return minCpuCores;
    }

    public double getMinMainMemoryAvailableGb() {
        return minMainMemoryAvailableGb;
    }

    public double getMinDiskAvailableGb() {
        return minDiskAvailableGb;
    }

    public boolean isFastDisk() {
        return fastDisk;
    }

    public double getBandwidth() {
        return bandwidth;
    }

    public Set<String> getIpAddresses() {
        return ipAddresses;
    }

    public Optional<String> getHardwareDivergence() {
        return hardwareDivergence;
    }

    public Optional<String> getHardwareFailureDescription() {
        return hardwareFailureDescription;
    }

    public NodeReports getReports() { return reports; }

    public Optional<String> getParentHostname() {
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
                Objects.equals(nodeType, that.nodeType) &&
                Objects.equals(flavor, that.flavor) &&
                Objects.equals(canonicalFlavor, that.canonicalFlavor) &&
                Objects.equals(wantedVespaVersion, that.wantedVespaVersion) &&
                Objects.equals(vespaVersion, that.vespaVersion) &&
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
                Objects.equals(minCpuCores, that.minCpuCores) &&
                Objects.equals(minMainMemoryAvailableGb, that.minMainMemoryAvailableGb) &&
                Objects.equals(minDiskAvailableGb, that.minDiskAvailableGb) &&
                Objects.equals(fastDisk, that.fastDisk) &&
                Objects.equals(bandwidth, that.bandwidth) &&
                Objects.equals(ipAddresses, that.ipAddresses) &&
                Objects.equals(hardwareDivergence, that.hardwareDivergence) &&
                Objects.equals(hardwareFailureDescription, that.hardwareFailureDescription) &&
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
                nodeType,
                flavor,
                canonicalFlavor,
                wantedVespaVersion,
                vespaVersion,
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
                minCpuCores,
                minMainMemoryAvailableGb,
                minDiskAvailableGb,
                fastDisk,
                bandwidth,
                ipAddresses,
                hardwareDivergence,
                hardwareFailureDescription,
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
                + " nodeType=" + nodeType
                + " flavor=" + flavor
                + " canonicalFlavor=" + canonicalFlavor
                + " wantedVespaVersion=" + wantedVespaVersion
                + " vespaVersion=" + vespaVersion
                + " wantedOsVersion=" + wantedOsVersion
                + " currentOsVersion=" + currentOsVersion
                + " allowedToBeDown=" + allowedToBeDown
                + " wantToDeprovision=" + wantToDeprovision
                + " owner=" + owner
                + " membership=" + membership
                + " minCpuCores=" + minCpuCores
                + " wantedRestartGeneration=" + wantedRestartGeneration
                + " currentRestartGeneration=" + currentRestartGeneration
                + " wantedRebootGeneration=" + wantedRebootGeneration
                + " currentRebootGeneration=" + currentRebootGeneration
                + " wantedFirmwareCheck=" + wantedFirmwareCheck
                + " currentFirmwareCheck=" + currentFirmwareCheck
                + " minMainMemoryAvailableGb=" + minMainMemoryAvailableGb
                + " minDiskAvailableGb=" + minDiskAvailableGb
                + " fastDisk=" + fastDisk
                + " bandwidth=" + bandwidth
                + " ipAddresses=" + ipAddresses
                + " hardwareDivergence=" + hardwareDivergence
                + " hardwareFailureDescription=" + hardwareFailureDescription
                + " reports=" + reports
                + " parentHostname=" + parentHostname
                + " }";
    }

    public static class Owner {
        private final String tenant;
        private final String application;
        private final String instance;

        public Owner(String tenant, String application, String instance) {
            this.tenant = tenant;
            this.application = application;
            this.instance = instance;
        }

        public String getTenant() {
            return tenant;
        }

        public String getApplication() {
            return application;
        }

        public String getInstance() {
            return instance;
        }

        public ApplicationId asApplicationId() {
            return ApplicationId.from(tenant, application, instance);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Owner owner = (Owner) o;

            if (!tenant.equals(owner.tenant)) return false;
            if (!application.equals(owner.application)) return false;
            return instance.equals(owner.instance);

        }

        @Override
        public int hashCode() {
            int result = tenant.hashCode();
            result = 31 * result + application.hashCode();
            result = 31 * result + instance.hashCode();
            return result;
        }

        public String toString() {
            return "Owner {" +
                    " tenant = " + tenant +
                    " application = " + application +
                    " instance = " + instance +
                    " }";
        }
    }

    public static class Membership {
        private final String clusterType;
        private final String clusterId;
        private final String group;
        private final int index;
        private final boolean retired;

        public Membership(String clusterType, String clusterId, String group, int index, boolean retired) {
            this.clusterType = clusterType;
            this.clusterId = clusterId;
            this.group = group;
            this.index = index;
            this.retired = retired;
        }

        public String getClusterType() {
            return clusterType;
        }

        public String getClusterId() {
            return clusterId;
        }

        public String getGroup() {
            return group;
        }

        public int getIndex() {
            return index;
        }

        public boolean isRetired() {
            return retired;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Membership that = (Membership) o;

            if (index != that.index) return false;
            if (retired != that.retired) return false;
            if (!clusterType.equals(that.clusterType)) return false;
            if (!clusterId.equals(that.clusterId)) return false;
            return group.equals(that.group);

        }

        @Override
        public int hashCode() {
            int result = clusterType.hashCode();
            result = 31 * result + clusterId.hashCode();
            result = 31 * result + group.hashCode();
            result = 31 * result + index;
            result = 31 * result + (retired ? 1 : 0);
            return result;
        }

        @Override
        public String toString() {
            return "Membership {" +
                    " clusterType = " + clusterType +
                    " clusterId = " + clusterId +
                    " group = " + group +
                    " index = " + index +
                    " retired = " + retired +
                    " }";
        }
    }

    public static class Builder {
        private String hostname;
        private Optional<DockerImage> wantedDockerImage = Optional.empty();
        private Optional<DockerImage> currentDockerImage = Optional.empty();
        private Node.State state;
        private NodeType nodeType;
        private String flavor;
        private String canonicalFlavor;
        private Optional<String> wantedVespaVersion = Optional.empty();
        private Optional<String> vespaVersion = Optional.empty();
        private Optional<String> wantedOsVersion = Optional.empty();
        private Optional<String> currentOsVersion = Optional.empty();
        private Optional<Boolean> allowedToBeDown = Optional.empty();
        private Optional<Boolean> wantToDeprovision = Optional.empty();
        private Optional<Owner> owner = Optional.empty();
        private Optional<Membership> membership = Optional.empty();
        private Optional<Long> wantedRestartGeneration = Optional.empty();
        private Optional<Long> currentRestartGeneration = Optional.empty();
        private long wantedRebootGeneration;
        private long currentRebootGeneration;
        private Optional<Instant> wantedFirmwareCheck = Optional.empty();
        private Optional<Instant> currentFirmwareCheck = Optional.empty();
        private Optional<String> modelName = Optional.empty();
        private double minCpuCores;
        private double minMainMemoryAvailableGb;
        private double minDiskAvailableGb;
        private boolean fastDisk = false;
        private double bandwidth;
        private Set<String> ipAddresses = Collections.emptySet();
        private Optional<String> hardwareDivergence = Optional.empty();
        private Optional<String> hardwareFailureDescription = Optional.empty();
        private NodeReports reports = new NodeReports();
        private Optional<String> parentHostname = Optional.empty();

        public Builder() {}

        public Builder(NodeSpec node) {
            hostname(node.hostname);
            state(node.state);
            nodeType(node.nodeType);
            flavor(node.flavor);
            canonicalFlavor(node.canonicalFlavor);
            minCpuCores(node.minCpuCores);
            minMainMemoryAvailableGb(node.minMainMemoryAvailableGb);
            minDiskAvailableGb(node.minDiskAvailableGb);
            fastDisk(node.fastDisk);
            bandwidth(node.bandwidth);
            ipAddresses(node.ipAddresses);
            wantedRebootGeneration(node.wantedRebootGeneration);
            currentRebootGeneration(node.currentRebootGeneration);
            reports(new NodeReports(node.reports));

            node.wantedDockerImage.ifPresent(this::wantedDockerImage);
            node.currentDockerImage.ifPresent(this::currentDockerImage);
            node.wantedVespaVersion.ifPresent(this::wantedVespaVersion);
            node.vespaVersion.ifPresent(this::vespaVersion);
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
            node.hardwareDivergence.ifPresent(this::hardwareDivergence);
            node.hardwareFailureDescription.ifPresent(this::hardwareFailureDescription);
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

        public Builder state(Node.State state) {
            this.state = state;
            return this;
        }

        public Builder nodeType(NodeType nodeType) {
            this.nodeType = nodeType;
            return this;
        }

        public Builder flavor(String flavor) {
            this.flavor = flavor;
            return this;
        }

        public Builder canonicalFlavor(String canonicalFlavor) {
            this.canonicalFlavor = canonicalFlavor;
            return this;
        }

        public Builder wantedVespaVersion(String wantedVespaVersion) {
            this.wantedVespaVersion = Optional.of(wantedVespaVersion);
            return this;
        }

        public Builder vespaVersion(String vespaVersion) {
            this.vespaVersion = Optional.of(vespaVersion);
            return this;
        }

        public Builder wantedOsVersion(String wantedOsVersion) {
            this.wantedOsVersion = Optional.of(wantedOsVersion);
            return this;
        }

        public Builder currentOsVersion(String currentOsVersion) {
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

        public Builder owner(Owner owner) {
            this.owner = Optional.of(owner);
            return this;
        }

        public Builder membership(Membership membership) {
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

        public Builder minCpuCores(double minCpuCores) {
            this.minCpuCores = minCpuCores;
            return this;
        }

        public Builder minMainMemoryAvailableGb(double minMainMemoryAvailableGb) {
            this.minMainMemoryAvailableGb = minMainMemoryAvailableGb;
            return this;
        }

        public Builder minDiskAvailableGb(double minDiskAvailableGb) {
            this.minDiskAvailableGb = minDiskAvailableGb;
            return this;
        }

        public Builder fastDisk(boolean fastDisk) {
            this.fastDisk = fastDisk;
            return this;
        }

        public Builder bandwidth(double bandwidth) {
            this.bandwidth = bandwidth;
            return this;
        }

        public Builder ipAddresses(Set<String> ipAddresses) {
            this.ipAddresses = ipAddresses;
            return this;
        }

        public Builder hardwareDivergence(String hardwareDivergence) {
            this.hardwareDivergence = Optional.of(hardwareDivergence);
            return this;
        }

        public Builder hardwareFailureDescription(String hardwareFailureDescription) {
            this.hardwareFailureDescription = Optional.of(hardwareFailureDescription);
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
            attributes.getHardwareDivergence().ifPresent(this::hardwareDivergence);
            attributes.getRebootGeneration().ifPresent(this::currentRebootGeneration);
            attributes.getRestartGeneration().ifPresent(this::currentRestartGeneration);
            attributes.getHardwareFailureDescription().ifPresent(this::hardwareFailureDescription);
            attributes.getWantToDeprovision().ifPresent(this::wantToDeprovision);
            NodeReports.fromMap(attributes.getReports());

            return this;
        }

        public String getHostname() {
            return hostname;
        }

        public Optional<DockerImage> getWantedDockerImage() {
            return wantedDockerImage;
        }

        public Optional<DockerImage> getCurrentDockerImage() {
            return currentDockerImage;
        }

        public Node.State getState() {
            return state;
        }

        public NodeType getNodeType() {
            return nodeType;
        }

        public String getFlavor() {
            return flavor;
        }

        public String getCanonicalFlavor() {
            return canonicalFlavor;
        }

        public Optional<String> getWantedVespaVersion() {
            return wantedVespaVersion;
        }

        public Optional<String> getVespaVersion() {
            return vespaVersion;
        }

        public Optional<String> getWantedOsVersion() {
            return wantedOsVersion;
        }

        public Optional<String> getCurrentOsVersion() {
            return currentOsVersion;
        }

        public Optional<Boolean> getAllowedToBeDown() {
            return allowedToBeDown;
        }

        public Optional<Boolean> getWantToDeprovision() {
            return wantToDeprovision;
        }

        public Optional<Owner> getOwner() {
            return owner;
        }

        public Optional<Membership> getMembership() {
            return membership;
        }

        public Optional<Long> getWantedRestartGeneration() {
            return wantedRestartGeneration;
        }

        public Optional<Long> getCurrentRestartGeneration() {
            return currentRestartGeneration;
        }

        public long getWantedRebootGeneration() {
            return wantedRebootGeneration;
        }

        public long getCurrentRebootGeneration() {
            return currentRebootGeneration;
        }

        public double getMinCpuCores() {
            return minCpuCores;
        }

        public double getMinMainMemoryAvailableGb() {
            return minMainMemoryAvailableGb;
        }

        public double getMinDiskAvailableGb() {
            return minDiskAvailableGb;
        }

        public boolean isFastDisk() {
            return fastDisk;
        }

        public double getBandwidth() {
            return bandwidth;
        }

        public Set<String> getIpAddresses() {
            return ipAddresses;
        }

        public Optional<String> getHardwareDivergence() {
            return hardwareDivergence;
        }

        public Optional<String> getHardwareFailureDescription() {
            return hardwareFailureDescription;
        }

        public NodeReports getReports() {
            return reports;
        }

        public Optional<String> getParentHostname() {
            return parentHostname;
        }

        public NodeSpec build() {
            return new NodeSpec(hostname, wantedDockerImage, currentDockerImage, state, nodeType,
                    flavor, canonicalFlavor,
                    wantedVespaVersion, vespaVersion, wantedOsVersion, currentOsVersion, allowedToBeDown, wantToDeprovision,
                    owner, membership,
                    wantedRestartGeneration, currentRestartGeneration,
                    wantedRebootGeneration, currentRebootGeneration,
                    wantedFirmwareCheck, currentFirmwareCheck, modelName,
                    minCpuCores, minMainMemoryAvailableGb, minDiskAvailableGb,
                    fastDisk, bandwidth, ipAddresses, hardwareDivergence, hardwareFailureDescription,
                    reports, parentHostname);
        }

    }
}
