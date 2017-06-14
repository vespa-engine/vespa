// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provisioning.FlavorsConfig;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

/**
 * @author freva
 */
public class FlavorSpareCountTest {
    /* Creates flavors where 'replaces' graph that looks like this (largest flavor at the bottom):
     *    5
     *    |
     *    |
     *    3      4               8
     *     \   /  \              |
     *      \ /    \             |
     *       1      6            7
     *      / \
     *     /   \
     *    0     2
     */
    private final List<Flavor> flavors = makeFlavors(
            Collections.singletonList(1),   // 0 -> {1}
            Arrays.asList(3, 4),        // 1 -> {3, 4}
            Collections.singletonList(1),   // 2 -> {1}
            Collections.singletonList(5),   // 3 -> {5}
            Collections.emptyList(),        // 4 -> {}
            Collections.emptyList(),        // 5 -> {}
            Collections.singletonList(4),   // 6 -> {4}
            Collections.singletonList(8),   // 7 -> {8}
            Collections.emptyList());       // 8 -> {}

    private final Map<Flavor, FlavorSpareCount> flavorSpareCountByFlavor =
            FlavorSpareCount.constructFlavorSpareCountGraph(flavors);

    @Test
    public void testFlavorSpareCountGraph() {
        List<List<Integer>> expectedPossibleWantedFlavorsByFlavorId = Arrays.asList(
                Arrays.asList(0, 1, 3, 4, 5),
                Arrays.asList(1, 3, 4, 5),
                Arrays.asList(1, 2, 3, 4, 5),
                Arrays.asList(3, 5),
                Collections.singletonList(4),
                Collections.singletonList(5),
                Arrays.asList(4, 6),
                Arrays.asList(7, 8),
                Collections.singletonList(8));

        List<List<Integer>> expectedImmediateReplaceesByFlavorId = Arrays.asList(
                Collections.emptyList(),
                Arrays.asList(0, 2),
                Collections.emptyList(),
                Collections.singletonList(1),
                Arrays.asList(1, 6),
                Collections.singletonList(3),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.singletonList(7));

        for (int i = 0; i < flavors.size(); i++) {
            Flavor flavor = flavors.get(i);
            FlavorSpareCount flavorSpareCount = flavorSpareCountByFlavor.get(flavor);
            Set<FlavorSpareCount> expectedPossibleWantedFlavors = expectedPossibleWantedFlavorsByFlavorId.get(i)
                    .stream().map(flavors::get).map(flavorSpareCountByFlavor::get).collect(Collectors.toSet());
            Set<FlavorSpareCount> expectedImmediateReplacees = expectedImmediateReplaceesByFlavorId.get(i)
                    .stream().map(flavors::get).map(flavorSpareCountByFlavor::get).collect(Collectors.toSet());

            assertEquals(expectedPossibleWantedFlavors, flavorSpareCount.getPossibleWantedFlavors());
            assertEquals(expectedImmediateReplacees, flavorSpareCount.getImmediateReplacees());
        }
    }

    @Test
    public void testSumOfReadyAmongReplacees() {
        long[] numReadyPerFlavor = {3, 5, 2, 6, 2, 7, 4, 3, 4};
        for (int i = 0; i < numReadyPerFlavor.length; i++) {
            flavorSpareCountByFlavor.get(flavors.get(i))
                    .updateReadyAndActiveCounts(numReadyPerFlavor[i], (long) (100 * Math.random()));
        }

        long[] expectedSumTrees = {3, 10, 2, 16, 16, 23, 4, 3, 7};
        for (int i = 0; i < expectedSumTrees.length; i++) {
            assertEquals(expectedSumTrees[i], flavorSpareCountByFlavor.get(flavors.get(i)).getNumReadyAmongReplacees());
        }
    }

    /**
     * Takes in variable number of List of Integers:
     *  For each list a flavor is created
     *  For each element, n, in list, the new flavor replace n'th flavor
     */
    @SafeVarargs
    static List<Flavor> makeFlavors(List<Integer>... replaces) {
        FlavorConfigBuilder flavorConfigBuilder = new FlavorConfigBuilder();
        for (int i = 0; i < replaces.length; i++) {
            FlavorsConfig.Flavor.Builder builder = flavorConfigBuilder
                    .addFlavor("flavor-" + i, 1. /* cpu*/, 3. /* mem GB*/, 2. /*disk GB*/, Flavor.Type.BARE_METAL);

            for (Integer replacesId : replaces[i]) {
                flavorConfigBuilder.addReplaces("flavor-" + replacesId, builder);
            }
        }
        return new NodeFlavors(flavorConfigBuilder.build())
                .getFlavors().stream()
                .sorted(Comparator.comparing(Flavor::name))
                .collect(Collectors.toList());
    }
}
