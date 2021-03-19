// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.NodeRepository;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeRepositoryNode;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeState;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ChangeManagementAssessor {

    private final NodeRepository nodeRepository;

    public ChangeManagementAssessor(NodeRepository nodeRepository) {
        this.nodeRepository = nodeRepository;
    }

    public Assessment assessment(List<String> impactedHostnames, ZoneId zone) {
        return assessmentInner(impactedHostnames, nodeRepository.listNodes(zone).nodes(), zone);
    }

    Assessment assessmentInner(List<String> impactedHostnames, List<NodeRepositoryNode> allNodes, ZoneId zone) {

        // Group impacted application nodes by parent host
        Map<String, List<NodeRepositoryNode>> prParentHost = allNodes.stream()
                .filter(nodeRepositoryNode -> nodeRepositoryNode.getState() == NodeState.active) //TODO look at more states?
                .filter(node -> impactedHostnames.contains(node.getParentHostname() == null ? "" : node.getParentHostname()))
                .collect(Collectors.groupingBy(NodeRepositoryNode::getParentHostname));

        // Group nodes pr cluster
        Map<String, List<NodeRepositoryNode>> prCluster = prParentHost.values()
                .stream()
                .flatMap(Collection::stream)
                .collect(Collectors.groupingBy(ChangeManagementAssessor::clusterKey));

        boolean allHostsReplacable = nodeRepository.isReplaceable(
                zone,
                impactedHostnames.stream()
                        .map(HostName::from)
                        .collect(Collectors.toList())
        );

        // Report assessment pr cluster
        var clusterAssessments = prCluster.entrySet().stream().map((entry) -> {
            String key = entry.getKey();
            List<NodeRepositoryNode> nodes = entry.getValue();
            String app = Arrays.stream(key.split(":")).limit(3).collect(Collectors.joining(":"));
            String cluster = Arrays.stream(key.split(":")).skip(3).collect(Collectors.joining(":"));

            long[] totalStats = clusterStats(key, allNodes);
            long[] impactedStats = clusterStats(key, nodes);

            ClusterAssessment assessment = new ClusterAssessment();
            assessment.app = app;
            assessment.zone = zone.value();
            assessment.cluster = cluster;
            assessment.clusterSize = totalStats[0];
            assessment.clusterImpact = impactedStats[0];
            assessment.groupsTotal = totalStats[1];
            assessment.groupsImpact = impactedStats[1];


            // TODO check upgrade policy
            assessment.upgradePolicy = "na";
            // TODO do some heuristic on suggestion action
            assessment.suggestedAction = allHostsReplacable ? "Retire all hosts" : "nothing";
            // TODO do some heuristic on impact
            assessment.impact = "na";

            return assessment;
        }).collect(Collectors.toList());

        var hostAssessments = prParentHost.entrySet().stream().map((entry) -> {
            HostAssessment hostAssessment = new HostAssessment();
            hostAssessment.hostName = entry.getKey();
            hostAssessment.numberOfChildren = entry.getValue().size();

            //TODO: Some better heuristic for what's considered problematic
            hostAssessment.numberOfProblematicChildren = (int) entry.getValue().stream()
                    .mapToInt(node -> prCluster.get(clusterKey(node)).size())
                    .filter(i -> i > 1)
                    .count();

            return hostAssessment;
        }).collect(Collectors.toList());

        return new Assessment(clusterAssessments, hostAssessments);
    }

    private static String clusterKey(NodeRepositoryNode node) {
        if (node.getOwner() != null && node.getMembership() != null) {
            String appId = String.format("%s:%s:%s", node.getOwner().tenant, node.getOwner().application, node.getOwner().instance);
            String cluster = String.format("%s:%s", node.getMembership().clustertype, node.getMembership().clusterid);
            return appId + ":" + cluster;
        }
        return "";
    }

    private static long[] clusterStats(String key, List<NodeRepositoryNode> containerNodes) {
        List<NodeRepositoryNode> clusterNodes = containerNodes.stream().filter(nodeRepositoryNode -> clusterKey(nodeRepositoryNode).equals(key)).collect(Collectors.toList());
        long groups = clusterNodes.stream().map(nodeRepositoryNode -> nodeRepositoryNode.getMembership() != null ? nodeRepositoryNode.getMembership().group : "").distinct().count();
        return new long[] { clusterNodes.size(), groups};
    }

    public static class Assessment {
        List<ClusterAssessment> clusterAssessments;
        List<HostAssessment> hostAssessments;

        Assessment(List<ClusterAssessment> clusterAssessments, List<HostAssessment> hostAssessments) {
            this.clusterAssessments = clusterAssessments;
            this.hostAssessments = hostAssessments;
        }

        public List<ClusterAssessment> getClusterAssessments() {
            return clusterAssessments;
        }

        public List<HostAssessment> getHostAssessments() {
            return hostAssessments;
        }
    }

    public static class ClusterAssessment {
        public String app;
        public String zone;
        public String cluster;
        public long clusterImpact;
        public long clusterSize;
        public long groupsImpact;
        public long groupsTotal;
        public String upgradePolicy;
        public String suggestedAction;
        public String impact;
    }

    public static class HostAssessment {
        public String hostName;
        public int numberOfChildren;
        public int numberOfProblematicChildren;
    }

}
