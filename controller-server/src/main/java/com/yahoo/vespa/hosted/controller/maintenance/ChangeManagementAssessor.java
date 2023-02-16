// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.NodeFilter;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.NodeRepository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author smorgrav
 */
public class ChangeManagementAssessor {

    private final NodeRepository nodeRepository;

    public ChangeManagementAssessor(NodeRepository nodeRepository) {
        this.nodeRepository = nodeRepository;
    }

    public Assessment assessment(List<String> impactedHostnames, ZoneId zone) {
        return assessmentInner(impactedHostnames, nodeRepository.list(zone, NodeFilter.all()), zone);
    }

    Assessment assessmentInner(List<String> impactedHostnames, List<Node> allNodes, ZoneId zone) {
        List<String> impactedParentHosts = toParentHosts(impactedHostnames, allNodes);
        // Group impacted application nodes by parent host
        Map<Node, List<Node>> prParentHost = allNodes.stream()
                .filter(node -> node.state() == Node.State.active) //TODO look at more states?
                .filter(node -> impactedParentHosts.contains(node.parentHostname().map(HostName::value).orElse("")))
                .collect(Collectors.groupingBy(node ->
                    allNodes.stream()
                            .filter(parent -> parent.hostname().equals(node.parentHostname().get()))
                            .findFirst().orElseThrow()
                ));

        // Group nodes pr cluster
        Map<Cluster, List<Node>> prCluster = prParentHost.values()
                .stream()
                .flatMap(Collection::stream)
                .collect(Collectors.groupingBy(ChangeManagementAssessor::clusterKey));

        var tenantHosts = prParentHost.keySet().stream()
                .filter(node -> node.type() == NodeType.host)
                .map(node -> node.hostname())
                .toList();

        boolean allHostsReplacable = tenantHosts.isEmpty() || nodeRepository.isReplaceable(zone, tenantHosts);

        // Report assessment pr cluster
        var clusterAssessments = prCluster.entrySet().stream().map((entry) -> {
            Cluster cluster = entry.getKey();
            List<Node> nodes = entry.getValue();

            long[] totalStats = clusterStats(cluster, allNodes);
            long[] impactedStats = clusterStats(cluster, nodes);

            ClusterAssessment assessment = new ClusterAssessment();
            assessment.app = cluster.getApp();
            assessment.zone = zone.value();
            assessment.cluster = cluster.getClusterType() + ":" + cluster.getClusterId();
            assessment.clusterSize = totalStats[0];
            assessment.clusterImpact = impactedStats[0];
            assessment.groupsTotal = totalStats[1];
            assessment.groupsImpact = impactedStats[1];


            // TODO check upgrade policy
            assessment.upgradePolicy = "na";
            // TODO do some heuristic on suggestion action
            assessment.suggestedAction = allHostsReplacable ? "Retire all hosts" : "nothing";
            // TODO do some heuristic on impact
            assessment.impact = getImpact(cluster, impactedStats, totalStats);

            return assessment;
        }).toList();

        var hostAssessments = prParentHost.entrySet().stream().map((entry) -> {
            HostAssessment hostAssessment = new HostAssessment();
            hostAssessment.hostName = entry.getKey().hostname().value();
            hostAssessment.switchName = entry.getKey().switchHostname().orElse(null);
            hostAssessment.numberOfChildren = entry.getValue().size();

            //TODO: Some better heuristic for what's considered problematic
            hostAssessment.numberOfProblematicChildren = (int) entry.getValue().stream()
                    .mapToInt(node -> prCluster.get(clusterKey(node)).size())
                    .filter(i -> i > 1)
                    .count();

            return hostAssessment;
        }).toList();

        return new Assessment(clusterAssessments, hostAssessments);
    }

