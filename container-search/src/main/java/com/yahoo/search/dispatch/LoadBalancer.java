// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch;

import com.yahoo.search.dispatch.searchcluster.Group;
import com.yahoo.search.dispatch.searchcluster.SearchCluster;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;

/**
 * LoadBalancer determines which group of content nodes should be accessed next for each search query when the internal java dispatcher is
 * used.
 *
 * @author ollivir
 */
public class LoadBalancer {
    // The implementation here is a simplistic least queries in flight + round-robin load balancer

    private static final Logger log = Logger.getLogger(LoadBalancer.class.getName());

    private static final long DEFAULT_LATENCY_DECAY_RATE = 1000;
    private static final long MIN_LATENCY_DECAY_RATE = 42;
    private static final double INITIAL_QUERY_TIME = 0.001;
    private static final double MIN_QUERY_TIME = 0.001;

    private final List<GroupStatus> scoreboard;
    private final GroupScheduler scheduler;

    public LoadBalancer(SearchCluster searchCluster, boolean roundRobin) {
        this.scoreboard = new ArrayList<>(searchCluster.groups().size());
        for (Group group : searchCluster.orderedGroups()) {
            scoreboard.add(new GroupStatus(group));
        }
        if (roundRobin || scoreboard.size() == 1) {
            this.scheduler = new RoundRobinScheduler(scoreboard);
        } else {
            this.scheduler = new AdaptiveScheduler(new Random(), scoreboard);
        }
    }

    /**
     * Select and allocate the search cluster group which is to be used for the next search query. Callers <b>must</b> call
     * {@link #releaseGroup} symmetrically for each taken allocation.
     *
     * @param rejectedGroups if not null, the load balancer will only return groups with IDs not in the set
     * @return the node group to target, or <i>empty</i> if the internal dispatch logic cannot be used
     */
    public Optional<Group> takeGroup(Set<Integer> rejectedGroups) {
        synchronized (this) {
            Optional<GroupStatus> best = scheduler.takeNextGroup(rejectedGroups);

            if (best.isPresent()) {
                GroupStatus gs = best.get();
                gs.allocate();
                Group ret = gs.group;
                log.fine(() -> "Offering <" + ret + "> for query connection");
                return Optional.of(ret);
            } else {
                return Optional.empty();
            }
        }
    }

    /**
     * Release an allocation given by {@link #takeGroup}. The release must be done exactly once for each allocation.
     *
     * @param group previously allocated group
     * @param success was the query successful
     * @param searchTimeMs query execution time in milliseconds, used for adaptive load balancing
     */
    public void releaseGroup(Group group, boolean success, double searchTimeMs) {
        synchronized (this) {
            for (GroupStatus sched : scoreboard) {
                if (sched.group.id() == group.id()) {
                    sched.release(success, (double) searchTimeMs / 1000.0);
                    break;
                }
            }
        }
    }

    static class GroupStatus {
        private final Group group;
        private int allocations = 0;
        private long queries = 0;
        private double averageSearchTime = INITIAL_QUERY_TIME;

        GroupStatus(Group group) {
            this.group = group;
        }

        void allocate() {
            allocations++;
        }

        void release(boolean success, double searchTime) {
            allocations--;
            if (allocations < 0) {
                log.warning("Double free of query target group detected");
                allocations = 0;
            }
            if (success) {
                searchTime = Math.max(searchTime, MIN_QUERY_TIME);
                double decayRate = Math.min(queries + MIN_LATENCY_DECAY_RATE, DEFAULT_LATENCY_DECAY_RATE);
                averageSearchTime = (searchTime + (decayRate - 1) * averageSearchTime) / decayRate;
                queries++;
            }
        }

        double averageSearchTime() {
            return averageSearchTime;
        }

        double averageSearchTimeInverse() {
            return 1.0 / averageSearchTime;
        }

        int groupId() {
            return group.id();
        }

