// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch.searchcluster;

import com.yahoo.vespa.config.search.DispatchConfig;
import com.yahoo.vespa.config.search.DispatchNodesConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author ollivir
 */
public class MockSearchCluster extends SearchCluster {

    public MockSearchCluster(String clusterId, int groups, int nodesPerGroup) {
        this(clusterId, groups, nodesPerGroup, null);
    }

    public MockSearchCluster(String clusterId, int groups, int nodesPerGroup, PingFactory pingFactory) {
        super(clusterId, buildGroupListForTest(groups, nodesPerGroup, 88.0), null, pingFactory);
    }

    @Override
    public int groupsWithSufficientCoverage() {
        return groupList().size();
    }

    @Override
    public void working(Node node) {
        node.setWorking(true);
    }

    @Override
    public void failed(Node node) {
        node.setWorking(false);
    }

    public static DispatchConfig createDispatchConfig() {
        return createDispatchConfig(100.0);
    }

    public static DispatchConfig createDispatchConfig(double minSearchCoverage) {
        return createDispatchConfigBuilder(minSearchCoverage).build();
    }

    public static DispatchConfig.Builder createDispatchConfigBuilder(double minSearchCoverage) {
        DispatchConfig.Builder builder = new DispatchConfig.Builder();
        builder.minActivedocsPercentage(88.0);
        builder.minSearchCoverage(minSearchCoverage);
        builder.distributionPolicy(DispatchConfig.DistributionPolicy.Enum.ROUNDROBIN);
        if (minSearchCoverage < 100.0) {
            builder.minWaitAfterCoverageFactor(0);
            builder.maxWaitAfterCoverageFactor(0.5);
        }
        return builder;
    }

    public static DispatchNodesConfig createNodesConfig(int numGroups, int nodesPerGroup) {
        var builder = new DispatchNodesConfig.Builder();
        int key = 0;
        for (int g = 0; g < numGroups; g++) {
            for (int i = 0; i < nodesPerGroup; i++) {
                var nodeBuilder = new DispatchNodesConfig.Node.Builder();
                nodeBuilder.key(key++).port(0).group(g).host("host" + g + "." + i);
                builder.node.add(nodeBuilder);
            }
        }
        return builder.build();
    }

    public static SearchGroupsImpl buildGroupListForTest(int numGroups, int nodesPerGroup, double minActivedocsPercentage) {
        return new SearchGroupsImpl(buildGroupMapForTest(numGroups, nodesPerGroup), minActivedocsPercentage);
    }
    private static Map<Integer, Group> buildGroupMapForTest(int numGroups, int nodesPerGroup) {
        Map<Integer, Group> groups = new HashMap<>();
        int distributionKey = 0;
        for (int group = 0; group < numGroups; group++) {
            List<Node> groupNodes = new ArrayList<>();
            for (int i = 0; i < nodesPerGroup; i++) {
                Node node = new Node(distributionKey, "host" + distributionKey, group);
                node.setWorking(true);
                groupNodes.add(node);
                distributionKey++;
            }
            Group g = new Group(group, groupNodes);
            groups.put(group, g);
        }
        return Map.copyOf(groups);
    }

}
