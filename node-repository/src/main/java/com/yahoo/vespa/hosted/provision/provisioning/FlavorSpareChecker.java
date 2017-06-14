// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.Flavor;
import com.yahoo.vespa.hosted.provision.Node;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * This class helps answer the question if there are enough nodes to retire a node with flavor f by:
 * <ul>
 *   <li>Finding all the possible flavors that the replacement node could end up on</li>
 *   <li>Making sure that regardless of which flavor it ends up on, there is still enough spare nodes
 *       to handle at unexpected node failures.</li>
 * </ul>
 * <p>
 * Definitions:
 * <ul>
 *   <li>Wanted flavor: The flavor that is the node prefers, for example by specifying in services.xml</li>
 *   <li>Node-repo flavor: The flavor that the node actually has (Either the wanted flavor or a flavor that transitively
 *                     replaces the wanted flavor)</li>
 *   <li>Replacee flavor: Flavor x is replacee of y iff x transitively replaces y</li>
 *   <li>Immediate replacee flavor: Flavor x is an immediate replacee of flavor y iff x directly replaces y.</li>
 * </ul>
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
        possibleNewFlavors.forEach(FlavorSpareCount::decrementNumberOfReady);
        return !possibleNewFlavors.isEmpty();
    }

    public boolean canRetireUnallocatedNodeWithFlavor(Flavor flavor) {
        FlavorSpareCount flavorSpareCount = spareCountByFlavor.get(flavor);
        if (flavorSpareCount.hasReady() && spareNodesPolicy.hasSpare(flavorSpareCount)) {
            flavorSpareCount.decrementNumberOfReady();
            return true;
        }

        return false;
    }


    /**
     * Returns a set of possible new flavors that can replace this flavor given current node allocation.
     * If the set is empty, there are not enough spare nodes to safely retire this flavor.
     * <p>
     * The algorithm is:
     * for all possible wanted flavor, check:
     * <ul>
     *   <li>1: Sum of ready nodes of flavor f and all replacee flavors of f is &gt; reserved (set by {@link SpareNodesPolicy}</li>
     *   <li>2a: Number of ready nodes of flavor f is &gt; 0</li>
     *   <li>2b: Verify 1 &amp; 2 for all immediate replacee of f, f_i, where sum of ready nodes of f_i and all
     *       replacee flavors of f_i is &gt; 0</li>
 *     </ul>
     * Only 2a OR 2b need to be satisfied.
     */
    private Set<FlavorSpareCount> findPossibleReplacementFlavorFor(FlavorSpareCount flavorSpareCount) {
        Set<FlavorSpareCount> possibleReplacementFlavors = new HashSet<>();
        for (FlavorSpareCount possibleWantedFlavor : flavorSpareCount.getPossibleWantedFlavors()) {
            Set<FlavorSpareCount> replacementFlavors = verifyReplacementConditions(possibleWantedFlavor);
            if (replacementFlavors.isEmpty()) return Collections.emptySet();
            else possibleReplacementFlavors.addAll(replacementFlavors);
        }

        return possibleReplacementFlavors;
    }

    private Set<FlavorSpareCount> verifyReplacementConditions(FlavorSpareCount flavorSpareCount) {
        Set<FlavorSpareCount> possibleReplacementFlavors = new HashSet<>();
        // Breaks condition 1, end
        if (! spareNodesPolicy.hasSpare(flavorSpareCount)) return Collections.emptySet();

        // Condition 2a
        if (flavorSpareCount.hasReady()) {
            possibleReplacementFlavors.add(flavorSpareCount);

        // Condition 2b
        } else {
            for (FlavorSpareCount possibleNewFlavor : flavorSpareCount.getImmediateReplacees()) {
                if (possibleNewFlavor.getNumReadyAmongReplacees() == 0) continue;

                Set<FlavorSpareCount> replacementFlavors = verifyReplacementConditions(possibleNewFlavor);
                if (replacementFlavors.isEmpty()) return Collections.emptySet();
                else possibleReplacementFlavors.addAll(replacementFlavors);
            }
        }
        return possibleReplacementFlavors;
    }

    public interface SpareNodesPolicy {
        boolean hasSpare(FlavorSpareCount flavorSpareCount);
    }
}