        void setQueryStatistics(long queries, double averageSearchTime) {
            this.queries = queries;
            this.averageSearchTime = averageSearchTime;
        }
    }

    private interface GroupScheduler {
        Optional<GroupStatus> takeNextGroup(Set<Integer> rejectedGroups);
    }

    private static class RoundRobinScheduler implements GroupScheduler {
        private int needle = 0;
        private final List<GroupStatus> scoreboard;

        public RoundRobinScheduler(List<GroupStatus> scoreboard) {
            this.scoreboard = scoreboard;
        }

        @Override
        public Optional<GroupStatus> takeNextGroup(Set<Integer> rejectedGroups) {
            GroupStatus bestCandidate = null;
            int bestIndex = needle;

            int index = needle;
            for (int i = 0; i < scoreboard.size(); i++) {
                GroupStatus candidate = scoreboard.get(index);
                if (rejectedGroups == null || !rejectedGroups.contains(candidate.group.id())) {
                    GroupStatus better = betterGroup(bestCandidate, candidate);
                    if (better == candidate) {
                        bestCandidate = candidate;
                        bestIndex = index;
                    }
                }
                index = nextScoreboardIndex(index);
            }
            needle = nextScoreboardIndex(bestIndex);
            return Optional.ofNullable(bestCandidate);
        }

        /**
         * Select the better of the two given GroupStatus objects, biased to the first
         * parameter. Thus, if all groups have equal coverage sufficiency, the one
         * currently at the needle will be used. Either parameter can be null, in which
         * case any non-null will be preferred.
         *
         * @param first preferred GroupStatus
         * @param second potentially better GroupStatus
         * @return the better of the two
         */
        private static GroupStatus betterGroup(GroupStatus first, GroupStatus second) {
            if (second == null) {
                return first;
            }
            if (first == null) {
                return second;
            }

            // different coverage
            if (first.group.hasSufficientCoverage() != second.group.hasSufficientCoverage()) {
                if (!first.group.hasSufficientCoverage()) {
                    // first doesn't have coverage, second does
                    return second;
                } else {
                    // second doesn't have coverage, first does
                    return first;
                }
            }

            return first;
        }

        private int nextScoreboardIndex(int current) {
            int next = current + 1;
            if (next >= scoreboard.size()) {
                next %= scoreboard.size();
            }
            return next;
        }
    }

    static class AdaptiveScheduler implements GroupScheduler {
        private final Random random;
        private final List<GroupStatus> scoreboard;

        public AdaptiveScheduler(Random random, List<GroupStatus> scoreboard) {
            this.random = random;
            this.scoreboard = scoreboard;
        }

        private Optional<GroupStatus> selectGroup(double needle, boolean requireCoverage, Set<Integer> rejected) {
            double sum = 0;
            int n = 0;
            for (GroupStatus gs : scoreboard) {
                if (rejected == null || !rejected.contains(gs.group.id())) {
                    if (!requireCoverage || gs.group.hasSufficientCoverage()) {
                        sum += gs.averageSearchTimeInverse();
                        n++;
                    }
                }
            }
            if (n == 0) {
                return Optional.empty();
            }
            double accum = 0;
            for (GroupStatus gs : scoreboard) {
                if (rejected == null || !rejected.contains(gs.group.id())) {
                    if (!requireCoverage || gs.group.hasSufficientCoverage()) {
                        accum += gs.averageSearchTimeInverse();
                        if (needle < accum / sum) {
                            return Optional.of(gs);
                        }
                    }
                }
            }
            return Optional.empty(); // should not happen here
        }

        @Override
        public Optional<GroupStatus> takeNextGroup(Set<Integer> rejectedGroups) {
            double needle = random.nextDouble();
            Optional<GroupStatus> gs = selectGroup(needle, true, rejectedGroups);
            if (gs.isPresent()) {
                return gs;
            }
            // fallback - any coverage better than none
            return selectGroup(needle, false, rejectedGroups);
        }
    }
}
