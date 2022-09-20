// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.yahoo.search.dispatch.searchcluster.Group;
import com.yahoo.search.dispatch.searchcluster.Node;
import com.yahoo.search.dispatch.searchcluster.SearchCluster;
import com.yahoo.vespa.config.search.DispatchConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * @author ollivir
 */
public class MockSearchCluster extends SearchCluster {

    private final int numGroups;
    private final int numNodesPerGroup;
    private final ImmutableList<Group> orderedGroups;
    private final ImmutableMap<Integer, Group> groups;
    private final List<Node> nodes;

    public MockSearchCluster(String clusterId, int groups, int nodesPerGroup) {
        this(clusterId, createDispatchConfig(), groups, nodesPerGroup);
    }

    public MockSearchCluster(String clusterId, DispatchConfig dispatchConfig, int groups, int nodesPerGroup) {
        super(clusterId, dispatchConfig, null, null);

        ImmutableList.Builder<Group> orderedGroupBuilder = ImmutableList.builder();
        ImmutableMap.Builder<Integer, Group> groupBuilder = ImmutableMap.builder();
        ImmutableMultimap.Builder<String, Node> hostBuilder = ImmutableMultimap.builder();
        int distributionKey = 0;
        this.nodes = new ArrayList<>();
        for (int group = 0; group < groups; group++) {
            List<Node> groupNodes = new ArrayList<>();
            for (int i = 0; i < nodesPerGroup; i++) {
                Node node = new Node(distributionKey, "host" + distributionKey, group);
                nodes.add(node);
                groupNodes.add(node);
                hostBuilder.put(node.hostname(), node);
                distributionKey++;
            }
            Group g = new Group(group, groupNodes);
            groupBuilder.put(group, g);
            orderedGroupBuilder.add(g);
        }
        this.orderedGroups = orderedGroupBuilder.build();
        this.groups = groupBuilder.build();
        this.numGroups = groups;
        this.numNodesPerGroup = nodesPerGroup;
    }

    @Override
    public ImmutableList<Group> orderedGroups() {
        return orderedGroups;
    }

    @Override
    public List<Node> nodes() { return nodes; }

    @Override
    public ImmutableMap<Integer, Group> groups() {
        return groups;
    }

    @Override
    public boolean allGroupsHaveSize1() { return numNodesPerGroup == 1;}

    @Override
    public int groupsWithSufficientCoverage() {
        return numGroups;
    }

    @Override
    public Optional<Group> group(int n) {
        if (n < numGroups) {
            return Optional.of(groups.get(n));
        } else {
            return Optional.empty();
        }
    }

    @Override
    public Optional<Node> localCorpusDispatchTarget() {
        return Optional.empty();
    }

    @Override
    public void working(Node node) {
        node.setWorking(true);
    }

    @Override
    public void failed(Node node) {
        node.setWorking(false);
    }

    public static DispatchConfig createDispatchConfig(Node... nodes) {
        return createDispatchConfig(100.0, nodes);
    }
    public static DispatchConfig createDispatchConfig(List<Node> nodes) {
        return createDispatchConfig(100.0, nodes).build();
    }

    public static DispatchConfig createDispatchConfig(double minSearchCoverage, Node... nodes) {
        return createDispatchConfig(minSearchCoverage, Arrays.asList(nodes)).build();
    }

    public static DispatchConfig.Builder createDispatchConfig(double minSearchCoverage, List<Node> nodes) {
        DispatchConfig.Builder builder = new DispatchConfig.Builder();
        builder.minActivedocsPercentage(88.0);
        builder.minSearchCoverage(minSearchCoverage);
        builder.distributionPolicy(DispatchConfig.DistributionPolicy.Enum.ROUNDROBIN);
        if (minSearchCoverage < 100.0) {
            builder.minWaitAfterCoverageFactor(0);
            builder.maxWaitAfterCoverageFactor(0.5);
        }
        int port = 10000;
        for (Node n : nodes) {
            builder.node(new DispatchConfig.Node.Builder().key(n.key()).host(n.hostname()).port(port++).group(n.group()));
        }
        return builder;
    }

}
