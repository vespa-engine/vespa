// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch.lb;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * Group scheduler which attempts to equalize the load between groups by selecting the lest loaded
 * of two randomly selected groups.
 *
 * @author Henning Baldersheim
 */
class BestOfRandom2Scheduler implements GroupScheduler {

    private final Random random;
    private final Map<Integer, TrackedGroup> scoreboard;

    public BestOfRandom2Scheduler(Random random, Map<Integer, TrackedGroup> scoreboard) {
        this.random = random;
        this.scoreboard = scoreboard;
    }

    @Override
    public Optional<TrackedGroup> takeNextGroup(Set<Integer> rejectedGroups) {
        TrackedGroup gs = selectBestOf2(rejectedGroups, true);
        return (gs != null)
               ? Optional.of(gs)
               : Optional.ofNullable(selectBestOf2(rejectedGroups, false));
    }

    private TrackedGroup selectBestOf2(Set<Integer> rejectedGroups, boolean requireCoverage) {
        List<Integer> candidates = new ArrayList<>(scoreboard.size());
        for (TrackedGroup gs : scoreboard.values()) {
            if (rejectedGroups == null || !rejectedGroups.contains(gs.group().id())) {
                if (!requireCoverage || gs.group().hasSufficientCoverage()) {
                    candidates.add(gs.groupId());
                }
            }
        }
        TrackedGroup candA = selectRandom(candidates);
        TrackedGroup candB = selectRandom(candidates);
        if (candA == null) return candB;
        if (candB == null) return candA;
        if (candB.allocations() < candA.allocations()) return candB;
        return candA;
    }

    private TrackedGroup selectRandom(List<Integer> candidates) {
        if (!candidates.isEmpty()) {
            int index = random.nextInt(candidates.size());
            return scoreboard.get(candidates.remove(index));
        }
        return null;
    }

}
