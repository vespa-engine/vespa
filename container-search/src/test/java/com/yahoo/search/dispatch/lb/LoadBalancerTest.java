// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch.lb;

import com.yahoo.search.dispatch.RequestDuration;
import com.yahoo.search.dispatch.searchcluster.Group;
import com.yahoo.search.dispatch.searchcluster.Node;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Olli Virtanen
 */
public class LoadBalancerTest {

    private static final double delta = 0.0000001;

    @Test
    void test_single_node() {
        Node n1 = new Node("test", 0, "test-node1", 0, false);
        LoadBalancer lb = new LoadBalancer(List.of(new Group(0, List.of(n1))),
                                           LoadBalancer.Policy.ROUNDROBIN,
                                           "default");

        Optional<Group> grp = lb.takeAnyGroupNotIn(Set.of());
        Group group = grp.orElseThrow(() -> new IllegalStateException("Expected a SearchCluster.Group"));
        assertEquals(1, group.nodes().size());
    }

    @Test
    void test_az_aware_group_load_balancing() {
        new LoadBalancerTester(LoadBalancer.Policy.ROUNDROBIN, 0.0).assertAzAwareLoadBalancing();
        new LoadBalancerTester(LoadBalancer.Policy.ADAPTIVE, 3.0).assertAzAwareLoadBalancing();
        new LoadBalancerTester(LoadBalancer.Policy.BEST_OF_RANDOM_2, 3.0).assertAzAwareLoadBalancing();
        new LoadBalancerTester(LoadBalancer.Policy.LATENCY_AMORTIZED_OVER_TIME, 3.0).assertAzAwareLoadBalancing();
    }

    @Test
    void test_search_time_decay() {
        AdaptiveScheduler.DecayByRequests decayer = new AdaptiveScheduler.DecayByRequests(0, Duration.ofSeconds(1));
        TrackedGroup gs = newGroupStatus(1);
        gs.setDecayer(decayer);
        updateSearchTime(gs, RequestDuration.of(Duration.ofSeconds(1)));
        assertEquals(Duration.ofSeconds(1), decayer.averageSearchTime());
        updateSearchTime(gs, RequestDuration.of(Duration.ofSeconds(2)));
        assertEquals(Duration.ofNanos(1023255813), decayer.averageSearchTime());
        updateSearchTime(gs, RequestDuration.of(Duration.ofSeconds(2)));
        assertEquals(Duration.ofNanos(1045454545), decayer.averageSearchTime());
        updateSearchTime(gs, RequestDuration.of(Duration.ofMillis(100)));
        updateSearchTime(gs, RequestDuration.of(Duration.ofMillis(100)));
        updateSearchTime(gs, RequestDuration.of(Duration.ofMillis(100)));
        updateSearchTime(gs, RequestDuration.of(Duration.ofMillis(100)));
        assertEquals(Duration.ofNanos(966666666), decayer.averageSearchTime());
        for (int i = 0; i < 10000; i++) {
            updateSearchTime(gs, RequestDuration.of(Duration.ofSeconds(1)));
        }
        assertEquals(Duration.ofNanos(999999812), decayer.averageSearchTime());
        updateSearchTime(gs, RequestDuration.of(Duration.ofMillis(100)));
        assertEquals(Duration.ofNanos(999099812), decayer.averageSearchTime());
        for (int i = 0; i < 10000; i++) {
            updateSearchTime(gs, RequestDuration.of(Duration.ZERO));
        }
        assertEquals(Duration.ofNanos(1045087), decayer.averageSearchTime());
    }

    @Test
    void test_adaptive_scheduler_flat() {
        Random seq = sequence(0.0, 0.1, 0.2, 0.39, 0.4, 0.6, 0.8, 0.99999);
        AdaptiveScheduler sched = new AdaptiveScheduler(AdaptiveScheduler.Type.REQUESTS, seq, createScoreBoard(5));

        assertEquals(0, sched.takeNextGroup(null).get().id());
        assertEquals(0, sched.takeNextGroup(null).get().id());
        assertEquals(1, sched.takeNextGroup(null).get().id());
        assertEquals(1, sched.takeNextGroup(null).get().id());
        assertEquals(2, sched.takeNextGroup(null).get().id());
        assertEquals(3, sched.takeNextGroup(null).get().id());
        assertEquals(4, sched.takeNextGroup(null).get().id());
        assertEquals(4, sched.takeNextGroup(null).get().id());
    }

    @Test
    void test_adaptive_scheduler_weighted() {
        var scoreboard = createScoreBoard(5);
        Random sequence = sequence(0.0, 0.4379, 0.4380, 0.6569, 0.6570, 0.8029, 0.8030, 0.9124, 0.9125);
        var scheduler = new AdaptiveScheduler(AdaptiveScheduler.Type.REQUESTS, sequence, scoreboard);
        int i = 0;
        for (TrackedGroup gs : scoreboard.values()) {
            gs.setDecayer(new AdaptiveScheduler.DecayByRequests(1, Duration.ofMillis((long)(0.1 * (i + 1)*1000.0))));
            i++;
        }

        assertEquals(0, scheduler.takeNextGroup(null).get().id());
        assertEquals(0, scheduler.takeNextGroup(null).get().id());
        assertEquals(1, scheduler.takeNextGroup(null).get().id());
        assertEquals(1, scheduler.takeNextGroup(null).get().id());
        assertEquals(2, scheduler.takeNextGroup(null).get().id());
        assertEquals(2, scheduler.takeNextGroup(null).get().id());
        assertEquals(3, scheduler.takeNextGroup(null).get().id());
        assertEquals(3, scheduler.takeNextGroup(null).get().id());
        assertEquals(4, scheduler.takeNextGroup(null).get().id());
    }

