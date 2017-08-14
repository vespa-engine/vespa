// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.Flavor;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Keeps track of number of ready and active nodes for a flavor and its replaces neighbors
 *
 * @author freva
 */
public class FlavorSpareCount {

    private final Flavor flavor;
    private Set<FlavorSpareCount> possibleWantedFlavors;
    private Set<FlavorSpareCount> immediateReplacees;
    private long numReady;
    private long numActive;

    public static Map<Flavor, FlavorSpareCount> constructFlavorSpareCountGraph(List<Flavor> flavors) {
        Map<Flavor, FlavorSpareCount> spareCountByFlavor = new HashMap<>();
        Map<Flavor, Set<Flavor>> immediateReplaceeFlavorsByFlavor = new HashMap<>();
        for (Flavor flavor : flavors) {
            for (Flavor replaces : flavor.replaces()) {
                if (! immediateReplaceeFlavorsByFlavor.containsKey(replaces)) {
                    immediateReplaceeFlavorsByFlavor.put(replaces, new HashSet<>());
                }
                immediateReplaceeFlavorsByFlavor.get(replaces).add(flavor);
            }

            spareCountByFlavor.put(flavor, new FlavorSpareCount(flavor));
        }

        spareCountByFlavor.forEach((flavor, flavorSpareCount) -> {
            flavorSpareCount.immediateReplacees = ! immediateReplaceeFlavorsByFlavor.containsKey(flavor) ?
                    Collections.emptySet() :
                    immediateReplaceeFlavorsByFlavor.get(flavor).stream().map(spareCountByFlavor::get).collect(Collectors.toSet());
            flavorSpareCount.possibleWantedFlavors = recursiveReplacements(flavor, new HashSet<>())
                    .stream().map(spareCountByFlavor::get).collect(Collectors.toSet());
        });

        return spareCountByFlavor;
    }

    private static Set<Flavor> recursiveReplacements(Flavor flavor, Set<Flavor> replacements) {
        replacements.add(flavor);
        for (Flavor replaces : flavor.replaces()) {
            recursiveReplacements(replaces, replacements);
        }

        return replacements;
    }

    private FlavorSpareCount(Flavor flavor) {
        this.flavor = flavor;
    }

    public Flavor getFlavor() {
        return flavor;
    }

    void updateReadyAndActiveCounts(long numReady, long numActive) {
        this.numReady = numReady;
        this.numActive = numActive;
    }

    boolean hasReady() {
        return numReady > 0;
    }

    public long getNumReadyAmongReplacees() {
        long sumReadyNodes = numReady;
        for (FlavorSpareCount replacee : immediateReplacees) {
            sumReadyNodes += replacee.getNumReadyAmongReplacees();
        }

        return sumReadyNodes;
    }

    Set<FlavorSpareCount> getPossibleWantedFlavors() {
        return possibleWantedFlavors;
    }

    Set<FlavorSpareCount> getImmediateReplacees() {
        return immediateReplacees;
    }

    void decrementNumberOfReady() {
        numReady--;
    }

    @Override
    public String toString() {
        return flavor.name() + " has " + numReady + " ready nodes and " + numActive + " active nodes";
    }
}
