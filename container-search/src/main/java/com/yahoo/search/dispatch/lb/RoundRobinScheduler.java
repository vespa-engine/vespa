// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch.lb;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A scheduler which picks groups round-robin.
 *
 * @author Olli Virtanen
 */
class RoundRobinScheduler implements GroupScheduler {

    private int needle = 0;
    private final Map<Integer, TrackedGroup> scoreboard;

    public RoundRobinScheduler(Map<Integer, TrackedGroup> scoreboard) {
        this.scoreboard = scoreboard;
    }

    @Override
    public Optional<TrackedGroup> takeNextGroup(Set<Integer> rejectedGroups) {
        TrackedGroup bestCandidate = null;

        int groupId = needle;
        for (int i = 0; i < scoreboard.size(); i++) {
            TrackedGroup candidate = scoreboard.get(groupId);
            if (rejectedGroups == null || !rejectedGroups.contains(candidate.id())) {
                TrackedGroup better = betterGroup(bestCandidate, candidate);
                if (better == candidate)
                    bestCandidate = candidate;
            }
            groupId = nextScoreboardIndex(groupId);
        }
        if (bestCandidate == null) return Optional.empty();
        needle = nextScoreboardIndex(bestCandidate.id());
        return Optional.of(bestCandidate);
    }

    /**
     * Select the better of the two given GroupStatus objects, biased to the first
     * parameter. Thus, if all groups have equal coverage sufficiency, the one
     * currently at the needle will be used. Either parameter can be null, in which
     * case any non-null will be preferred.
     *
     * @param first  preferred GroupStatus
     * @param second potentially better GroupStatus
     * @return the better of the two
     */
    private static TrackedGroup betterGroup(TrackedGroup first, TrackedGroup second) {
        if (second == null) return first;
        if (first == null) return second;
        if (first.group().hasSufficientCoverage() != second.group().hasSufficientCoverage())
            return first.group().hasSufficientCoverage() ? first : second;
        return first;
    }

    private int nextScoreboardIndex(int current) {
        int next = current + 1;
        if (next >= scoreboard.size())
            next %= scoreboard.size();
        return next;
    }

}
