// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.distribution.ConfiguredNode;
import com.yahoo.vdslib.distribution.Distribution;
import com.yahoo.vespa.config.content.StorDistributionConfig;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class DistributionBuilder {
    // TODO support nested groups
    static class GroupBuilder {
        final int groupCount;
        List<Integer> groupsWithNodeCount;

        GroupBuilder(int groupCount) {
            this.groupCount = groupCount;
        }

        GroupBuilder(int... nodeCounts) {
            this.groupCount = nodeCounts.length;
            this.groupsWithNodeCount = IntStream.of(nodeCounts).boxed()
                    .toList();
        }

        GroupBuilder eachWithNodeCount(int nodeCount) {
            groupsWithNodeCount = IntStream.range(0, groupCount)
                    .map(i -> nodeCount).boxed()
                    .toList();
            return this;
        }

        int totalNodeCount() {
            return groupsWithNodeCount.stream().reduce(0, Integer::sum);
        }

        String groupDistributionSpec() {
            return IntStream.range(0, groupCount).mapToObj(i -> "1")
                    .collect(Collectors.joining("|")) + "|*";
        }
    }

    static GroupBuilder withGroups(int groups) {
        return new GroupBuilder(groups);
    }

    static GroupBuilder withGroupNodes(int... nodeCounts) {
        return new GroupBuilder(nodeCounts);
    }

    static List<ConfiguredNode> buildConfiguredNodes(int nodeCount) {
        return IntStream.range(0, nodeCount)
                .mapToObj(i -> new ConfiguredNode(i, false))
                .toList();
    }

    private static StorDistributionConfig.Group.Nodes.Builder configuredNode(ConfiguredNode node) {
        StorDistributionConfig.Group.Nodes.Builder builder = new StorDistributionConfig.Group.Nodes.Builder();
        builder.index(node.index());
        return builder;
    }

    private static StorDistributionConfig.Group.Builder configuredGroup(
            String name, int index, Collection<ConfiguredNode> nodes) {
        StorDistributionConfig.Group.Builder builder = new StorDistributionConfig.Group.Builder();
        builder.name(name);
        builder.index(Integer.toString(index));
        nodes.forEach(n -> builder.nodes(configuredNode(n)));
        return builder;
    }

    public static Distribution forFlatCluster(int nodeCount) {
        Collection<ConfiguredNode> nodes = buildConfiguredNodes(nodeCount);

        StorDistributionConfig.Builder configBuilder = new StorDistributionConfig.Builder();
        configBuilder.redundancy(2);
        configBuilder.group(configuredGroup("bar", 0, nodes));

        return new Distribution(new StorDistributionConfig(configBuilder));
    }

    static Distribution forHierarchicCluster(GroupBuilder root) {
        List<ConfiguredNode> nodes = buildConfiguredNodes(root.totalNodeCount());

        StorDistributionConfig.Builder configBuilder = new StorDistributionConfig.Builder();
        configBuilder.redundancy(2);

        StorDistributionConfig.Group.Builder rootBuilder = new StorDistributionConfig.Group.Builder();
        rootBuilder.name("invalid");
        rootBuilder.index("invalid");
        rootBuilder.partitions(root.groupDistributionSpec());
        configBuilder.group(rootBuilder);

        int offset = 0;
        for (int group = 0; group < root.groupsWithNodeCount.size(); ++group) {
            int nodeCount = root.groupsWithNodeCount.get(group);
            StorDistributionConfig.Group.Builder groupBuilder
                    = configuredGroup("group_" + (group + 1), group + 1, nodes.subList(offset, offset + nodeCount));
            configBuilder.group(groupBuilder);
            offset += nodeCount;
        }

        return new Distribution(new StorDistributionConfig(configBuilder));
    }
}
