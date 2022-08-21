// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch;

import com.yahoo.search.dispatch.LoadBalancer.AdaptiveScheduler;
import com.yahoo.search.dispatch.LoadBalancer.BestOfRandom2;
import com.yahoo.search.dispatch.LoadBalancer.GroupStatus;
import com.yahoo.search.dispatch.searchcluster.Group;
import com.yahoo.search.dispatch.searchcluster.Node;
import com.yahoo.search.dispatch.searchcluster.SearchCluster;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static com.yahoo.search.dispatch.MockSearchCluster.createDispatchConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author ollivir
 */
public class LoadBalancerTest {

    @Test
    void requireThatLoadBalancerServesSingleNodeSetups() {
        Node n1 = new Node(0, "test-node1", 0);
        SearchCluster cluster = new SearchCluster("a", createDispatchConfig(n1), null, null);
        LoadBalancer lb = new LoadBalancer(cluster, LoadBalancer.Policy.ROUNDROBIN);

        Optional<Group> grp = lb.takeGroup(null);
        Group group = grp.orElseGet(() -> {
            throw new AssertionFailedError("Expected a SearchCluster.Group");
        });
        assertEquals(1, group.nodes().size());
    }

    @Test
    void requireThatLoadBalancerServesMultiGroupSetups() {
        Node n1 = new Node(0, "test-node1", 0);
        Node n2 = new Node(1, "test-node2", 1);
        SearchCluster cluster = new SearchCluster("a", createDispatchConfig(n1, n2), null, null);
        LoadBalancer lb = new LoadBalancer(cluster, LoadBalancer.Policy.ROUNDROBIN);

        Optional<Group> grp = lb.takeGroup(null);
        Group group = grp.orElseGet(() -> {
            throw new AssertionFailedError("Expected a SearchCluster.Group");
        });
        assertEquals(1, group.nodes().size());
    }

    @Test
    void requireThatLoadBalancerServesClusteredGroups() {
        Node n1 = new Node(0, "test-node1", 0);
        Node n2 = new Node(1, "test-node2", 0);
        Node n3 = new Node(0, "test-node3", 1);
        Node n4 = new Node(1, "test-node4", 1);
        SearchCluster cluster = new SearchCluster("a", createDispatchConfig(n1, n2, n3, n4), null, null);
        LoadBalancer lb = new LoadBalancer(cluster, LoadBalancer.Policy.ROUNDROBIN);

        Optional<Group> grp = lb.takeGroup(null);
        assertTrue(grp.isPresent());
    }

    @Test
    void requireThatLoadBalancerReturnsDifferentGroups() {
        Node n1 = new Node(0, "test-node1", 0);
        Node n2 = new Node(1, "test-node2", 1);
        SearchCluster cluster = new SearchCluster("a", createDispatchConfig(n1, n2), null, null);
        LoadBalancer lb = new LoadBalancer(cluster, LoadBalancer.Policy.ROUNDROBIN);

        // get first group
        Optional<Group> grp = lb.takeGroup(null);
        Group group = grp.get();
        int id1 = group.id();
        // release allocation
        lb.releaseGroup(group, true, Duration.ofMillis(1));

        // get second group
        grp = lb.takeGroup(null);
        group = grp.get();
        assertNotEquals(id1, group.id());
    }

    @Test
    void requireCorrectAverageSearchTimeDecay() {
        final double delta = 0.00001;

        GroupStatus gs = newGroupStatus(1);
        gs.setQueryStatistics(0, Duration.ofSeconds(1));
        updateSearchTime(gs, Duration.ofSeconds(1));
        assertEquals(Duration.ofSeconds(1), gs.averageSearchTime());
        updateSearchTime(gs, Duration.ofSeconds(2));
        assertEquals(Duration.ofNanos(1023255813), gs.averageSearchTime());
        updateSearchTime(gs, Duration.ofSeconds(2));
        assertEquals(Duration.ofNanos(1045454545), gs.averageSearchTime());
        updateSearchTime(gs, Duration.ofMillis(100));
        updateSearchTime(gs, Duration.ofMillis(100));
        updateSearchTime(gs, Duration.ofMillis(100));
        updateSearchTime(gs, Duration.ofMillis(100));
        assertEquals(Duration.ofNanos(966666666), gs.averageSearchTime());
        for (int i = 0; i < 10000; i++) {
            updateSearchTime(gs, Duration.ofSeconds(1));
        }
        assertEquals(Duration.ofNanos(999999812), gs.averageSearchTime());
        updateSearchTime(gs, Duration.ofMillis(100));
        assertEquals(Duration.ofNanos(999099812), gs.averageSearchTime());
        for (int i = 0; i < 10000; i++) {
            updateSearchTime(gs, Duration.ZERO);
        }
        assertEquals(Duration.ofNanos(1045087), gs.averageSearchTime());
    }

