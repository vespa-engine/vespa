// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.application.ClusterInfo;
import com.yahoo.vespa.hosted.controller.application.Deployment;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Maintain info about hardware, hostnames and cluster specifications.
 *
 * This is used to calculate cost metrics for the application api.
 *
 * @author smorgrav
 */
public class ClusterInfoMaintainer extends Maintainer {

    private final Controller controller;

    ClusterInfoMaintainer(Controller controller, Duration duration, JobControl jobControl) {
        super(controller, duration, jobControl);
        this.controller = controller;
    }

    private static String clusterid(NodeRepositoryJsonModel.Node node) {
        return node.membership.clusterId;
    }

    private Map<ClusterSpec.Id, ClusterInfo> getClusterInfo(NodeRepositoryJsonModel nodes) {
        Map<ClusterSpec.Id, ClusterInfo> infoMap = new HashMap<>();

        // Group nodes by clusterid
        Map<String, List<NodeRepositoryJsonModel.Node>> clusters = nodes.nodes.stream()
                .filter(node -> node.membership != null)
                .collect(Collectors.groupingBy(ClusterInfoMaintainer::clusterid));

        // For each cluster - get info
        for (String id : clusters.keySet()) {
            List<NodeRepositoryJsonModel.Node> clusterNodes = clusters.get(id);

            //Assume they are all equal and use first node as a representatitve for the cluster
            NodeRepositoryJsonModel.Node node = clusterNodes.get(0);

            // Add to map
            List<String> hostnames = clusterNodes.stream().map(node1 -> node1.hostname).collect(Collectors.toList());
            ClusterInfo inf = new ClusterInfo(node.flavor, node.cost, ClusterSpec.Type.from(node.membership.clusterType), hostnames);
            infoMap.put(new ClusterSpec.Id(id), inf);
        }

        return infoMap;
    }

    // TODO use appId in url
    private NodeRepositoryJsonModel getApplicationNodes(ApplicationId appId, Zone zone) {
        NodeRepositoryJsonModel nodesResponse = null;
        ObjectMapper mapper = new ObjectMapper();
        for (URI uri : controller.getConfigServerUris(zone.environment(), zone.region())) {
            try {
                nodesResponse = mapper.readValue(uri.toURL(), NodeRepositoryJsonModel.class);
                break;
            } catch (IOException ioe) {
                //TODO
            }
        }
        return nodesResponse;
    }

    @Override
    protected void maintain() {

        for (Application application : controller().applications().asList()) {
            Lock lock = controller().applications().lock(application.id());
            for (Deployment deployment : application.deployments().values()) {
                NodeRepositoryJsonModel appNodes = getApplicationNodes(application.id(), deployment.zone());
                Map<ClusterSpec.Id, ClusterInfo> clusterInfo = getClusterInfo(appNodes);
                Application app = application.with(deployment.withClusterInfo(clusterInfo));
                controller.applications().store(app, lock);
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NodeRepositoryJsonModel {

        @JsonProperty("nodes")
        public List<Node> nodes;

        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Node {
            @JsonProperty("hostname")
            public String hostname;
            @JsonProperty("flavor")
            public String flavor;
            @JsonProperty("membership")
            public Membership membership;
            @JsonProperty("cost")
            public int cost;

            @JsonIgnoreProperties(ignoreUnknown = true)
            public static class Membership {
                @JsonProperty("clustertype")
                public String clusterType;
                @JsonProperty("clusterid")
                public String clusterId;
            }
        }
    }
}
