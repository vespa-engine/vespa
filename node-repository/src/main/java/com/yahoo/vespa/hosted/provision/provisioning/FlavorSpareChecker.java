// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.Flavor;
import com.yahoo.vespa.hosted.provision.Node;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Definitions:
 * - Wanted flavor: The flavor that is the node prefers, for example by specifying in services.xml
 * - Node-repo flavor: The flavor that the node actually has (Either the wanted flavor or a flavor that transitively
 *                     replaces the wanted flavor)
 * - Replacee flavor: Flavor x is replacee of y iff x transitively replaces y
 * - Immediate replacee flavor: Flavor x is an immediate replacee of flavor y iff x directly replaces y.
 *
 * @author freva
 */
public class FlavorSpareChecker {
    private final SpareNodesPolicy spareNodesPolicy;
    private final Map<Flavor, FlavorSpareCount> spareCountByFlavor;

    public FlavorSpareChecker(SpareNodesPolicy spareNodesPolicy, Map<Flavor, FlavorSpareCount> spareCountByFlavor) {
        this.spareNodesPolicy = spareNodesPolicy;
        this.spareCountByFlavor = spareCountByFlavor;
    }

    public void updateReadyAndActiveCountsByFlavor(Map<Flavor, Map<Node.State, Long>> numberOfNodesByFlavorByState) {
        spareCountByFlavor.forEach((flavor, flavorSpareCount) -> {
            Map<Node.State, Long> numberOfNodesByState = numberOfNodesByFlavorByState.getOrDefault(flavor, Collections.emptyMap());
            flavorSpareCount.updateReadyAndActiveCounts(
                    numberOfNodesByState.getOrDefault(Node.State.ready, 0L),
                    numberOfNodesByState.getOrDefault(Node.State.active, 0L));
        });
    }

    public boolean canRetireAllocatedNodeWithFlavor(Flavor flavor) {
        Set<FlavorSpareCount> possibleNewFlavors = findPossibleReplacementFlavorFor(spareCountByFlavor.get(flavor));
        possibleNewFlavors.forEach(FlavorSpareCount::decrementNumberOfSpares);
        return !possibleNewFlavors.isEmpty();
    }

    public boolean canRetireUnallocatedNodeWithFlavor(Flavor flavor) {
        FlavorSpareCount flavorSpareCount = spareCountByFlavor.get(flavor);
        if (flavorSpareCount.hasReady() && spareNodesPolicy.hasSpare(flavorSpareCount)) {
            flavorSpareCount.decrementNumberOfSpares();
            return true;
        }

        return false;
    }


    /**
     * Returns a set of possible new flavors that can replace this flavor given current node allocation.
     * If the set is empty, there are not enough spare nodes to safely retire this flavor.
     *
     * The algorithm is:
     *  for all possible wanted flavor, check:
     *      1: Sum of spare nodes of flavor f and all replacee flavors of f is > 0
     *      2a: Number of ready nodes of flavor f is > 0
     *      2b: Verify 1 & 2a for all immediate replacee of f, f_i, where sum of ready nodes of f_i and all
     *          replacee flavors of f_i is > 0
     * Only 2a OR 2b need to be satisfied.
     */
    private Set<FlavorSpareCount> findPossibleReplacementFlavorFor(FlavorSpareCount flavorSpareCount) {
        Set<FlavorSpareCount> possibleNewFlavors = new HashSet<>();
        for (FlavorSpareCount possibleWantedFlavor : flavorSpareCount.getPossibleWantedFlavors()) {
            Set<FlavorSpareCount> newFlavors = verifyReplacementConditions(possibleWantedFlavor);
            if (newFlavors.isEmpty()) return Collections.emptySet();
            else possibleNewFlavors.addAll(newFlavors);
        }

        return possibleNewFlavors;
    }

    private Set<FlavorSpareCount> verifyReplacementConditions(FlavorSpareCount flavorSpareCount) {
        Set<FlavorSpareCount> possibleNewFlavors = new HashSet<>();
        // Breaks condition 1, end
        if (! spareNodesPolicy.hasSpare(flavorSpareCount)) return Collections.emptySet();

        // Condition 2a
        if (flavorSpareCount.hasReady()) {
            possibleNewFlavors.add(flavorSpareCount);

            // Condition 2b
        } else {
            for (FlavorSpareCount possibleNewFlavor : flavorSpareCount.getImmediateReplacees()) {
                if (possibleNewFlavor.getSumOfReadyAmongReplacees() == 0) continue;

                Set<FlavorSpareCount> newFlavors = verifyReplacementConditions(possibleNewFlavor);
                if (newFlavors.isEmpty()) return Collections.emptySet();
                else possibleNewFlavors.addAll(newFlavors);
            }
        }
        return possibleNewFlavors;
    }

    public interface SpareNodesPolicy {
        boolean hasSpare(FlavorSpareCount flavorSpareCount);
    }
}
