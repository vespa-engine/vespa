// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provisioning.FlavorsConfig;
import com.yahoo.vespa.hosted.provision.testutils.FlavorConfigBuilder;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

/**
 * @author freva
 */
@SuppressWarnings("unchecked")
public class FlavorClustersTest {

    @Test
    public void testSingletonClusters() {
        NodeFlavors nodeFlavors = makeFlavors(Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        FlavorClusters clusters = new FlavorClusters(nodeFlavors.getFlavors());
        Set<Set<Flavor>> expectedClusters = createExpectedClusters(nodeFlavors,
                Collections.singletonList(0), Collections.singletonList(1), Collections.singletonList(2));
        assertEquals(expectedClusters, clusters.flavorClusters);
    }

    @Test
    public void testSingleClusterWithMultipleNodes() {
        // 0 -> 1 -> 2
        NodeFlavors nodeFlavors = makeFlavors(Collections.singletonList(1), Collections.singletonList(2), Collections.emptyList());
        FlavorClusters clusters = new FlavorClusters(nodeFlavors.getFlavors());
        Set<Set<Flavor>> expectedClusters = createExpectedClusters(nodeFlavors, Arrays.asList(0, 1, 2));
        assertEquals(expectedClusters, clusters.flavorClusters);
    }

    @Test
    public void testMultipleClustersWithMultipleNodes() {
        /* Creates flavors where 'replaces' graph that looks like this:
         *    5
         *    |
         *    |
         *    3      4                    8
         *     \   /                      |
         *      \ /                       |
         *       1           6            7
         *      / \
         *     /   \
         *    0     2
         */
        NodeFlavors nodeFlavors = makeFlavors(
                Collections.singletonList(1),   // 0 -> {1}
                Arrays.asList(3, 4),        // 1 -> {3, 4}
                Collections.singletonList(1),   // 2 -> {1}
                Collections.singletonList(5),   // 3 -> {5}
                Collections.emptyList(),        // 4 -> {}
                Collections.emptyList(),        // 5 -> {}
                Collections.emptyList(),        // 6 -> {}
                Collections.singletonList(8),   // 7 -> {8}
                Collections.emptyList());       // 8 -> {}

        FlavorClusters clusters = new FlavorClusters(nodeFlavors.getFlavors());
        Set<Set<Flavor>> expectedClusters = createExpectedClusters(nodeFlavors,
                Arrays.asList(0, 1, 2, 3, 4, 5),
                Collections.singletonList(6),
                Arrays.asList(7, 8));
        assertEquals(expectedClusters, clusters.flavorClusters);
    }

    private Set<Set<Flavor>> createExpectedClusters(NodeFlavors nodeFlavors, List<Integer>... clusters) {
        return Arrays.stream(clusters).map(cluster ->
                cluster.stream()
                        .map(flavorId -> nodeFlavors.getFlavorOrThrow("flavor-" + flavorId))
                        .collect(Collectors.toSet()))
                .collect(Collectors.toSet());
    }

    public static NodeFlavors makeFlavors(int numFlavors) {
        FlavorConfigBuilder flavorConfigBuilder = new FlavorConfigBuilder();
        for (int i = 0; i < numFlavors; i++) {
            flavorConfigBuilder.addFlavor("flavor-" + i, 1. /* cpu*/, 3. /* mem GB*/, 2. /*disk GB*/, Flavor.Type.BARE_METAL);
        }
        return new NodeFlavors(flavorConfigBuilder.build());
    }

    /**
     * Takes in variable number of List of Integers:
     *  For each list a flavor is created
     *  For each element, n, in list, the new flavor replace n'th flavor
     */
    @SafeVarargs
    public static NodeFlavors makeFlavors(List<Integer>... replaces) {
        FlavorConfigBuilder flavorConfigBuilder = new FlavorConfigBuilder();
        for (int i = 0; i < replaces.length; i++) {
            FlavorsConfig.Flavor.Builder builder = flavorConfigBuilder
                    .addFlavor("flavor-" + i, 1. /* cpu*/, 3. /* mem GB*/, 2. /*disk GB*/, Flavor.Type.BARE_METAL);

            for (Integer replacesId : replaces[i]) {
                flavorConfigBuilder.addReplaces("flavor-" + replacesId, builder);
            }
        }
        return new NodeFlavors(flavorConfigBuilder.build());
    }
}