    @Test
    void test_best_of_random_2_scheduler() {
        Random seq = sequence(
                0.1, 0.125,
                0.1, 0.125,
                0.1, 0.125,
                0.1, 0.125,
                0.1, 0.375,
                0.9, 0.125,
                0.9, 0.125,
                0.9, 0.125
                );
        BestOfRandom2Scheduler sched = new BestOfRandom2Scheduler(seq, createScoreBoard(5));

        assertEquals(0, allocate(sched.takeNextGroup(Set.of()).get()).id());
        assertEquals(1, allocate(sched.takeNextGroup(Set.of()).get()).id());
        assertEquals(0, allocate(sched.takeNextGroup(Set.of()).get()).id());
        assertEquals(1, allocate(sched.takeNextGroup(Set.of()).get()).id());
        assertEquals(2, allocate(sched.takeNextGroup(Set.of()).get()).id());
        assertEquals(4, allocate(sched.takeNextGroup(Set.of()).get()).id());
        assertEquals(4, allocate(sched.takeNextGroup(Set.of()).get()).id());
        assertEquals(4, allocate(sched.takeNextGroup(Set.of()).get()).id());
        assertEquals(0, allocate(sched.takeNextGroup(Set.of()).get()).id());
    }

    @Test
    public void test_decay_by_time() {
        Decayer decayer = new AdaptiveScheduler.DecayByTime(Duration.ofMillis(2), RequestDuration.of(Instant.EPOCH, Duration.ZERO));
        assertEquals(0.002, decayer.averageCost(), delta);
        decayer.decay(RequestDuration.of(Instant.ofEpochMilli(1000), Duration.ofMillis(10)));
        assertEquals(0.003344426, decayer.averageCost(), delta);
        decayer.decay(RequestDuration.of(Instant.ofEpochMilli(2000), Duration.ofMillis(10)));
        assertEquals(0.004453688, decayer.averageCost(), delta);
        decayer.decay(RequestDuration.of(Instant.ofEpochMilli(3000), Duration.ofMillis(10)));
        assertEquals(0.005378073, decayer.averageCost(), delta);
        decayer.decay(RequestDuration.of(Instant.ofEpochMilli(3100), Duration.ofMillis(10)));
        assertEquals(0.005468700, decayer.averageCost(), delta);
        decayer.decay(RequestDuration.of(Instant.ofEpochMilli(3100), Duration.ofMillis(10)));
        assertEquals(0.005468700, decayer.averageCost(), delta);
        decayer.decay(RequestDuration.of(Instant.ofEpochMilli(3000), Duration.ofMillis(10)));
        assertEquals(0.005557549, decayer.averageCost(), delta);
        decayer.decay(RequestDuration.of(Instant.ofEpochMilli(5000), Duration.ofMillis(10)));
        assertEquals(0.006826820, decayer.averageCost(), delta);
        assertEquals(112, countRequestsToReach90p(Duration.ofMillis(100), Duration.ofMillis(10)));
        assertEquals(57, countRequestsToReach90p(Duration.ofMillis(200), Duration.ofMillis(10)));
        assertEquals(14, countRequestsToReach90p(Duration.ofMillis(1000), Duration.ofMillis(10)));
    }

    @Test
    public void test_decay_by_time_does_not_jump_too_far() {
        AdaptiveScheduler.DecayByTime decayer = new AdaptiveScheduler.DecayByTime(Duration.ofMillis(2), RequestDuration.of(Instant.EPOCH, Duration.ZERO));
        assertEquals(0.002, decayer.averageCost(), delta);
        assertEquals(Duration.ofMillis(2), decayer.averageSearchTime());
        decayer.decay(RequestDuration.of(Instant.ofEpochMilli(10000), Duration.ofMillis(10)));
        assertEquals(0.007335110, decayer.averageCost(), delta);
        assertEquals(Duration.ofNanos(7335109), decayer.averageSearchTime());

    }

    private Map<Integer, TrackedGroup> createScoreBoard(int count) {
        Map<Integer, TrackedGroup> scoreboard = new HashMap<>();
        for (int i = 0; i < count; i++) {
            TrackedGroup gs = newGroupStatus(i);
            scoreboard.put(gs.id(), gs);
        }
        return scoreboard;
    }

    private static TrackedGroup allocate(TrackedGroup gs) {
        gs.allocate();
        return gs;
    }

    private static int countRequestsToReach90p(Duration timeBetweenSample, Duration searchTime) {
        double p90 = 0.9*searchTime.toMillis()/1000.0;
        Decayer decayer = new AdaptiveScheduler.DecayByTime(Duration.ofMillis(1), RequestDuration.of(Instant.EPOCH, Duration.ZERO));
        int requests = 0;
        Instant start = Instant.EPOCH;
        while (decayer.averageCost() < p90) {
            decayer.decay(RequestDuration.of(start, searchTime));
            start = start.plus(timeBetweenSample);
            requests++;
        }
        return requests;
    }

    private static void updateSearchTime(TrackedGroup gs, RequestDuration time) {
        gs.allocate();
        gs.release(true, time);
    }

    private TrackedGroup newGroupStatus(int id) {
        Group dummyGroup = new Group(id, List.of()) {
            @Override
            public boolean hasSufficientCoverage() {
                return true;
            }
        };
        return new TrackedGroup(dummyGroup);
    }

    private Random sequence(double... values) {
        return new Random() {
            private int index = 0;

            @Override
            public double nextDouble() {
                double retv = values[index];
                index++;
                if (index >= values.length) {
                    index = 0;
                }
                return retv;
            }
            @Override
            public int nextInt(int bound) {
                return (int)(nextDouble() * bound);
            }
        };
    }

}