    private List<String> toParentHosts(List<String> impactedHostnames, List<Node> allNodes) {
        return impactedHostnames.stream()
                .flatMap(hostname ->
                    allNodes.stream()
                            .filter(node -> List.of(NodeType.config, NodeType.proxy, NodeType.host).contains(node.type()))
                            .filter(node -> hostname.equals(node.hostname().value()) || hostname.equals(node.parentHostname().map(HostName::value).orElse("")))
                            .map(node -> {
                                if (node.type() == NodeType.host)
                                    return node.hostname().value();
                                return node.parentHostname().get().value();
                            }).findFirst().stream()
                )
                .toList();
    }

    private static Cluster clusterKey(Node node) {
        if (node.owner().isEmpty())
            return Cluster.EMPTY;
        String appId = node.owner().get().serializedForm();
        return new Cluster(node.clusterType(), node.clusterId(), appId, node.type());
    }

    private static long[] clusterStats(Cluster cluster, List<Node> containerNodes) {
        List<Node> clusterNodes = containerNodes.stream().filter(node -> cluster.equals(clusterKey(node))).toList();
        long groups = clusterNodes.stream().map(Node::group).distinct().count();
        return new long[] { clusterNodes.size(), groups};
    }

    private String getImpact(Cluster cluster, long[] impactedStats, long[] totalStats) {
        switch (cluster.getNodeType()) {
            case tenant:
                return getTenantImpact(cluster, impactedStats, totalStats);
            case proxy:
                return getProxyImpact(impactedStats[0], totalStats[0]);
            case config:
                return getConfigServerImpact(impactedStats[0]);
            default:
                return "Unkown impact";
        }
    }

    private String getTenantImpact(Cluster cluster, long[] impactedStats, long[] totalStats) {
        switch (cluster.getClusterType()) {
            case container:
                return getContainerImpact(impactedStats[0], totalStats[0]);
            case content:
            case combined:
                return getContentImpact(totalStats[1] > 1, impactedStats[0], impactedStats[1]);
            default:
                return "Unknown impact";
        }
    }

    private String getProxyImpact(long impactedNodes, long totalNodes) {
        int impact = (int) (100.0 * impactedNodes / totalNodes);
        return impact + "% of routing nodes impacted. Consider reprovisioning if too many";
    }

    private String getConfigServerImpact(long impactedNodes) {
        if (impactedNodes == 1) {
            return "Acceptable impact";
        }
        return "Large impact. Consider reprovisioning one or more config servers";
    }

    private String getContainerImpact(long impactedNodes, long totalNodes) {
        if ((double) impactedNodes / totalNodes  <= 0.1) {
            return "Impact not larger than upgrade policy";
        }
        return "Impact larger than upgrade policy";
    }

    private String getContentImpact(boolean isGrouped, long impactedNodes, long impactedGroups) {
        if ((isGrouped && impactedGroups == 1) || impactedNodes == 1)
            return "Impact not larger than upgrade policy";
        return "Impact larger than upgrade policy";
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
        public String switchName;
        public int numberOfChildren;
        public int numberOfProblematicChildren;
    }

    private static class Cluster {
        private Node.ClusterType clusterType;
        private String clusterId;
        private String app;
        private NodeType nodeType;

        public final static Cluster EMPTY = new Cluster(Node.ClusterType.unknown, "na", "na", NodeType.tenant);

        public Cluster(Node.ClusterType clusterType, String clusterId, String app, NodeType nodeType) {
            this.clusterType = clusterType;
            this.clusterId = clusterId;
            this.app = app;
            this.nodeType = nodeType;
        }

        public Node.ClusterType getClusterType() {
            return clusterType;
        }

        public String getClusterId() {
            return clusterId;
        }

        public String getApp() {
            return app;
        }

        public NodeType getNodeType() {
            return nodeType;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Cluster cluster = (Cluster) o;
            return Objects.equals(clusterType, cluster.clusterType) &&
                    Objects.equals(clusterId, cluster.clusterId) &&
                    Objects.equals(app, cluster.app);
        }

        @Override
        public int hashCode() {
            return Objects.hash(clusterType, clusterId, app);
        }
    }

}
