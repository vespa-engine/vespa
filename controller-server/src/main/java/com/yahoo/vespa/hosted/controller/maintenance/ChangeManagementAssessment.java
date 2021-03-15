package com.yahoo.vespa.hosted.controller.maintenance;


import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.NodeRepository;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeRepositoryNode;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeState;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ChangeManagementAssessment {

    private final NodeRepository nodeRepository;

    public static class Assessment {
        String app;
        String zone;
        String cluster;
        long clusterImpact;
        long clusterTotal;
        long groupsImpact;
        long groupsTotal;
        String upgradePolicy;
        String suggestedAction;
        String impact;
    }

    public ChangeManagementAssessment(Controller controller) {
        this.nodeRepository = controller.serviceRegistry().configServer().nodeRepository();
    }

    public List<Assessment> assessment(List<String> impactedHostnames, ZoneId zone) {
        return assessmentInner(impactedHostnames, nodeRepository.listNodes(zone).nodes(), zone);
    }

    static List<Assessment> assessmentInner(List<String> impactedHostnames, List<NodeRepositoryNode> allNodes, ZoneId zone) {
        List<NodeRepositoryNode> containerNodes = allNodes.stream()
                .filter(nodeRepositoryNode -> nodeRepositoryNode.getState() == NodeState.active) //TODO look at more states?
                .filter(node -> impactedHostnames.contains(node.getParentHostname() == null ? "" : node.getParentHostname())).collect(Collectors.toList());

        // Group nodes pr cluster
        Map<String, List<NodeRepositoryNode>> prCluster = containerNodes.stream().collect(Collectors.groupingBy(ChangeManagementAssessment::clusterKey));

        // Report assessment pr cluster
        return prCluster.entrySet().stream().map((entry) -> {
            String key = entry.getKey();
            List<NodeRepositoryNode> nodes = entry.getValue();
            String app = Arrays.stream(key.split(":")).limit(3).collect(Collectors.joining(":"));
            String cluster = Arrays.stream(key.split(":")).skip(3).collect(Collectors.joining(":"));

            long[] totalStats = clusterStats(key, containerNodes);
            long[] impactedStats = clusterStats(key, nodes);

            Assessment assessment = new Assessment();
            assessment.app = app;
            assessment.zone = zone.value();
            assessment.cluster = cluster;
            assessment.clusterTotal = totalStats[0];
            assessment.clusterImpact = impactedStats[0];
            assessment.groupsTotal = totalStats[1];
            assessment.groupsImpact = impactedStats[1];
            assessment.upgradePolicy = "na"; //TODO
            assessment.suggestedAction = "nothing"; //TODO
            assessment.impact = "high"; //TODO

            return assessment;
        }).collect(Collectors.toList());
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

}
