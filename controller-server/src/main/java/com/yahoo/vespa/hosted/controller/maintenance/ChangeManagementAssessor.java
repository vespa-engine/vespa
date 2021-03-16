package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.NodeRepository;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeRepositoryNode;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeState;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ChangeManagementAssessor {

    private final NodeRepository nodeRepository;

    public static class Assessment {
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

    public ChangeManagementAssessor(Controller controller) {
        this.nodeRepository = controller.serviceRegistry().configServer().nodeRepository();
    }

    public List<Assessment> assessment(List<String> impactedHostnames, ZoneId zone) {
        return assessmentInner(impactedHostnames, nodeRepository.listNodes(zone).nodes(), zone);
    }

    static List<Assessment> assessmentInner(List<String> impactedHostnames, List<NodeRepositoryNode> allNodes, ZoneId zone) {
        // Get all active nodes running on the impacted hosts
        List<NodeRepositoryNode> containerNodes = allNodes.stream()
                .filter(nodeRepositoryNode -> nodeRepositoryNode.getState() == NodeState.active) //TODO look at more states?
                .filter(node -> impactedHostnames.contains(node.getParentHostname() == null ? "" : node.getParentHostname())).collect(Collectors.toList());

        // Group nodes pr cluster
        Map<String, List<NodeRepositoryNode>> prCluster = containerNodes.stream().collect(Collectors.groupingBy(ChangeManagementAssessor::clusterKey));

        // Report assessment pr cluster
        return prCluster.entrySet().stream().map((entry) -> {
            String key = entry.getKey();
            List<NodeRepositoryNode> nodes = entry.getValue();
            String app = Arrays.stream(key.split(":")).limit(3).collect(Collectors.joining(":"));
            String cluster = Arrays.stream(key.split(":")).skip(3).collect(Collectors.joining(":"));

            long[] totalStats = clusterStats(key, allNodes);
            long[] impactedStats = clusterStats(key, nodes);

            Assessment assessment = new Assessment();
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
            assessment.suggestedAction = "nothing";
            // TODO do some heuristic on impact
            assessment.impact = "na";

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
