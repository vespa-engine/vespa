// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin;

import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.dockerapi.DockerImage;
import com.yahoo.vespa.hosted.provision.Node;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * @author stiankri
 */
// TODO: Rename to Node or NodeRepositoryNode
public class ContainerNodeSpec {
    public final String hostname;
    public final Node.State nodeState;
    public final NodeType nodeType;
    public final String nodeFlavor;
    public final String nodeCanonicalFlavor;

    public final Optional<DockerImage> wantedDockerImage;
    public final Optional<DockerImage> currentDockerImage;

    public final Optional<String> wantedVespaVersion;
    public final Optional<String> vespaVersion;

    public final Optional<Long> wantedRestartGeneration;
    public final Optional<Long> currentRestartGeneration;

    public final Optional<Long> wantedRebootGeneration;
    public final Optional<Long> currentRebootGeneration;

    public final Optional<Boolean> allowedToBeDown;
    public final Optional<Owner> owner;
    public final Optional<Membership> membership;

    public final Double minCpuCores;
    public final Double minMainMemoryAvailableGb;
    public final Double minDiskAvailableGb;

    public final Boolean fastDisk;
    public final Set<String> ipAddresses;

    public final Optional<String> hardwareDivergence;
    public final Optional<String> parentHostname;

    public ContainerNodeSpec(
            final String hostname,
            final Optional<DockerImage> wantedDockerImage,
            final Optional<DockerImage> currentDockerImage,
            final Node.State nodeState,
            final NodeType nodeType,
            final String nodeFlavor,
            final String nodeCanonicalFlavor,
            final Optional<String> wantedVespaVersion,
            final Optional<String> vespaVersion,
            final Optional<Boolean> allowedToBeDown,
            final Optional<Owner> owner,
            final Optional<Membership> membership,
            final Optional<Long> wantedRestartGeneration,
            final Optional<Long> currentRestartGeneration,
            final Optional<Long> wantedRebootGeneration,
            final Optional<Long> currentRebootGeneration,
            final Double minCpuCores,
            final Double minMainMemoryAvailableGb,
            final Double minDiskAvailableGb,
            final Boolean fastDisk,
            final Set<String> ipAddresses,
            final Optional<String> hardwareDivergence,
            final Optional<String> parentHostname) {
        Objects.requireNonNull(hostname);
        Objects.requireNonNull(nodeState);
        Objects.requireNonNull(nodeType);
        Objects.requireNonNull(nodeFlavor);
        Objects.requireNonNull(allowedToBeDown);
        Objects.requireNonNull(owner);
        Objects.requireNonNull(membership);
        Objects.requireNonNull(minCpuCores);
        Objects.requireNonNull(minMainMemoryAvailableGb);
        Objects.requireNonNull(minDiskAvailableGb);
        Objects.requireNonNull(fastDisk);
        Objects.requireNonNull(ipAddresses);

        this.hostname = hostname;
        this.wantedDockerImage = wantedDockerImage;
        this.currentDockerImage = currentDockerImage;
        this.nodeState = nodeState;
        this.nodeType = nodeType;
        this.nodeFlavor = nodeFlavor;
        this.nodeCanonicalFlavor = nodeCanonicalFlavor;
        this.wantedVespaVersion = wantedVespaVersion;
        this.vespaVersion = vespaVersion;
        this.allowedToBeDown = allowedToBeDown;
        this.owner = owner;
        this.membership = membership;
        this.wantedRestartGeneration = wantedRestartGeneration;
        this.currentRestartGeneration = currentRestartGeneration;
        this.wantedRebootGeneration = wantedRebootGeneration;
        this.currentRebootGeneration = currentRebootGeneration;
        this.minCpuCores = minCpuCores;
        this.minMainMemoryAvailableGb = minMainMemoryAvailableGb;
        this.minDiskAvailableGb = minDiskAvailableGb;
        this.fastDisk = fastDisk;
        this.ipAddresses = ipAddresses;
        this.hardwareDivergence = hardwareDivergence;
        this.parentHostname = parentHostname;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ContainerNodeSpec)) return false;

        ContainerNodeSpec that = (ContainerNodeSpec) o;

        return Objects.equals(hostname, that.hostname) &&
                Objects.equals(wantedDockerImage, that.wantedDockerImage) &&
                Objects.equals(currentDockerImage, that.currentDockerImage) &&
                Objects.equals(nodeState, that.nodeState) &&
                Objects.equals(nodeType, that.nodeType) &&
                Objects.equals(nodeFlavor, that.nodeFlavor) &&
                Objects.equals(nodeCanonicalFlavor, that.nodeCanonicalFlavor) &&
                Objects.equals(wantedVespaVersion, that.wantedVespaVersion) &&
                Objects.equals(vespaVersion, that.vespaVersion) &&
                Objects.equals(allowedToBeDown, that.allowedToBeDown) &&
                Objects.equals(owner, that.owner) &&
                Objects.equals(membership, that.membership) &&
                Objects.equals(wantedRestartGeneration, that.wantedRestartGeneration) &&
                Objects.equals(currentRestartGeneration, that.currentRestartGeneration) &&
                Objects.equals(wantedRebootGeneration, that.wantedRebootGeneration) &&
                Objects.equals(currentRebootGeneration, that.currentRebootGeneration) &&
                Objects.equals(minCpuCores, that.minCpuCores) &&
                Objects.equals(minMainMemoryAvailableGb, that.minMainMemoryAvailableGb) &&
                Objects.equals(minDiskAvailableGb, that.minDiskAvailableGb) &&
                Objects.equals(fastDisk, that.fastDisk) &&
                Objects.equals(ipAddresses, that.ipAddresses) &&
                Objects.equals(hardwareDivergence, that.hardwareDivergence) &&
                Objects.equals(parentHostname, that.parentHostname);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                hostname,
                wantedDockerImage,
                currentDockerImage,
                nodeState,
                nodeType,
                nodeFlavor,
                nodeCanonicalFlavor,
                wantedVespaVersion,
                vespaVersion,
                allowedToBeDown,
                owner,
                membership,
                wantedRestartGeneration,
                currentRestartGeneration,
                wantedRebootGeneration,
                currentRebootGeneration,
                minCpuCores,
                minMainMemoryAvailableGb,
                minDiskAvailableGb,
                fastDisk,
                ipAddresses,
                hardwareDivergence,
                parentHostname);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " {"
                + " hostname=" + hostname
                + " wantedDockerImage=" + wantedDockerImage
                + " currentDockerImage=" + currentDockerImage
                + " nodeState=" + nodeState
                + " nodeType=" + nodeType
                + " nodeFlavor=" + nodeFlavor
                + " nodeCanonicalFlavor=" + nodeCanonicalFlavor
                + " wantedVespaVersion=" + wantedVespaVersion
                + " vespaVersion=" + vespaVersion
                + " allowedToBeDown=" + allowedToBeDown
                + " owner=" + owner
                + " membership=" + membership
                + " minCpuCores=" + minCpuCores
                + " wantedRestartGeneration=" + wantedRestartGeneration
                + " currentRestartGeneration=" + currentRestartGeneration
                + " wantedRebootGeneration=" + wantedRebootGeneration
                + " currentRebootGeneration=" + currentRebootGeneration
                + " minMainMemoryAvailableGb=" + minMainMemoryAvailableGb
                + " minDiskAvailableGb=" + minDiskAvailableGb
                + " fastDisk=" + fastDisk
                + " ipAddresses=" + ipAddresses
                + " hardwareDivergence=" + hardwareDivergence
                + " parentHostname=" + parentHostname
                + " }";
    }

    public static class Owner {
        public final String tenant;
        public final String application;
        public final String instance;

        public Owner(String tenant, String application, String instance) {
            this.tenant = tenant;
            this.application = application;
            this.instance = instance;
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
        public final String clusterType;
        public final String clusterId;
        public final String group;
        public final int index;
        public final boolean retired;

        public Membership(String clusterType, String clusterId, String group, int index, boolean retired) {
            this.clusterType = clusterType;
            this.clusterId = clusterId;
            this.group = group;
            this.index = index;
            this.retired = retired;
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

    // For testing only
    public static class Builder {
        private String hostname;
        private Optional<DockerImage> wantedDockerImage = Optional.empty();
        private Optional<DockerImage> currentDockerImage = Optional.empty();
        private Node.State nodeState;
        private NodeType nodeType;
        private String nodeFlavor;
        private String nodeCanonicalFlavor;
        private Optional<String> wantedVespaVersion = Optional.empty();
        private Optional<String> vespaVersion = Optional.empty();
        private Optional<Boolean> allowedToBeDown = Optional.empty();
        private Optional<Owner> owner = Optional.empty();
        private Optional<Membership> membership = Optional.empty();
        private Optional<Long> wantedRestartGeneration = Optional.empty();
        private Optional<Long> currentRestartGeneration = Optional.empty();
        private Optional<Long> wantedRebootGeneration = Optional.empty();
        private Optional<Long> currentRebootGeneration = Optional.empty();
        private Double minCpuCores;
        private Double minMainMemoryAvailableGb;
        private Double minDiskAvailableGb;
        private Boolean fastDisk = false;
        private Set<String> ipAddresses = Collections.emptySet();
        private Optional<String> hardwareDivergence = Optional.empty();
        private Optional<String> parentHostname = Optional.empty();

        public Builder() {}

        public Builder(ContainerNodeSpec nodeSpec) {
            hostname(nodeSpec.hostname);
            nodeState(nodeSpec.nodeState);
            nodeType(nodeSpec.nodeType);
            nodeFlavor(nodeSpec.nodeFlavor);
            nodeCanonicalFlavor(nodeSpec.nodeCanonicalFlavor);
            minCpuCores(nodeSpec.minCpuCores);
            minMainMemoryAvailableGb(nodeSpec.minMainMemoryAvailableGb);
            minDiskAvailableGb(nodeSpec.minDiskAvailableGb);
            fastDisk(nodeSpec.fastDisk);
            ipAddresses(nodeSpec.ipAddresses);

            nodeSpec.wantedDockerImage.ifPresent(this::wantedDockerImage);
            nodeSpec.currentDockerImage.ifPresent(this::currentDockerImage);
            nodeSpec.wantedVespaVersion.ifPresent(this::wantedVespaVersion);
            nodeSpec.vespaVersion.ifPresent(this::vespaVersion);
            nodeSpec.owner.ifPresent(this::owner);
            nodeSpec.membership.ifPresent(this::membership);
            nodeSpec.wantedRestartGeneration.ifPresent(this::wantedRestartGeneration);
            nodeSpec.currentRestartGeneration.ifPresent(this::currentRestartGeneration);
            nodeSpec.wantedRebootGeneration.ifPresent(this::wantedRebootGeneration);
            nodeSpec.currentRebootGeneration.ifPresent(this::currentRebootGeneration);
            nodeSpec.hardwareDivergence.ifPresent(this::hardwareDivergence);
            nodeSpec.parentHostname.ifPresent(this::parentHostname);
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

        public Builder nodeState(Node.State nodeState) {
            this.nodeState = nodeState;
            return this;
        }
        public Builder nodeType(NodeType nodeType) {
            this.nodeType = nodeType;
            return this;
        }

        public Builder nodeFlavor(String nodeFlavor) {
            this.nodeFlavor = nodeFlavor;
            return this;
        }

        public Builder nodeCanonicalFlavor(String nodeCanonicalFlavor) {
            this.nodeCanonicalFlavor = nodeCanonicalFlavor;
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

        public Builder allowedToBeDown(boolean allowedToBeDown) {
            this.allowedToBeDown = Optional.of(allowedToBeDown);
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
            this.wantedRebootGeneration = Optional.of(wantedRebootGeneration);
            return this;
        }

        public Builder currentRebootGeneration(long currentRebootGeneration) {
            this.currentRebootGeneration = Optional.of(currentRebootGeneration);
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

        public Builder ipAddresses(Set<String> ipAddresses) {
            this.ipAddresses = ipAddresses;
            return this;
        }

        public Builder hardwareDivergence(String hardwareDivergence) {
            this.hardwareDivergence = Optional.of(hardwareDivergence);
            return this;
        }

        public Builder parentHostname(String parentHostname) {
            this.parentHostname = Optional.of(parentHostname);
            return this;
        }

        public ContainerNodeSpec build() {
            return new ContainerNodeSpec(hostname, wantedDockerImage, currentDockerImage, nodeState, nodeType,
                                         nodeFlavor, nodeCanonicalFlavor,
                                         wantedVespaVersion, vespaVersion, allowedToBeDown, owner, membership,
                                         wantedRestartGeneration, currentRestartGeneration,
                                         wantedRebootGeneration, currentRebootGeneration,
                                         minCpuCores, minMainMemoryAvailableGb, minDiskAvailableGb,
                                         fastDisk, ipAddresses, hardwareDivergence, parentHostname);
        }

    }
}
