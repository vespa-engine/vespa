// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch.rpc;

import com.yahoo.collections.ListMap;
import com.yahoo.vespa.config.search.DispatchNodesConfig.Node;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntPredicate;

/**
 * Knows which group each search node belongs to, and uses that to select
 * which nodes to ask when retrying a fill for hits that got an empty docsum.
 */
class FillRetrySelector {

    private final int numNodes;
    /** Node ids in each group, indexed by group id */
    private final ListMap<Integer, Integer> keysByGroup;
    /** Group id of each node, indexed by node id */
    private final Map<Integer, Integer> groupOf;

    private FillRetrySelector(Map<Integer, Integer> groupOf) {
        this.groupOf = Map.copyOf(groupOf);
        this.numNodes = groupOf.size();
        this.keysByGroup = new ListMap<>();
        groupOf.forEach((key, group) -> keysByGroup.put(group, key));
    }

    static FillRetrySelector create(List<Node> nodes) {
        Map<Integer, Integer> groupOf = new HashMap<>();
        for (var node : nodes) {
            groupOf.put(node.key(), node.group());
        }
        return new FillRetrySelector(groupOf);
    }

    /** Creates an instance where all the given nodes are in one group */
    static FillRetrySelector singleGroup(Iterable<Integer> keys) {
        Map<Integer, Integer> groupOf = new HashMap<>();
        keys.forEach(key -> groupOf.put(key, 0));
        return new FillRetrySelector(groupOf);
    }

    int numNodes() { return numNodes; }

    /** Returns the group id of the given node, or null if the node is unknown */
    Integer groupOf(int key) { return groupOf.get(key); }

    /**
     * Selects alternative nodes for retrying fills: the nodes in the hit's own group, plus
     * nodes from 1 other group chosen randomly.
     */
    List<Integer> alternativesForGroupOf(int nodeId, IntPredicate isHealthyGroup) {
        return alternativesForGroupOf(nodeId, isHealthyGroup, ThreadLocalRandom.current());
    }

    /** As above, but with the given random generator for choosing the other group */
    List<Integer> alternativesForGroupOf(int nodeId, IntPredicate isHealthyGroup, Random random) {
        List<Integer> result = new ArrayList<>();
        Integer hadGroup = groupOf(nodeId);
        final int oldGroup = (hadGroup != null) ? hadGroup : -1;
        if (oldGroup != -1) {
            result.addAll(keysByGroup.get(oldGroup));
        }
        List<Integer> otherGroups = keysByGroup.keySet().stream().filter(g -> g != oldGroup).sorted().toList();
        List<Integer> healthyGroups = otherGroups.stream().filter(isHealthyGroup::test).toList();
        if ( ! healthyGroups.isEmpty()) {
            otherGroups = healthyGroups;
        }
        if (otherGroups.isEmpty()) {
            return List.copyOf(result);
        }
        int slot = random.nextInt(otherGroups.size());
        List<Integer> keys = keysByGroup.get(otherGroups.get(slot));
        result.addAll(keys);
        return List.copyOf(result);
    }

}