    @Test
    void requireEqualDistributionInFlatWeightListWithAdaptiveScheduler() {
        List<GroupStatus> scoreboard = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            scoreboard.add(newGroupStatus(i));
        }
        Random seq = sequence(0.0, 0.1, 0.2, 0.39, 0.4, 0.6, 0.8, 0.99999);
        AdaptiveScheduler sched = new AdaptiveScheduler(seq, scoreboard);

        assertEquals(0, sched.takeNextGroup(null).get().groupId());
        assertEquals(0, sched.takeNextGroup(null).get().groupId());
        assertEquals(1, sched.takeNextGroup(null).get().groupId());
        assertEquals(1, sched.takeNextGroup(null).get().groupId());
        assertEquals(2, sched.takeNextGroup(null).get().groupId());
        assertEquals(3, sched.takeNextGroup(null).get().groupId());
        assertEquals(4, sched.takeNextGroup(null).get().groupId());
        assertEquals(4, sched.takeNextGroup(null).get().groupId());
    }

    @Test
    void requireThatAdaptiveSchedulerObeysWeights() {
        List<GroupStatus> scoreboard = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            GroupStatus gs = newGroupStatus(i);
            gs.setQueryStatistics(1, Duration.ofMillis((long)(0.1 * (i + 1)*1000.0)));
            scoreboard.add(gs);
        }
        Random seq = sequence(0.0, 0.4379, 0.4380, 0.6569, 0.6570, 0.8029, 0.8030, 0.9124, 0.9125);
        AdaptiveScheduler sched = new AdaptiveScheduler(seq, scoreboard);

        assertEquals(0, sched.takeNextGroup(null).get().groupId());
        assertEquals(0, sched.takeNextGroup(null).get().groupId());
        assertEquals(1, sched.takeNextGroup(null).get().groupId());
        assertEquals(1, sched.takeNextGroup(null).get().groupId());
        assertEquals(2, sched.takeNextGroup(null).get().groupId());
        assertEquals(2, sched.takeNextGroup(null).get().groupId());
        assertEquals(3, sched.takeNextGroup(null).get().groupId());
        assertEquals(3, sched.takeNextGroup(null).get().groupId());
        assertEquals(4, sched.takeNextGroup(null).get().groupId());
    }

    private static GroupStatus allocate(GroupStatus gs) {
        gs.allocate();
        return gs;
    }
    @Test
    void requireBestOfRandom2Scheduler() {
        List<GroupStatus> scoreboard = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            scoreboard.add(newGroupStatus(i));
        }
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
        BestOfRandom2 sched = new BestOfRandom2(seq, scoreboard);

        assertEquals(0, allocate(sched.takeNextGroup(null).get()).groupId());
        assertEquals(1, allocate(sched.takeNextGroup(null).get()).groupId());
        assertEquals(0, allocate(sched.takeNextGroup(null).get()).groupId());
        assertEquals(1, allocate(sched.takeNextGroup(null).get()).groupId());
        assertEquals(2, allocate(sched.takeNextGroup(null).get()).groupId());
        assertEquals(4, allocate(sched.takeNextGroup(null).get()).groupId());
        assertEquals(4, allocate(sched.takeNextGroup(null).get()).groupId());
        assertEquals(4, allocate(sched.takeNextGroup(null).get()).groupId());
        assertEquals(0, allocate(sched.takeNextGroup(null).get()).groupId());
    }

    private static void updateSearchTime(GroupStatus gs, Duration time) {
        gs.allocate();
        gs.release(true, time);
    }

    private GroupStatus newGroupStatus(int id) {
        Group dummyGroup = new Group(id, Collections.emptyList()) {
            @Override
            public boolean hasSufficientCoverage() {
                return true;
            }
        };
        return new GroupStatus(dummyGroup);
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
