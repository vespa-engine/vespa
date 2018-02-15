// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.noderepository.bindings;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * This class represents a response from the /nodes/v2/node/ API. It is designed to be
 * usable by any module, by not depending itself on any module-specific classes.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GetNodesResponse {

    public final List<Node> nodes;

    @JsonCreator
    public GetNodesResponse(@JsonProperty("nodes") List<Node> nodes) {
        this.nodes = Collections.unmodifiableList(nodes);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Node {

        public final String hostname;
        public final String wantedDockerImage;
        public final String currentDockerImage;
        public final String nodeState;
        public final String nodeType;
        public final String nodeFlavor;
        public final String nodeCanonicalFlavor;
        public final String wantedVespaVersion;
        public final String vespaVersion;
        public final Owner owner;
        public final Membership membership;
        public final Long wantedRestartGeneration;
        public final Long currentRestartGeneration;
        public final Long wantedRebootGeneration;
        public final Long currentRebootGeneration;
        public final Double minCpuCores;
        public final Double minMainMemoryAvailableGb;
        public final Double minDiskAvailableGb;
        public final Boolean fastDisk;
        public final Set<String> ipAddresses;
        public final String hardwareDivergence;

        @JsonCreator
        public Node(@JsonProperty("id") String hostname,
                    @JsonProperty("wantedDockerImage") String wantedDockerImage,
                    @JsonProperty("currentDockerImage") String currentDockerImage,
                    @JsonProperty("state") String nodeState,
                    @JsonProperty("type") String nodeType,
                    @JsonProperty("flavor") String nodeFlavor,
                    @JsonProperty("canonicalFlavor") String nodeCanonicalFlavor,
                    @JsonProperty("wantedVespaVersion") String wantedVespaVersion,
                    @JsonProperty("vespaVersion") String vespaVersion,
                    @JsonProperty("owner") Owner owner,
                    @JsonProperty("membership") Membership membership,
                    @JsonProperty("restartGeneration") Long wantedRestartGeneration,
                    @JsonProperty("currentRestartGeneration") Long currentRestartGeneration,
                    @JsonProperty("rebootGeneration") Long wantedRebootGeneration,
                    @JsonProperty("currentRebootGeneration") Long currentRebootGeneration,
                    @JsonProperty("minCpuCores") Double minCpuCores,
                    @JsonProperty("minMainMemoryAvailableGb") Double minMainMemoryAvailableGb,
                    @JsonProperty("minDiskAvailableGb") Double minDiskAvailableGb,
                    @JsonProperty("fastDisk") Boolean fastDisk,
                    @JsonProperty("ipAddresses") Set<String> ipAddresses,
                    @JsonProperty("hardwareDivergence") String hardwareDivergence) {
            this.hostname = hostname;
            this.wantedDockerImage = wantedDockerImage;
            this.currentDockerImage = currentDockerImage;
            this.nodeState = nodeState;
            this.nodeType = nodeType;
            this.nodeFlavor = nodeFlavor;
            this.nodeCanonicalFlavor = nodeCanonicalFlavor;
            this.wantedVespaVersion = wantedVespaVersion;
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
            this.fastDisk = fastDisk;
            this.ipAddresses = ipAddresses;
            this.hardwareDivergence = hardwareDivergence;
        }

        public String toString() {
            return "Node {"
                    + " containerHostname = " + hostname
                    + " wantedDockerImage = " + wantedDockerImage
                    + " currentDockerImage = " + currentDockerImage
                    + " nodeState = " + nodeState
                    + " nodeType = " + nodeType
                    + " nodeFlavor = " + nodeFlavor
                    + " wantedVespaVersion = " + wantedVespaVersion
                    + " vespaVersion = " + vespaVersion
                    + " owner = " + owner
                    + " membership = " + membership
                    + " wantedRestartGeneration = " + wantedRestartGeneration
                    + " currentRestartGeneration = " + currentRestartGeneration
                    + " wantedRebootGeneration = " + wantedRebootGeneration
                    + " currentRebootGeneration = " + currentRebootGeneration
                    + " minCpuCores = " + minCpuCores
                    + " minMainMemoryAvailableGb = " + minMainMemoryAvailableGb
                    + " minDiskAvailableGb = " + minDiskAvailableGb
                    + " }";
        }


        public static class Owner {
            public final String tenant;
            public final String application;
            public final String instance;

            public Owner(
                    @JsonProperty("tenant") String tenant,
                    @JsonProperty("application") String application,
                    @JsonProperty("instance") String instance) {
                this.tenant = tenant;
                this.application = application;
                this.instance = instance;
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

            public Membership(
                    @JsonProperty("clustertype") String clusterType,
                    @JsonProperty("clusterid") String clusterId,
                    @JsonProperty("group") String group,
                    @JsonProperty("index") int index,
                    @JsonProperty("retired") boolean retired) {
                this.clusterType = clusterType;
                this.clusterId = clusterId;
                this.group = group;
                this.index = index;
                this.retired = retired;
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
    }
}
