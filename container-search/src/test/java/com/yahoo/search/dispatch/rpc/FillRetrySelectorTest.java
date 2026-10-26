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
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FillRetrySelectorTest {

    // group ids above 127 to catch Integer identity comparisons
    private static final int FIRST_GROUP = 1000;
    private static final IntPredicate ALL_SUFFICIENT = group -> true;


    @Test
    void unknownNodeGetsOneOfTheOtherGroups() {
        var selector = groups(2, 3);
        assertEquals(Set.of(0, 1, 2), Set.copyOf(selector.alternativesForGroupOf(99, ALL_SUFFICIENT, fixedSlot(0))));
        assertEquals(Set.of(3, 4, 5), Set.copyOf(selector.alternativesForGroupOf(99, ALL_SUFFICIENT, fixedSlot(1))));
    }

    @Test
    void unknownNodeInSingleGroupGetsAllNodes() {
        var selector = groups(1, 3);
        assertEquals(Set.of(0, 1, 2), Set.copyOf(selector.alternativesForGroupOf(99, ALL_SUFFICIENT)));
    }

    @Test
    void singleGroupUsesAllNodes() {
        var selector = groups(1, 4);
        assertEquals(Set.of(0, 1, 2, 3), Set.copyOf(selector.alternativesForGroupOf(1, ALL_SUFFICIENT)));
    }

    @Test
    void ownNodeIsIncludedOnlyOnce() {
        var alternatives = groups(3, 2).alternativesForGroupOf(0, ALL_SUFFICIENT);
        assertEquals(alternatives.size(), Set.copyOf(alternatives).size());
        assertTrue(alternatives.contains(0));
    }

    @Test
    void twoSmallGroupsUseAllNodes() {
        var selector = groups(2, 5);
        assertEquals(Set.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9), Set.copyOf(selector.alternativesForGroupOf(4, ALL_SUFFICIENT)));
    }

    @Test
    void twoLargeGroupsUseAllNodes() {
        var selector = groups(2, 6);
        assertEquals(Set.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11), Set.copyOf(selector.alternativesForGroupOf(0, ALL_SUFFICIENT)));
    }

    @Test
    void threeGroupsUseOwnAndOneOtherGroup() {
        var selector = groups(3, 5);
        assertEquals(Set.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9), Set.copyOf(selector.alternativesForGroupOf(7, ALL_SUFFICIENT, fixedSlot(0))));
        assertEquals(Set.of(5, 6, 7, 8, 9, 10, 11, 12, 13, 14), Set.copyOf(selector.alternativesForGroupOf(7, ALL_SUFFICIENT, fixedSlot(1))));
    }

    @Test
    void manyGroupsWithSmallGroupsUseOwnAndOneOtherGroup() {
        var selector = groups(5, 2);
        for (int slot = 0; slot < 4; slot++) {
            var alternatives = selector.alternativesForGroupOf(0, ALL_SUFFICIENT, fixedSlot(slot));
            assertEquals(4, alternatives.size());
            assertTrue(alternatives.containsAll(List.of(0, 1)));
            assertEquals(2, groupsUsed(selector, alternatives).size());
        }
    }

    @Test
    void manyGroupsWithLargeGroupsUseOwnAndOneOtherGroup() {
        var selector = groups(4, 6);
        for (int slot = 0; slot < 3; slot++) {
            var alternatives = selector.alternativesForGroupOf(0, ALL_SUFFICIENT, fixedSlot(slot));
            assertEquals(12, alternatives.size());
            assertTrue(alternatives.containsAll(List.of(0, 1, 2, 3, 4, 5)));
            assertEquals(2, groupsUsed(selector, alternatives).size());
        }
    }

    @Test
    void sameOldGroupGivesSameAlternatives() {
        var selector = groups(10, 2);
        Set<List<Integer>> seen = new HashSet<>();
        for (int slot = 0; slot < 9; slot++) {
            var fromNode0 = selector.alternativesForGroupOf(0, ALL_SUFFICIENT, fixedSlot(slot));
            var fromNode1 = selector.alternativesForGroupOf(1, ALL_SUFFICIENT, fixedSlot(slot));
            assertTrue(fromNode0.containsAll(List.of(0, 1)), "own group is included");
            assertEquals(fromNode0, fromNode1, "same old group and random choice gives same alternatives");
            seen.add(fromNode0.stream().filter(k -> k > 1).toList());
        }
        assertEquals(9, seen.size(), "each random choice of start group gives a different selection");
    }

    @Test
    void otherGroupsWithSufficientCoverageArePreferred() {
        var selector = groups(4, 6);
        IntPredicate onlyThirdSufficient = group -> group == FIRST_GROUP + 2;
        for (int slot = 0; slot < 3; slot++) {
            var alternatives = selector.alternativesForGroupOf(0, onlyThirdSufficient, fixedSlot(slot));
            assertEquals(Set.of(0, 1, 2, 3, 4, 5, 12, 13, 14, 15, 16, 17), Set.copyOf(alternatives));
        }
    }

    @Test
    void allGroupsAreCandidatesIfNoOtherGroupHasSufficientCoverage() {
        var selector = groups(4, 6);
        IntPredicate onlyOwnSufficient = group -> group == FIRST_GROUP;
        Set<Integer> otherGroupsSeen = new HashSet<>();
        for (int slot = 0; slot < 3; slot++) {
            var alternatives = selector.alternativesForGroupOf(0, onlyOwnSufficient, fixedSlot(slot));
            assertEquals(12, alternatives.size());
            var otherGroups = groupsUsed(selector, alternatives);
            otherGroups.remove(FIRST_GROUP);
            otherGroupsSeen.addAll(otherGroups);
        }
        assertEquals(Set.of(FIRST_GROUP + 1, FIRST_GROUP + 2, FIRST_GROUP + 3), otherGroupsSeen);
    }

    private static final int[][] SHAPES = { {1, 4}, {1, 20}, {2, 5}, {2, 6}, {2, 100}, {3, 1}, {3, 3}, {3, 5}, {3, 6},
                                            {4, 1}, {4, 2}, {5, 2}, {4, 5}, {4, 6}, {10, 1}, {10, 2} };

    @Test
    void alwaysAsksOwnGroupAndExactlyOneOtherGroup() {
        for (int[] shape : SHAPES) {
            var selector = groups(shape[0], shape[1]);
            for (int slot = 0; slot < Math.max(1, shape[0] - 1); slot++) {
                var alternatives = selector.alternativesForGroupOf(0, ALL_SUFFICIENT, fixedSlot(slot));
                String name = shape[0] + " groups of " + shape[1] + " nodes";
                for (int key = 0; key < shape[1]; key++) {
                    assertTrue(alternatives.contains(key), name + " asks own group node " + key);
                }
                var otherGroups = groupsUsed(selector, alternatives);
                otherGroups.remove(FIRST_GROUP);
                assertEquals(Math.min(1, shape[0] - 1), otherGroups.size(), name + " asks " + otherGroups.size() + " other groups");
            }
        }
    }

    private static Set<Integer> groupsUsed(FillRetrySelector selector, List<Integer> keys) {
        Set<Integer> groups = new HashSet<>();
        keys.forEach(key -> groups.add(selector.groupOf(key)));
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
    private static FillRetrySelector groups(int numGroups, int nodesPerGroup) {
        int[] sizes = new int[numGroups];
        Arrays.fill(sizes, nodesPerGroup);
        return groupsOfSizes(sizes);
    }

    /** Creates one group of each given size; node keys are consecutive within each group */
    private static FillRetrySelector groupsOfSizes(int... sizes) {
        var builder = new DispatchNodesConfig.Builder();
        int key = 0;
        for (int g = 0; g < sizes.length; g++) {
            for (int n = 0; n < sizes[g]; n++, key++) {
                builder.node(new DispatchNodesConfig.Node.Builder().key(key).host("host" + key).port(19100 + key).group(FIRST_GROUP + g));
            }
        }
        return FillRetrySelector.create(builder.build().node());
    }

}
