// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch.lb;

import com.yahoo.search.dispatch.RequestDuration;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * Group scheduler which attempts to equalize the load between groups by selecting the group with
 * the lowest current average search time.
 *
 * @author Olli Virtanen
 */
class AdaptiveScheduler implements GroupScheduler {

    private static final long DEFAULT_LATENCY_DECAY_RATE = 1000;
    private static final long MIN_LATENCY_DECAY_RATE = 42;
    private static final double LATENCY_DECAY_TIME = Duration.ofSeconds(5).toMillis()/1000.0;
    private static final Duration INITIAL_QUERY_TIME = Duration.ofMillis(1);

    enum Type {TIME, REQUESTS}

    private final Random random;
    private final Map<Integer, TrackedGroup> scoreboard;

    public AdaptiveScheduler(Type type, Random random, Map<Integer, TrackedGroup> scoreboard) {
        this.random = random;
        this.scoreboard = scoreboard;
        scoreboard.forEach((id, gs) -> gs.setDecayer(type == Type.REQUESTS ? new DecayByRequests() : new DecayByTime()));
    }

    @Override
    public Optional<TrackedGroup> takeNextGroup(Set<Integer> rejectedGroups) {
        double needle = random.nextDouble();
        Optional<TrackedGroup> gs = selectGroup(needle, true, rejectedGroups);
        if (gs.isPresent()) return gs;
        return selectGroup(needle, false, rejectedGroups); // any coverage better than none. TODO: Done by Dispatcher
    }

    private Optional<TrackedGroup> selectGroup(double needle, boolean requireCoverage, Set<Integer> rejected) {
        double sum = 0;
        int n = 0;
        for (TrackedGroup group : scoreboard.values()) {
            if (rejected == null || !rejected.contains(group.id())) {
                if (!requireCoverage || group.group().hasSufficientCoverage()) {
                    sum += group.weight();
                    n++;
                }
            }
        }
        if (n == 0) {
            return Optional.empty();
        }
        double accumulator = 0;
        for (TrackedGroup group : scoreboard.values()) {
            if (rejected == null || !rejected.contains(group.id())) {
                if (!requireCoverage || group.group().hasSufficientCoverage()) {
                    accumulator += group.weight();
                    if (needle < accumulator / sum) {
                        return Optional.of(group);
                    }
                }
            }
        }
        return Optional.empty(); // should not happen here
    }

    private static double toDouble(Duration duration) {
        return duration.toNanos() / 1_000_000_000.0;
    }

    private static Duration fromDouble(double seconds) {return Duration.ofNanos((long) (seconds * 1_000_000_000));}

    static class DecayByRequests implements Decayer {

        private long queries;
        private double averageSearchTime;

        DecayByRequests() {
            this(0, INITIAL_QUERY_TIME);
        }

        DecayByRequests(long initialQueries, Duration initialSearchTime) {
            queries = initialQueries;
            averageSearchTime = toDouble(initialSearchTime);
        }

        @Override
        public void decay(RequestDuration duration) {
            double searchTime = Math.max(toDouble(duration.duration()), LoadBalancer.MIN_QUERY_TIME);
            double decayRate = Math.min(queries + MIN_LATENCY_DECAY_RATE, DEFAULT_LATENCY_DECAY_RATE);
            queries++;
            averageSearchTime = (searchTime + (decayRate - 1) * averageSearchTime) / decayRate;
        }

        @Override
        public double averageCost() {return averageSearchTime;}

        Duration averageSearchTime() {return fromDouble(averageSearchTime);}

    }

    static class DecayByTime implements Decayer {

        private double averageSearchTime;

        private RequestDuration prev;

        DecayByTime() {
            this(INITIAL_QUERY_TIME, RequestDuration.of(Duration.ZERO));
        }

        DecayByTime(Duration initialSearchTime, RequestDuration start) {
            averageSearchTime = toDouble(initialSearchTime);
            prev = start;
        }

        @Override
        public void decay(RequestDuration duration) {
            double searchTime = Math.max(toDouble(duration.duration()), LoadBalancer.MIN_QUERY_TIME);
            double sampleWeight = toDouble(duration.difference(prev));
            averageSearchTime = (sampleWeight * searchTime + LATENCY_DECAY_TIME * averageSearchTime) / (LATENCY_DECAY_TIME + sampleWeight);
            prev = duration;
        }

        @Override
        public double averageCost() {return averageSearchTime;}

        Duration averageSearchTime() {return fromDouble(averageSearchTime);}

    }

}
