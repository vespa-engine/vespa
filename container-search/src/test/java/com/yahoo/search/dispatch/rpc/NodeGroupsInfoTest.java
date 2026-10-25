// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch.rpc;

import com.yahoo.vespa.config.search.DispatchNodesConfig;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NodeGroupsInfoTest {

    // group ids above 127 to catch Integer identity comparisons
    private static final int FIRST_GROUP = 1000;
    private static final IntPredicate ALL_HEALTHY = group -> true;

    @Test
    void unknownNodeHasNoAlternatives() {
        var info = groups(2, 3);
        assertEquals(List.of(), info.newFillRetrySelection(ALL_HEALTHY).alternativesForFill(99));
    }

    @Test
    void singleGroupUsesAllOtherNodes() {
        var info = groups(1, 4);
        assertEquals(Set.of(0, 2, 3), Set.copyOf(info.newFillRetrySelection(ALL_HEALTHY).alternativesForFill(1)));
    }

    @Test
    void twoSmallGroupsUseAllOtherNodes() {
        var info = groups(2, 5);
        assertEquals(Set.of(0, 1, 2, 3, 5, 6, 7, 8, 9), Set.copyOf(info.newFillRetrySelection(ALL_HEALTHY).alternativesForFill(4)));
    }

    @Test
    void twoLargeGroupsUseAllOtherNodes() {
        var info = groups(2, 6);
        assertEquals(Set.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11), Set.copyOf(info.newFillRetrySelection(ALL_HEALTHY).alternativesForFill(0)));
    }

    @Test
    void threeSmallGroupsUseAllOtherNodes() {
        var info = groups(3, 5);
        assertEquals(Set.of(0, 1, 2, 3, 4, 5, 6, 8, 9, 10, 11, 12, 13, 14), Set.copyOf(info.newFillRetrySelection(ALL_HEALTHY).alternativesForFill(7)));
    }

    @Test
    void manyGroupsWithSmallGroupsUseOwnAndTwoOtherGroups() {
        var info = groups(5, 2);
        for (int slot = 0; slot < 4; slot++) {
            var alternatives = info.newFillRetrySelection(ALL_HEALTHY, fixedSlot(slot)).alternativesForFill(0);
            assertEquals(5, alternatives.size());
            assertTrue(alternatives.contains(1));
            assertEquals(3, groupsUsed(info, alternatives).size());
        }
    }

    @Test
    void manyGroupsWithLargeGroupsUseOwnAndOneOtherGroup() {
        var info = groups(4, 6);
        for (int slot = 0; slot < 3; slot++) {
            var alternatives = info.newFillRetrySelection(ALL_HEALTHY, fixedSlot(slot)).alternativesForFill(0);
            assertEquals(11, alternatives.size());
            assertTrue(alternatives.containsAll(List.of(1, 2, 3, 4, 5)));
            assertEquals(2, groupsUsed(info, alternatives).size());
        }
    }

    @Test
    void secondGroupIsOnlyAddedIfBothFitWithinTheLimit() {
        // other groups: 5 + 5 fits within the limit of 10, 6 + 6 does not; plus the rest of the own group
        assertEquals(10 + 4, groups(4, 5).newFillRetrySelection(ALL_HEALTHY).alternativesForFill(0).size());
        assertEquals(6 + 5, groups(4, 6).newFillRetrySelection(ALL_HEALTHY).alternativesForFill(0).size());
    }

    @Test
    void randomChoiceIsMadeOncePerOldGroup() {
        var info = groups(10, 2);
        Set<List<Integer>> seen = new HashSet<>();
        for (int slot = 0; slot < 9; slot++) {
            var selection = info.newFillRetrySelection(ALL_HEALTHY, fixedSlot(slot));
            var fromNode0 = selection.alternativesForFill(0);
            var fromNode1 = selection.alternativesForFill(1);
            assertTrue(fromNode0.contains(1) && fromNode1.contains(0), "own group is included");
            assertFalse(fromNode0.contains(0) || fromNode1.contains(1), "the node itself is not");
            var otherGroupsFromNode0 = fromNode0.stream().filter(k -> k > 1).toList();
            assertEquals(otherGroupsFromNode0, fromNode1.stream().filter(k -> k > 1).toList(),
                         "same old group gives same alternatives in other groups");
            seen.add(otherGroupsFromNode0);
        }
        assertEquals(9, seen.size(), "each random choice of start group gives a different selection");
    }

    @Test
    void healthyOtherGroupsArePreferred() {
        var info = groups(4, 6);
        IntPredicate onlyThirdHealthy = group -> group == FIRST_GROUP + 2;
        for (int slot = 0; slot < 3; slot++) {
            var alternatives = info.newFillRetrySelection(onlyThirdHealthy, fixedSlot(slot)).alternativesForFill(0);
            assertEquals(Set.of(1, 2, 3, 4, 5, 12, 13, 14, 15, 16, 17), Set.copyOf(alternatives));
        }
    }

    @Test
    void unhealthyGroupIsNotAddedAsSecondGroup() {
        var info = groups(5, 2);
        IntPredicate onlyThirdHealthy = group -> group == FIRST_GROUP + 2;
        var alternatives = info.newFillRetrySelection(onlyThirdHealthy, fixedSlot(0)).alternativesForFill(0);
        assertEquals(Set.of(1, 4, 5), Set.copyOf(alternatives));
    }

    @Test
    void allGroupsAreCandidatesIfNoOtherGroupIsHealthy() {
        var info = groups(4, 6);
        IntPredicate onlyOwnHealthy = group -> group == FIRST_GROUP;
        Set<Integer> otherGroupsSeen = new HashSet<>();
        for (int slot = 0; slot < 3; slot++) {
            var alternatives = info.newFillRetrySelection(onlyOwnHealthy, fixedSlot(slot)).alternativesForFill(0);
            assertEquals(11, alternatives.size());
            var otherGroups = groupsUsed(info, alternatives);
            otherGroups.remove(FIRST_GROUP);
            otherGroupsSeen.addAll(otherGroups);
        }
        assertEquals(Set.of(FIRST_GROUP + 1, FIRST_GROUP + 2, FIRST_GROUP + 3), otherGroupsSeen);
    }

    private static final int[][] SHAPES = { {1, 4}, {1, 20}, {2, 5}, {2, 6}, {2, 100}, {3, 1}, {3, 3}, {3, 5}, {3, 6},
                                            {4, 1}, {4, 2}, {5, 2}, {4, 5}, {4, 6}, {10, 1}, {10, 2} };

    @Test
    void alwaysAsksOwnGroupAndNeverMoreThanTwoOtherGroups() {
        for (int[] shape : SHAPES) {
            var info = groups(shape[0], shape[1]);
            for (int slot = 0; slot < Math.max(1, shape[0] - 1); slot++) {
                var alternatives = info.newFillRetrySelection(ALL_HEALTHY, fixedSlot(slot)).alternativesForFill(0);
                String name = shape[0] + " groups of " + shape[1] + " nodes";
                for (int key = 1; key < shape[1]; key++) {
                    assertTrue(alternatives.contains(key), name + " asks own group node " + key);
                }
                var otherGroups = groupsUsed(info, alternatives);
                otherGroups.remove(FIRST_GROUP);
                assertTrue(otherGroups.size() <= 2, name + " asks " + otherGroups.size() + " other groups");
            }
        }
    }

    private static Set<Integer> groupsUsed(NodeGroupsInfo info, List<Integer> keys) {
        Set<Integer> groups = new HashSet<>();
        keys.forEach(key -> groups.add(info.groupOf(key)));
        return groups;
    }

    /** A random generator that always picks the given slot, wrapped to stay within the bound */
    private static Random fixedSlot(int slot) {
        return new Random() {
            @Override
            public int nextInt(int bound) { return slot % bound; }
        };
    }

    /** Creates numGroups groups with nodesPerGroup nodes each; node keys are consecutive within each group */
    private static NodeGroupsInfo groups(int numGroups, int nodesPerGroup) {
        int[] sizes = new int[numGroups];
        Arrays.fill(sizes, nodesPerGroup);
        return groupsOfSizes(sizes);
    }

    /** Creates one group of each given size; node keys are consecutive within each group */
    private static NodeGroupsInfo groupsOfSizes(int... sizes) {
        var builder = new DispatchNodesConfig.Builder();
        int key = 0;
        for (int g = 0; g < sizes.length; g++) {
            for (int n = 0; n < sizes[g]; n++, key++) {
                builder.node(new DispatchNodesConfig.Node.Builder().key(key).host("host" + key).port(19100 + key).group(FIRST_GROUP + g));
            }
        }
        return NodeGroupsInfo.create(builder.build().node());
    }

}
