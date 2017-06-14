// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.Flavor;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author freva
 */
public class FlavorSpareCheckerTest {
    /* Creates flavors where 'replaces' graph that looks like this (largest flavor at the bottom):
     *   5
     *   |
     *   |
     *   3      4             8
     *    \   /  \            |
     *     \ /    \           |
     *      1      6          7
     *     / \
     *    /   \
     *   0     2
     */
    private static final List<Flavor> flavors = FlavorSpareCountTest.makeFlavors(
            Collections.singletonList(1),   // 0 -> {1}
            Arrays.asList(3, 4),        // 1 -> {3, 4}
            Collections.singletonList(1),   // 2 -> {1}
            Collections.singletonList(5),   // 3 -> {5}
            Collections.emptyList(),        // 4 -> {}
            Collections.emptyList(),        // 5 -> {}
            Collections.singletonList(4),   // 6 -> {4}
            Collections.singletonList(8),   // 7 -> {8}
            Collections.emptyList());        // 8 -> {}

    private final Map<Flavor, FlavorSpareCount> flavorSpareCountByFlavor = flavors.stream()
            .collect(Collectors.toMap(
                    i -> i,
                    i -> mock(FlavorSpareCount.class)));

    private final FlavorSpareChecker.SpareNodesPolicy spareNodesPolicy = mock(FlavorSpareChecker.SpareNodesPolicy.class);
    private FlavorSpareChecker flavorSpareChecker = new FlavorSpareChecker(spareNodesPolicy, flavorSpareCountByFlavor);


    @Test
    public void canRetireUnallocated_Successfully() {
        Flavor flavorToRetire = flavors.get(0);
        FlavorSpareCount flavorSpareCount = flavorSpareCountByFlavor.get(flavorToRetire);
        when(flavorSpareCount.hasReady()).thenReturn(true);
        when(spareNodesPolicy.hasSpare(flavorSpareCount)).thenReturn(true);

        assertTrue(flavorSpareChecker.canRetireUnallocatedNodeWithFlavor(flavorToRetire));
        verifyDecrement(0);
    }

    @Test
    public void canRetireUnallocated_NoReadyForFlavor() {
        Flavor flavorToRetire = flavors.get(0);
        FlavorSpareCount flavorSpareCount = flavorSpareCountByFlavor.get(flavorToRetire);
        when(spareNodesPolicy.hasSpare(flavorSpareCount)).thenReturn(true);

        assertFalse(flavorSpareChecker.canRetireUnallocatedNodeWithFlavor(flavorToRetire));
        verifyDecrement();
    }

    @Test
    public void canRetireUnallocated_NoSpareForFlavor() {
        Flavor flavorToRetire = flavors.get(0);
        FlavorSpareCount flavorSpareCount = flavorSpareCountByFlavor.get(flavorToRetire);
        when(flavorSpareCount.hasReady()).thenReturn(true);

        assertFalse(flavorSpareChecker.canRetireUnallocatedNodeWithFlavor(flavorToRetire));
        verifyDecrement();
    }

    @Test
    public void canRetireAllocated_LeafFlavor_Successfully() {
        Flavor flavorToRetire = flavors.get(0);

        // If we want to retire flavor 0, then we must have enough spares & ready of flavor 0 and all
        // other flavor that it replaces transitively
        Stream.of(0, 1, 3, 4, 5)
                .map(flavors::get)
                .map(flavorSpareCountByFlavor::get)
                .forEach(flavorSpareCount -> {
                    when(flavorSpareCount.hasReady()).thenReturn(true);
                    when(spareNodesPolicy.hasSpare(flavorSpareCount)).thenReturn(true);
                });

        assertTrue(flavorSpareChecker.canRetireAllocatedNodeWithFlavor(flavorToRetire));
        verifyDecrement(0, 1, 3, 4, 5);
    }

    @Test
    public void canRetireAllocated_LeafFlavor_NoSparesForPossibleWantedFlavor() {
        Flavor flavorToRetire = flavors.get(0);

        // Flavor 4 is transitively replaced by flavor 0, even though we have enough spares of flavor 0,
        // we cannot retire it if there are not enough spares of flavor 4
        Stream.of(0, 1, 3, 5)
                .map(flavors::get)
                .map(flavorSpareCountByFlavor::get)
                .forEach(flavorSpareCount -> {
                    when(flavorSpareCount.hasReady()).thenReturn(true);
                    when(spareNodesPolicy.hasSpare(flavorSpareCount)).thenReturn(true);
                });

        assertFalse(flavorSpareChecker.canRetireAllocatedNodeWithFlavor(flavorToRetire));
        verifyDecrement();
    }

    @Test
    public void canRetireAllocated_CenterNode_Successfully() {
        Flavor flavorToRetire = flavors.get(1);

        Stream.of(1, 3, 4, 5)
                .map(flavors::get)
                .map(flavorSpareCountByFlavor::get)
                .forEach(flavorSpareCount -> {
                    when(flavorSpareCount.hasReady()).thenReturn(true);
                    when(spareNodesPolicy.hasSpare(flavorSpareCount)).thenReturn(true);
                });

        assertTrue(flavorSpareChecker.canRetireAllocatedNodeWithFlavor(flavorToRetire));
        verifyDecrement(1, 3, 4, 5);
    }

