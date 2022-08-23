// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch;

import com.yahoo.search.dispatch.searchcluster.Group;
import com.yahoo.search.dispatch.searchcluster.SearchCluster;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;

/**
 * LoadBalancer determines which group of content nodes should be accessed next for each search query when the
 * internal java dispatcher is used.
 * The implementation here is a simplistic least queries in flight + round-robin load balancer
 *
 * @author ollivir
 */
public class LoadBalancer {

    private static final Logger log = Logger.getLogger(LoadBalancer.class.getName());

    private static final long DEFAULT_LATENCY_DECAY_RATE = 1000;
    private static final long MIN_LATENCY_DECAY_RATE = 42;
    private static final double LATENCY_DECAY_TIME = Duration.ofSeconds(5).toMillis()/1000.0;
    private static final Duration INITIAL_QUERY_TIME = Duration.ofMillis(1);
    private static final double MIN_QUERY_TIME = Duration.ofMillis(1).toMillis()/1000.0;

    private final List<GroupStatus> scoreboard;
    private final GroupScheduler scheduler;

    public enum Policy { ROUNDROBIN, LATENCY_AMORTIZED_OVER_REQUESTS, LATENCY_AMORTIZED_OVER_TIME, BEST_OF_RANDOM_2}

    public LoadBalancer(SearchCluster searchCluster, Policy policy) {
        this.scoreboard = new ArrayList<>(searchCluster.groups().size());
        for (Group group : searchCluster.orderedGroups()) {
            scoreboard.add(new GroupStatus(group));
        }
        if (scoreboard.size() == 1)
            policy = Policy.ROUNDROBIN;

        this.scheduler = switch (policy) {
            case ROUNDROBIN: yield new RoundRobinScheduler(scoreboard);
            case BEST_OF_RANDOM_2: yield new BestOfRandom2(new Random(), scoreboard);
            case LATENCY_AMORTIZED_OVER_REQUESTS: yield new AdaptiveScheduler(AdaptiveScheduler.Type.REQUESTS, new Random(), scoreboard);
            case LATENCY_AMORTIZED_OVER_TIME: yield new AdaptiveScheduler(AdaptiveScheduler.Type.TIME, new Random(), scoreboard);
        };
    }

    /**
     * Select and allocate the search cluster group which is to be used for the next search query.
     * Callers <b>must</b> call {@link #releaseGroup} symmetrically for each taken allocation.
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
     * @param searchTime query execution time, used for adaptive load balancing
     */
    public void releaseGroup(Group group, boolean success, RequestDuration searchTime) {
        synchronized (this) {
            for (GroupStatus sched : scoreboard) {
                if (sched.group.id() == group.id()) {
                    sched.release(success, searchTime);
                    break;
                }
            }
        }
    }

    static class GroupStatus {

        interface Decayer {
            void decay(RequestDuration duration);
            double averageCost();
        }

        static class NoDecay implements Decayer {
            public void decay(RequestDuration duration) {}
            public double averageCost() { return MIN_QUERY_TIME; }
        }

        private final Group group;
        private int allocations = 0;
        private Decayer decayer;

        GroupStatus(Group group) {
            this.group = group;
            this.decayer = new NoDecay();
        }
        void setDecayer(Decayer decayer) {
            this.decayer = decayer;
        }

        void allocate() {
            allocations++;
        }

        void release(boolean success, RequestDuration searchTime) {
            allocations--;
            if (allocations < 0) {
                log.warning("Double free of query target group detected");
                allocations = 0;
            }
            if (success) {
                decayer.decay(searchTime);
            }
        }

        double weight() {
            return 1.0 / decayer.averageCost();
        }

