package com.yahoo.vespa.hosted.controller.maintenance;// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.application.ClusterInfo;
import com.yahoo.vespa.hosted.controller.application.Deployment;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Fetch info about hardware, hostnames and cluster specifications and update applications.
 *
 * @author smorgrav
 */
public class ClusterInfoMaintainer extends Maintainer {

    private final Controller controller;

    public ClusterInfoMaintainer(Controller controller, Duration duration, JobControl jobControl) {
        super(controller, duration, jobControl);
        this.controller = controller;
    }

    private  Map<ClusterSpec.Id, ClusterInfo> getClusterInfo(NodeRepositoryJsonModel nodes, NodeFlavors flavors) {
        Map<ClusterSpec.Id, ClusterInfo> infoMap = new HashMap<>();

        Map<String, List<NodeRepositoryJsonModel.Node>> clusters = nodes.nodes.stream()
                .filter(node -> node.membership != null)
                .collect(Collectors.groupingBy(ClusterInfoMaintainer::clusterid));

        for (String id : clusters.keySet()) {
            List<NodeRepositoryJsonModel.Node> clusterNodes = clusters.get(id);

            //Assume they are all equal and use first node as a representatitve for the cluster
            NodeRepositoryJsonModel.Node node = clusterNodes.get(0);

            Optional<Flavor> flavorOpt = flavors.getFlavor(node.nodeFlavor);

            List<String> hostnames = clusterNodes.stream().map(node1 -> { return node1.hostname; }).collect(Collectors.toList());

            ClusterInfo inf = new ClusterInfo(flavorOpt.get(), ClusterSpec.Type.from(node.membership.clusterType), hostnames);

            infoMap.put(new ClusterSpec.Id(id), inf);
        }

        return infoMap;
    }

    private static String clusterid(NodeRepositoryJsonModel.Node node) {
        return node.membership.clusterId;

    }

    private NodeRepositoryJsonModel getApplicationNodes(ApplicationId appId, Zone zone) {
        NodeRepositoryJsonModel nodesResponse = null;
        ObjectMapper mapper = new ObjectMapper();
        for (URI uri : controller.getConfigServerUris(null, null)) {
            try {
                nodesResponse = mapper.readValue(uri.toURL(), NodeRepositoryJsonModel.class);
                break;
            } catch (IOException ioe) {

            }
        }
        return nodesResponse;
    }

    @Override
    protected void maintain() {
        for (Application application : controller().applications().asList()) {
            for (Deployment deployment : application.deployments().values()) {
                Zone zone = deployment.zone();
                if (zone.nodeFlavors().isPresent()) {
                    NodeFlavors flavors = zone.nodeFlavors().get();
                    NodeRepositoryJsonModel appNodes = getApplicationNodes(application.id(), zone);

                    Map<ClusterSpec.Id, ClusterInfo> clusterInfo = getClusterInfo(appNodes, flavors);
                    application.with(deployment.withClusterInfo(clusterInfo));
                }
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NodeRepositoryJsonModel {

        public final List<Node> nodes;

        @JsonCreator
        public NodeRepositoryJsonModel(@JsonProperty("nodes") List<Node> nodes) {
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
                        @JsonProperty("minDiskAvailableGb") Double minDiskAvailableGb) {
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
}
