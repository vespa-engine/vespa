// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch.rpc;

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
class NodeGroupsInfo {

    /**
     * Fan-out budget for the other groups asked per skipped hit. We always ask the other nodes
     * in the hit's own group, since the document most likely moved within that group. In addition
     * we ask whole other groups, since a group is the smallest set guaranteed to hold the document:
     * always one, even if it exceeds the budget, and a second one only if both fit within it.
     * So we never ask more than two groups besides the hit's own.
     */
    static final int SOFT_LIMIT = 10;

    private final int numNodes;
    /** Node ids ("partid") in each group, indexed by group id */
    private final Map<Integer, List<Integer>> keysByGroup;
    /** Group id of each node, indexed by node id ("partid") */
    private final Map<Integer, Integer> groupOf;

    private NodeGroupsInfo(Map<Integer, Integer> groupOf) {
        Map<Integer, List<Integer>> keysByGroup = new HashMap<>();
        groupOf.forEach((key, group) -> keysByGroup.computeIfAbsent(group, g -> new ArrayList<>()).add(key));
        this.numNodes = groupOf.size();
        keysByGroup.replaceAll((group, keys) -> List.copyOf(keys));
        this.keysByGroup = Map.copyOf(keysByGroup);
        this.groupOf = Map.copyOf(groupOf);
    }

    static NodeGroupsInfo create(List<Node> nodes) {
        Map<Integer, Integer> groupOf = new HashMap<>();
        for (var node : nodes) {
            groupOf.put(node.key(), node.group());
        }
        return new NodeGroupsInfo(groupOf);
    }

    /** Creates an instance where all the given nodes are in one group */
    static NodeGroupsInfo singleGroup(Iterable<Integer> keys) {
        Map<Integer, Integer> groupOf = new HashMap<>();
        keys.forEach(key -> groupOf.put(key, 0));
        return new NodeGroupsInfo(groupOf);
    }

    int numNodes() { return numNodes; }

    /** Returns the group id of the given node, or null if the node is unknown */
    Integer groupOf(int key) { return groupOf.get(key); }

    /**
     * Returns a new selection of alternative nodes, to be used for one round of fill retries.
     * Other groups are chosen among the healthy ones if there are any, otherwise among all.
     * The selection uses the calling thread's random generator, so it must stay on that thread.
     *
     * @param isHealthyGroup tells whether the group with the given id is healthy
     */
    FillRetrySelection newFillRetrySelection(IntPredicate isHealthyGroup) {
        return newFillRetrySelection(isHealthyGroup, ThreadLocalRandom.current());
    }

    /** As above, but with the given random generator for choosing other groups */
    FillRetrySelection newFillRetrySelection(IntPredicate isHealthyGroup, Random random) {
        return new FillRetrySelection(isHealthyGroup, random);
    }

    /**
     * Selects alternative nodes for retrying fills: the other nodes in the hit's own group, plus
     * nodes from other groups. The other groups are chosen randomly, once per old group, so all
     * skipped hits from the same group are retried on the same nodes in other groups.
     */
    class FillRetrySelection {

        private final Map<Integer, List<Integer>> chosenByOldGroup = new HashMap<>();
        private final Map<Integer, List<Integer>> chosenByOldNode = new HashMap<>();
        private final IntPredicate isHealthyGroup;
        private final Random random;

        private FillRetrySelection(IntPredicate isHealthyGroup, Random random) {
            this.isHealthyGroup = isHealthyGroup;
            this.random = random;
        }

        List<Integer> alternativesForFill(int partId) {
            Integer group = groupOf.get(partId);
            if (group == null) {
                return List.of();
            }
            return chosenByOldNode.computeIfAbsent(partId, p -> {
                List<Integer> result = new ArrayList<>(chosenByOldGroup.computeIfAbsent(group, g -> selectFromOtherGroups(g, isHealthyGroup, random)));
                for (int key : keysByGroup.get(group)) {
                    if (key != p) {
                        result.add(key);
                    }
                }
                return List.copyOf(result);
            });
        }

    }

    private List<Integer> selectFromOtherGroups(int oldGroup, IntPredicate isHealthyGroup, Random random) {
        List<Integer> result = new ArrayList<>();
        List<Integer> otherGroups = keysByGroup.keySet().stream().filter(g -> g != oldGroup).sorted().toList();
        List<Integer> healthyGroups = otherGroups.stream().filter(isHealthyGroup::test).toList();
        if ( ! healthyGroups.isEmpty()) {
            otherGroups = healthyGroups;
        }
        if (otherGroups.isEmpty()) {
            return List.of();
        }
        int slot = random.nextInt(otherGroups.size());
        int maxGroups = Math.min(2, otherGroups.size());
        for (int gotGroups = 0; gotGroups < maxGroups; gotGroups++) {
            List<Integer> keys = keysByGroup.get(otherGroups.get(slot));
            if (gotGroups > 0 && result.size() + keys.size() > SOFT_LIMIT) {
                break;
            }
            result.addAll(keys);
            slot = (slot + 1) % otherGroups.size();
        }
        return List.copyOf(result);
    }

}
