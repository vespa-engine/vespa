// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.distribution.ConfiguredNode;
import com.yahoo.vdslib.distribution.Distribution;
import com.yahoo.vespa.config.content.StorDistributionConfig;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DistributionDiffCalculatorTest {

    private static DistributionConfigBundle flat(int redundancy, int searchableCopies, int nodes) {
        return DistributionConfigBundle.of(DistributionBuilder.configForFlatCluster(redundancy, searchableCopies, nodes));
    }

    private static DistributionConfigBundle flat(int redundancy, int searchableCopies, Collection<Integer> nodes) {
        return DistributionConfigBundle.of(DistributionBuilder.configForFlatCluster(redundancy, searchableCopies, nodes));
    }

    private static DistributionConfigBundle grouped(int... nodesInGroups) {
        var gb = new DistributionBuilder.GroupBuilder(nodesInGroups);
        return DistributionConfigBundle.of(DistributionBuilder.configForHierarchicCluster(gb));
    }

    private static String diff(DistributionConfigBundle oldCfg, DistributionConfigBundle newCfg) {
        return DistributionDiffCalculator.computeDiff(oldCfg, newCfg).toString();
    }

    @Test
    void no_diff_returns_empty_string() {
        assertEquals("", diff(flat(1, 1, 3), flat(1, 1, 3)));
        assertEquals("", diff(grouped(1, 2, 3), grouped(1, 2, 3)));
    }

    @Test
    void changed_redundancy_or_searchable_copies_is_reflected() {
        assertEquals("redundancy: 1 -> 2",                            diff(flat(1, 1, 3), flat(2, 1, 3)));
        assertEquals("redundancy: 2 -> 1",                            diff(flat(2, 1, 3), flat(1, 1, 3)));
        assertEquals("searchable-copies: 1 -> 2",                     diff(flat(1, 1, 3), flat(1, 2, 3)));
        assertEquals("searchable-copies: 2 -> 1",                     diff(flat(1, 2, 3), flat(1, 1, 3)));
        assertEquals("redundancy: 4 -> 5, searchable-copies: 1 -> 2", diff(flat(4, 1, 7), flat(5, 2, 7)));
    }

    @Test
    void flat_group_topology_renders_changed_node_sets() {
        assertEquals("root group: added {1}",             diff(flat(1, 1, Set.of(0)),    flat(1, 1, Set.of(0, 1))));
        assertEquals("root group: added {2, 3}",          diff(flat(1, 1, Set.of(1)),    flat(1, 1, Set.of(1, 2, 3))));
        assertEquals("root group: removed {3}",           diff(flat(1, 1, Set.of(2, 3)), flat(1, 1, Set.of(2))));
        assertEquals("root group: added {1} removed {0}", diff(flat(1, 1, Set.of(0)),    flat(1, 1, Set.of(1))));
    }

    @Test
    void grouped_topology_renders_diff_sets_across_groups() {
        // The test group builder assigns linearly increasing node indexes, so changing the
        // count of nodes in one group affects the indices of the nodes in subsequent groups.
        // It also starts group name indexes at 1, not 0.
        // group 1 {0} --> group 1 {0}, group 2 {1}
        assertEquals("groups: 1 -> 2, group 2: added {1}",                         diff(grouped(1),       grouped(1, 1)));
        // group 1 {0} --> group 1 {0}, group 2 {1, 2, 3}
        assertEquals("groups: 1 -> 2, group 2: added {1, 2, 3}",                   diff(grouped(1),       grouped(1, 3)));
        assertEquals("groups: 1 -> 3, group 2: added {1}, group 3: added {2}",     diff(grouped(1),       grouped(1, 1, 1)));
        assertEquals("groups: 3 -> 1, group 2: removed {1}, group 3: removed {2}", diff(grouped(1, 1, 1), grouped(1)));
        // Partial overlap
        assertEquals("groups: 2 -> 4, group 1: removed {1}, group 2: added {1} " +
                     "removed {2, 3}, group 3: added {2}, group 4: added {3}",     diff(grouped(2, 2),    grouped(1, 1, 1, 1)));

        // Flat <--> grouped
        assertEquals("groups: 1 -> 2, group 1: added {0}, group 2: added {1}, root group: removed {0, 1}",
                     diff(flat(2, 0, Set.of(0, 1)), grouped(1, 1)));
        assertEquals("groups: 2 -> 1, group 1: removed {0}, group 2: removed {1}, root group: added {0, 1}",
                     diff(grouped(1, 1), flat(2, 0, Set.of(0, 1))));
    }

    private static StorDistributionConfig.Group.Nodes.Builder configuredNode(int nodeIndex) {
        StorDistributionConfig.Group.Nodes.Builder builder = new StorDistributionConfig.Group.Nodes.Builder();
        builder.index(nodeIndex);
        return builder;
    }

    private static StorDistributionConfig.Group.Builder groupBuilder(String name, String index, String partitions, Set<Integer> nodes) {
        var builder = new StorDistributionConfig.Group.Builder();
        builder.name(name);
        builder.index(index);
        builder.partitions(partitions);
        nodes.forEach(n -> builder.nodes(configuredNode(n)));
        return builder;
    }

    private static StorDistributionConfig.Group.Builder groupBuilder(String name, String index, String partitions) {
        return groupBuilder(name, index, partitions, Set.of());
    }

    private static StorDistributionConfig nestedGroupConfig2x2(Set<Integer> g1Nodes, Set<Integer> g2Nodes,
                                                               Set<Integer> g3Nodes, Set<Integer> g4Nodes) {
        var configBuilder = new StorDistributionConfig.Builder();
        configBuilder.redundancy(2);

        // Example group structure for g1={0, 1, 2}, g2={3, 4, 5}, g3={6, 7, 8}, g4={9, 10, 11}
        //  root
        //   |-- group0
        //   |     |-- group0_0
        //   |     |     |-- node 0
        //   |     |     |-- node 1
        //   |     |     `-- node 2
        //   |     `-- group0_1
        //   |           |-- node 3
        //   |           |-- node 4
        //   |           `-- node 5
        //   |-- group1
        //   |     |-- group1_0
        //   |     |     |-- node 6
        //   |     |     |-- node 7
        //   |     |     `-- node 8
        //   |     `-- group1_1
        //   |           |-- node 9
        //   |           |-- node 10
        //   |           `-- node 11
        configBuilder.group(groupBuilder("invalid", "invalid", "*|*"));
        configBuilder.group(groupBuilder("group0", "0", "1|*"));
        configBuilder.group(groupBuilder("group0_0", "0.0", "", g1Nodes));
        configBuilder.group(groupBuilder("group0_1", "0.1", "", g2Nodes));
        configBuilder.group(groupBuilder("group1", "1", "1|*"));
        configBuilder.group(groupBuilder("group1_0", "1.0", "", g3Nodes));
        configBuilder.group(groupBuilder("group1_1", "1.1", "", g4Nodes));

        return new StorDistributionConfig(configBuilder);
    }

    @Test
    void nested_grouped_topology_renders_with_canonical_group_names() {
        var cfg1 = DistributionConfigBundle.of(nestedGroupConfig2x2(Set.of(0, 1, 2), Set.of(3, 4, 5),
                                                                    Set.of(6, 7, 8), Set.of(9, 10, 11)));
        // Add 1 node to 0.1 and remove 1 node from 1.1
        var cfg2 = DistributionConfigBundle.of(nestedGroupConfig2x2(Set.of(0, 1, 2), Set.of(3, 4, 5, 12),
                                                                    Set.of(6, 7, 8), Set.of(9, 11)));

        assertEquals("group 0.1: added {12}, group 1.1: removed {10}", diff(cfg1, cfg2));
        // Inverse
        assertEquals("group 0.1: removed {12}, group 1.1: added {10}", diff(cfg2, cfg1));
    }

}