        int groupId() {
            return group.id();
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
            if (second == null) return first;
            if (first == null) return second;
            if (first.group.hasSufficientCoverage() != second.group.hasSufficientCoverage())
                return first.group.hasSufficientCoverage() ? first : second;
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
        enum Type {TIME, REQUESTS}
        private final Random random;
        private final List<GroupStatus> scoreboard;

        private static double toDouble(Duration duration) {
            return duration.toNanos()/1_000_000_000.0;
        }
        private static Duration fromDouble(double seconds) { return Duration.ofNanos((long)(seconds*1_000_000_000));}

        static class DecayByRequests implements GroupStatus.Decayer {
            private long queries;
            private double averageSearchTime;
            DecayByRequests() {
                this(0, INITIAL_QUERY_TIME);
            }
            DecayByRequests(long initialQueries, Duration initialSearchTime) {
                queries = initialQueries;
                averageSearchTime = toDouble(initialSearchTime);
            }
            public void decay(RequestDuration duration) {
                double searchTime = Math.max(toDouble(duration.duration()), MIN_QUERY_TIME);
                double decayRate = Math.min(queries + MIN_LATENCY_DECAY_RATE, DEFAULT_LATENCY_DECAY_RATE);
                queries++;
                averageSearchTime = (searchTime + (decayRate - 1) * averageSearchTime) / decayRate;
            }
            public double averageCost() { return averageSearchTime; }
            Duration averageSearchTime() { return fromDouble(averageSearchTime);}
        }

        static class DecayByTime implements GroupStatus.Decayer {
            private double averageSearchTime;
            private RequestDuration prev;
            DecayByTime() {
                this(INITIAL_QUERY_TIME, RequestDuration.of(Duration.ZERO));
            }
            DecayByTime(Duration initialSearchTime, RequestDuration start) {
                averageSearchTime = toDouble(initialSearchTime);
                prev = start;
            }
            public void decay(RequestDuration duration) {
                double searchTime = Math.max(toDouble(duration.duration()), MIN_QUERY_TIME);
                double sampleWeight = toDouble(duration.difference(prev));
                averageSearchTime = (sampleWeight*searchTime + LATENCY_DECAY_TIME * averageSearchTime) / (LATENCY_DECAY_TIME + sampleWeight);
                prev = duration;
            }
            public double averageCost() { return averageSearchTime; }
            Duration averageSearchTime() { return fromDouble(averageSearchTime);}
        }

        public AdaptiveScheduler(Type type, Random random, List<GroupStatus> scoreboard) {
            this.random = random;
            this.scoreboard = scoreboard;
            for (GroupStatus gs : scoreboard) {
                gs.setDecayer(type == Type.REQUESTS ? new DecayByRequests() : new DecayByTime());
            }
        }

        private Optional<GroupStatus> selectGroup(double needle, boolean requireCoverage, Set<Integer> rejected) {
            double sum = 0;
            int n = 0;
            for (GroupStatus gs : scoreboard) {
                if (rejected == null || !rejected.contains(gs.group.id())) {
                    if (!requireCoverage || gs.group.hasSufficientCoverage()) {
                        sum += gs.weight();
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
                        accum += gs.weight();
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
            if (gs.isPresent()) return gs;
            return selectGroup(needle, false, rejectedGroups); // any coverage better than none
        }
    }

    static class BestOfRandom2 implements GroupScheduler {
        private final Random random;
        private final List<GroupStatus> scoreboard;
        public BestOfRandom2(Random random, List<GroupStatus> scoreboard) {
            this.random = random;
            this.scoreboard = scoreboard;
        }
        @Override
        public Optional<GroupStatus> takeNextGroup(Set<Integer> rejectedGroups) {
            GroupStatus gs = selectBestOf2(rejectedGroups, true);
            return (gs != null)
                    ? Optional.of(gs)
                    : Optional.ofNullable(selectBestOf2(rejectedGroups, false));
        }

        private GroupStatus selectBestOf2(Set<Integer> rejectedGroups, boolean requireCoverage) {
            List<Integer> candidates = new ArrayList<>(scoreboard.size());
            for (int i=0; i < scoreboard.size(); i++) {
                GroupStatus gs = scoreboard.get(i);
                if (rejectedGroups == null || !rejectedGroups.contains(gs.group.id())) {
                    if (!requireCoverage || gs.group.hasSufficientCoverage()) {
                        candidates.add(i);
                    }
                }
            }
            GroupStatus candA = selectRandom(candidates);
            GroupStatus candB = selectRandom(candidates);
            if (candA == null) return candB;
            if (candB == null) return candA;
            if (candB.allocations < candA.allocations) return candB;
            return candA;
        }
        private GroupStatus selectRandom(List<Integer> candidates) {
            if ( ! candidates.isEmpty()) {
                int index = random.nextInt(candidates.size());
                Integer groupIndex = candidates.remove(index);
                return scoreboard.get(groupIndex);
            }
            return null;
        }

    }

}