    @Test
    public void canRetireAllocated_CenterNode_NoNodeRepoFlavorNodes_Successfully() {
        Flavor flavorToRetire = flavors.get(1);

        // If we want to retire a node with node-repo flavor 1, but there are no ready nodes of flavor-1,
        // we must ensure there are spare nodes of flavors that replace flavor 1
        Stream.of(0, 1, 2, 3, 4, 5)
                .map(flavors::get)
                .map(flavorSpareCountByFlavor::get)
                .forEach(flavorSpareCount -> {
                    when(flavorSpareCount.hasReady()).thenReturn(true);
                    when(spareNodesPolicy.hasSpare(flavorSpareCount)).thenReturn(true);
                });
        when(flavorSpareCountByFlavor.get(flavorToRetire).hasReady()).thenReturn(false);
        when(flavorSpareCountByFlavor.get(flavors.get(0)).getNumReadyAmongReplacees()).thenReturn(1L);
        when(flavorSpareCountByFlavor.get(flavors.get(2)).getNumReadyAmongReplacees()).thenReturn(1L);

        assertTrue(flavorSpareChecker.canRetireAllocatedNodeWithFlavor(flavorToRetire));
        verifyDecrement(0, 2, 3, 4, 5);
    }

    @Test
    public void canRetireAllocated_CenterNode_NoNodeRepoFlavorNodes_NoImmediateSpare() {
        Flavor flavorToRetire = flavors.get(1);

        // Same as above, but now one of the flavors that could replace flavor 1 (flavor 2) does not have enough spares
        Stream.of(0, 1, 3, 4, 5)
                .map(flavors::get)
                .map(flavorSpareCountByFlavor::get)
                .forEach(flavorSpareCount -> {
                    when(flavorSpareCount.hasReady()).thenReturn(true);
                    when(spareNodesPolicy.hasSpare(flavorSpareCount)).thenReturn(true);
                });
        when(flavorSpareCountByFlavor.get(flavorToRetire).hasReady()).thenReturn(false);
        when(flavorSpareCountByFlavor.get(flavors.get(0)).getNumReadyAmongReplacees()).thenReturn(1L);
        when(flavorSpareCountByFlavor.get(flavors.get(2)).getNumReadyAmongReplacees()).thenReturn(1L);

        assertFalse(flavorSpareChecker.canRetireAllocatedNodeWithFlavor(flavorToRetire));
        verifyDecrement();
    }

    @Test
    public void canRetireAllocated_CenterNode_NoNodeRepoFlavorNodes_SkipEmptyImmediate() {
        Flavor flavorToRetire = flavors.get(1);

        // Flavor 2 still has no spares, but also the sum of ready nodes in its replaces tree is 0, so we should
        // be able to continue
        Stream.of(0, 1, 3, 4, 5)
                .map(flavors::get)
                .map(flavorSpareCountByFlavor::get)
                .forEach(flavorSpareCount -> {
                    when(flavorSpareCount.hasReady()).thenReturn(true);
                    when(spareNodesPolicy.hasSpare(flavorSpareCount)).thenReturn(true);
                });
        when(flavorSpareCountByFlavor.get(flavorToRetire).hasReady()).thenReturn(false);
        when(flavorSpareCountByFlavor.get(flavors.get(0)).getNumReadyAmongReplacees()).thenReturn(1L);
        when(flavorSpareCountByFlavor.get(flavors.get(2)).getNumReadyAmongReplacees()).thenReturn(0L);

        assertTrue(flavorSpareChecker.canRetireAllocatedNodeWithFlavor(flavorToRetire));
        verifyDecrement(0, 3, 4, 5);
    }

    private void verifyDecrement(int... decrementFlavorIds) {
        Set<Flavor> decrementedFlavors = Arrays.stream(decrementFlavorIds).boxed().map(flavors::get).collect(Collectors.toSet());
        for (Flavor flavor : flavors) {
            int times = decrementedFlavors.contains(flavor) ? 1 : 0;
            verify(flavorSpareCountByFlavor.get(flavor), times(times)).decrementNumberOfReady();
        }
    }

    @Before
    public void setup() {
        Map<Flavor, FlavorSpareCount> flavorSpareCountGraph = FlavorSpareCount.constructFlavorSpareCountGraph(flavors);
        flavorSpareCountByFlavor.forEach((flavor, flavorSpareCount) -> {
            Set<FlavorSpareCount> possibleWantedFlavors = flavorSpareCountGraph.get(flavor).getPossibleWantedFlavors()
                    .stream().map(FlavorSpareCount::getFlavor).map(flavorSpareCountByFlavor::get).collect(Collectors.toSet());
            Set<FlavorSpareCount> immediateReplacees = flavorSpareCountGraph.get(flavor).getImmediateReplacees()
                    .stream().map(FlavorSpareCount::getFlavor).map(flavorSpareCountByFlavor::get).collect(Collectors.toSet());

            doNothing().when(flavorSpareCount).decrementNumberOfReady();
            when(flavorSpareCount.hasReady()).thenReturn(false);
            when(flavorSpareCount.getPossibleWantedFlavors()).thenReturn(possibleWantedFlavors);
            when(flavorSpareCount.getImmediateReplacees()).thenReturn(immediateReplacees);
            when(spareNodesPolicy.hasSpare(flavorSpareCount)).thenReturn(false);
        });
    }
}
