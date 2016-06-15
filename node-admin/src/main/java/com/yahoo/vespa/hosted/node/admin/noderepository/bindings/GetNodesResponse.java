// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.noderepository.bindings;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

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
        public final Long wantedRestartGeneration;
        public final Long currentRestartGeneration;
        public final Double minCpuCores;
        public final Double minMainMemoryAvailableGb;
        public final Double minDiskAvailableGb;

        @JsonCreator
        public Node(@JsonProperty("id") String hostname,
                    @JsonProperty("wantedDockerImage") String wantedDockerImage,
                    @JsonProperty("currentDockerImage") String currentDockerImage,
                    @JsonProperty("state") String nodeState,
                    @JsonProperty("restartGeneration") Long wantedRestartGeneration,
                    @JsonProperty("currentRestartGeneration") Long currentRestartGeneration,
                    @JsonProperty("minCpuCores") Double minCpuCores,
                    @JsonProperty("minMainMemoryAvailableGb") Double minMainMemoryAvailableGb,
                    @JsonProperty("minDiskAvailableGb") Double minDiskAvailableGb) {
            this.hostname = hostname;
            this.wantedDockerImage = wantedDockerImage;
            this.currentDockerImage = currentDockerImage;
            this.nodeState = nodeState;
            this.wantedRestartGeneration = wantedRestartGeneration;
            this.currentRestartGeneration = currentRestartGeneration;
            this.minCpuCores = minCpuCores;
            this.minMainMemoryAvailableGb = minMainMemoryAvailableGb;
            this.minDiskAvailableGb = minDiskAvailableGb;
        }

        public String toString() {
            return "Node {"
                    + " containerHostname = " + hostname
                    + " wantedDockerImage = " + wantedDockerImage
                    + " currentDockerImage = " + currentDockerImage
                    + " nodeState = " + nodeState
                    + " wantedRestartGeneration = " + wantedRestartGeneration
                    + " currentRestartGeneration = " + currentRestartGeneration
                    + " minCpuCores = " + minCpuCores
                    + " minMainMemoryAvailableGb = " + minMainMemoryAvailableGb
                    + " minDiskAvailableGb = " + minDiskAvailableGb
                    + " }";
        }
    }
}
