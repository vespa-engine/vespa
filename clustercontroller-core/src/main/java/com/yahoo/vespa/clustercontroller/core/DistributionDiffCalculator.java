// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static com.yahoo.vespa.clustercontroller.core.DistributionDiff.NodeDiff;
import static com.yahoo.vespa.clustercontroller.core.DistributionDiff.IntegerDiff;

/**
 * Utility for computing a "minimal" diff between two arbitrary distribution configs.
 */
public class DistributionDiffCalculator {

    private record GroupNodes(Set<Integer> nodes) {

        NodeDiff sortedDiff(GroupNodes other) {
            var added   = other.nodes.stream().filter(n -> !nodes.contains(n)).sorted().toList();
            var removed = nodes.stream().filter(n -> !other.nodes.contains(n)).sorted().toList();
            return new NodeDiff(added, removed);
        }

    }

    public static DistributionDiff computeDiff(DistributionConfigBundle oldCfg, DistributionConfigBundle newCfg) {
        return new DistributionDiff(
                computeGroupDiff(oldCfg, newCfg),
                IntegerDiff.of(oldCfg.totalLeafGroupCount(), newCfg.totalLeafGroupCount()),
                IntegerDiff.of(oldCfg.redundancy(), newCfg.redundancy()),
                IntegerDiff.of(oldCfg.searchableCopies(), newCfg.searchableCopies()));
    }

    // Returns an ordered mapping from canonical group name to the number of added/removed nodes for
    // that group. "Canonical" here means a name that uniquely identifies a group regardless of its
    // placement in the topology, i.e. the name is a function of the group's path from the root.
    // Adding a new group is represented as if all its nodes were added in one go.
    // Conversely, removing an entire group is represented as if all its nodes were removed.
    private static TreeMap<String, NodeDiff> computeGroupDiff(DistributionConfigBundle oldCfg, DistributionConfigBundle newCfg) {
        var oldGroupNodes = enumerateGroupNodes(oldCfg);
        var newGroupNodes = enumerateGroupNodes(newCfg);

        var unionLeafGroupNames = new HashSet<>(oldGroupNodes.keySet());
        unionLeafGroupNames.addAll(newGroupNodes.keySet());

        var emptySentinel = new GroupNodes(Set.of());
        var diff = new TreeMap<String, NodeDiff>();
        for (String leafName : unionLeafGroupNames) {
            var oldG = oldGroupNodes.getOrDefault(leafName, emptySentinel);
            var newG = newGroupNodes.getOrDefault(leafName, emptySentinel);
            diff.put(leafName, oldG.sortedDiff(newG));
        }
        return diff;
    }

    // Returns an ordered mapping from canonical group name to the set of distinct node distribution
    // keys contained within that group. Note: we assume that no config is ever received that contains
    // the same node in different groups; such a config is inherently invalid.
    private static TreeMap<String, GroupNodes> enumerateGroupNodes(DistributionConfigBundle cfg) {
        var groups = new TreeMap<String, GroupNodes>();
        // Config is already in flattened form for groups, so use it directly.
        for (var g : cfg.config().group()) {
            // Use group "index" as name, as it encodes the group hierarchy as a dotted string path.
            // "invalid" is the fixed index string value of the root group. If the root group contains
            // any nodes at all, there will not be any nested groups by definition, as only leaf groups
            // may contain nodes.
            String name = "invalid".equals(g.index()) ? "root group" : "group %s".formatted(g.index());
            groups.put(name, new GroupNodes(g.nodes().stream().map(n -> n.index()).collect(Collectors.toSet())));
        }
        return groups;
    }

}
