// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin;

import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.dockerapi.DockerImage;
import com.yahoo.vespa.hosted.provision.Node;

import java.util.Objects;
import java.util.Optional;

/**
 * @author stiankri
 */
// TODO: Rename to Node or NodeRepositoryNode
public class ContainerNodeSpec {
    public final String hostname;
    public final Optional<DockerImage> wantedDockerImage;
    public final ContainerName containerName;
    public final Node.State nodeState;
    public final String nodeType;
    public final String nodeFlavor;
    public final Optional<String> vespaVersion;
    public final Optional<Owner> owner;
    public final Optional<Membership> membership;
    public final Optional<Long> wantedRestartGeneration;
    public final Optional<Long> currentRestartGeneration;
    public final Optional<Long> wantedRebootGeneration;
    public final Optional<Long> currentRebootGeneration;
    public final Optional<Double> minCpuCores;
    public final Optional<Double> minMainMemoryAvailableGb;
    public final Optional<Double> minDiskAvailableGb;

    public ContainerNodeSpec(
            final String hostname,
            final Optional<DockerImage> wantedDockerImage,
            final ContainerName containerName,
            final Node.State nodeState,
            final String nodeType,
            final String nodeFlavor,
            final Optional<String> vespaVersion,
            final Optional<Owner> owner,
            final Optional<Membership> membership,
            final Optional<Long> wantedRestartGeneration,
            final Optional<Long> currentRestartGeneration,
            final Optional<Long> wantedRebootGeneration,
            final Optional<Long> currentRebootGeneration,
            final Optional<Double> minCpuCores,
            final Optional<Double> minMainMemoryAvailableGb,
            final Optional<Double> minDiskAvailableGb) {
        Objects.requireNonNull(hostname);
        Objects.requireNonNull(containerName);
        Objects.requireNonNull(nodeState);
        Objects.requireNonNull(nodeType);
        Objects.requireNonNull(nodeFlavor);

        this.hostname = hostname;
        this.wantedDockerImage = wantedDockerImage;
        this.containerName = containerName;
        this.nodeState = nodeState;
        this.nodeType = nodeType;
        this.nodeFlavor = nodeFlavor;
        this.vespaVersion = vespaVersion;
        this.owner = owner;
        this.membership = membership;
        this.wantedRestartGeneration = wantedRestartGeneration;
        this.currentRestartGeneration = currentRestartGeneration;
        this.wantedRebootGeneration = wantedRebootGeneration;
        this.currentRebootGeneration = currentRebootGeneration;
        this.minCpuCores = minCpuCores;
        this.minMainMemoryAvailableGb = minMainMemoryAvailableGb;
        this.minDiskAvailableGb = minDiskAvailableGb;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ContainerNodeSpec)) return false;

        ContainerNodeSpec that = (ContainerNodeSpec) o;

        return Objects.equals(hostname, that.hostname) &&
                Objects.equals(wantedDockerImage, that.wantedDockerImage) &&
                Objects.equals(containerName, that.containerName) &&
                Objects.equals(nodeState, that.nodeState) &&
                Objects.equals(nodeType, that.nodeType) &&
                Objects.equals(nodeFlavor, that.nodeFlavor) &&
                Objects.equals(vespaVersion, that.vespaVersion) &&
                Objects.equals(owner, that.owner) &&
                Objects.equals(membership, that.membership) &&
                Objects.equals(wantedRestartGeneration, that.wantedRestartGeneration) &&
                Objects.equals(currentRestartGeneration, that.currentRestartGeneration) &&
                Objects.equals(wantedRebootGeneration, that.wantedRebootGeneration) &&
                Objects.equals(currentRebootGeneration, that.currentRebootGeneration) &&
                Objects.equals(minCpuCores, that.minCpuCores) &&
                Objects.equals(minMainMemoryAvailableGb, that.minMainMemoryAvailableGb) &&
                Objects.equals(minDiskAvailableGb, that.minDiskAvailableGb);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                hostname,
                wantedDockerImage,
                containerName,
                nodeState,
                nodeType,
                nodeFlavor,
                vespaVersion,
                owner,
                membership,
                wantedRestartGeneration,
                currentRestartGeneration,
                wantedRebootGeneration,
                currentRebootGeneration,
                minCpuCores,
                minMainMemoryAvailableGb,
                minDiskAvailableGb);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " {"
                + " hostname=" + hostname
                + " wantedDockerImage=" + wantedDockerImage
                + " containerName=" + containerName
                + " nodeState=" + nodeState
                + " nodeType = " + nodeType
                + " nodeFlavor = " + nodeFlavor
                + " vespaVersion = " + vespaVersion
                + " owner = " + owner
                + " membership = " + membership
                + " minCpuCores=" + minCpuCores
                + " wantedRestartGeneration=" + wantedRestartGeneration
                + " currentRestartGeneration=" + currentRestartGeneration
                + " wantedRebootGeneration=" + wantedRebootGeneration
                + " currentRebootGeneration=" + currentRebootGeneration
                + " minMainMemoryAvailableGb=" + minMainMemoryAvailableGb
                + " minDiskAvailableGb=" + minDiskAvailableGb
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

    public static class Builder {
        private String hostname;
        private Optional<DockerImage> wantedDockerImage = Optional.empty();
        private ContainerName containerName;
        private Node.State nodeState;
        private String nodeType;
        private String nodeFlavor;
        private Optional<String> vespaVersion = Optional.empty();
        private Optional<Owner> owner = Optional.empty();
        private Optional<Membership> membership = Optional.empty();
        private Optional<Long> wantedRestartGeneration = Optional.empty();
        private Optional<Long> currentRestartGeneration = Optional.empty();
        private Optional<Long> wantedRebootGeneration = Optional.empty();
        private Optional<Long> currentRebootGeneration = Optional.empty();
        private Optional<Double> minCpuCores = Optional.of(1d);
        private Optional<Double> minMainMemoryAvailableGb = Optional.of(1d);
        private Optional<Double> minDiskAvailableGb = Optional.of(1d);

        public Builder hostname(String hostname) {
            this.hostname = hostname;
            return this;
        }

        public Builder wantedDockerImage(Optional<DockerImage> wantedDockerImage) {
            this.wantedDockerImage = wantedDockerImage;
            return this;
        }

        public Builder containerName(ContainerName containerName) {
            this.containerName = containerName;
            return this;
        }

        public Builder nodeState(Node.State nodeState) {
            this.nodeState = nodeState;
            return this;
        }
        public Builder nodeType(String nodeType) {
            this.nodeType = nodeType;
            return this;
        }

        public Builder nodeFlavor(String nodeFlavor) {
            this.nodeFlavor = nodeFlavor;
            return this;
        }

        public Builder vespaVersion(Optional<String> vespaVersion) {
            this.vespaVersion = vespaVersion;
            return this;
        }

        public Builder owner(Optional<Owner> owner) {
            this.owner = owner;
            return this;
        }

        public Builder membership(Optional<Membership> membership) {
            this.membership = membership;
            return this;
        }

        public Builder wantedRestartGeneration(Optional<Long> wantedRestartGeneration) {
            this.wantedRestartGeneration = wantedRestartGeneration;
            return this;
        }

        public Builder currentRestartGeneration(Optional<Long> currentRestartGeneration) {
            this.currentRestartGeneration = currentRestartGeneration;
            return this;
        }

        public Builder wantedRebootGeneration(Optional<Long> wantedRebootGeneration) {
            this.wantedRebootGeneration = wantedRebootGeneration;
            return this;
        }

        public Builder currentRebootGeneration(Optional<Long> currentRebootGeneration) {
            this.currentRebootGeneration = currentRebootGeneration;
            return this;
        }

        public Builder minCpuCores(Optional<Double> minCpuCores) {
            this.minCpuCores = minCpuCores;
            return this;
        }

        public Builder minMainMemoryAvailableGb(Optional<Double> minMainMemoryAvailableGb) {
            this.minMainMemoryAvailableGb = minMainMemoryAvailableGb;
            return this;
        }

        public Builder minDiskAvailableGb(Optional<Double> minDiskAvailableGb) {
            this.minDiskAvailableGb = minDiskAvailableGb;
            return this;
        }

        public ContainerNodeSpec build() {
            return new ContainerNodeSpec(hostname, wantedDockerImage, containerName, nodeState, nodeType, nodeFlavor,
                                         vespaVersion, owner, membership,
                                         wantedRestartGeneration, currentRestartGeneration,
                                         wantedRebootGeneration, currentRebootGeneration,
                                         minCpuCores, minMainMemoryAvailableGb, minDiskAvailableGb);
        }

    }
}
