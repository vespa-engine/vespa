// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch;

import com.yahoo.search.dispatch.LoadBalancer.AdaptiveScheduler;
import com.yahoo.search.dispatch.LoadBalancer.GroupStatus;
import com.yahoo.search.dispatch.searchcluster.Group;
import com.yahoo.search.dispatch.searchcluster.Node;
import com.yahoo.search.dispatch.searchcluster.SearchCluster;
import junit.framework.AssertionFailedError;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static com.yahoo.search.dispatch.MockSearchCluster.createDispatchConfig;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author ollivir
 */
public class LoadBalancerTest {

    @Test
    public void requireThatLoadBalancerServesSingleNodeSetups() {
        Node n1 = new Node(0, "test-node1", 0);
        SearchCluster cluster = new SearchCluster("a", createDispatchConfig(n1), null, null);
        LoadBalancer lb = new LoadBalancer(cluster, true);

        Optional<Group> grp = lb.takeGroup(null);
        Group group = grp.orElseGet(() -> {
            throw new AssertionFailedError("Expected a SearchCluster.Group");
        });
        assertEquals(1, group.nodes().size());
    }

    @Test
    public void requireThatLoadBalancerServesMultiGroupSetups() {
        Node n1 = new Node(0, "test-node1", 0);
        Node n2 = new Node(1, "test-node2", 1);
        SearchCluster cluster = new SearchCluster("a", createDispatchConfig(n1, n2), null, null);
        LoadBalancer lb = new LoadBalancer(cluster, true);

        Optional<Group> grp = lb.takeGroup(null);
        Group group = grp.orElseGet(() -> {
            throw new AssertionFailedError("Expected a SearchCluster.Group");
        });
        assertEquals(1, group.nodes().size());
    }

    @Test
    public void requireThatLoadBalancerServesClusteredGroups() {
        Node n1 = new Node(0, "test-node1", 0);
        Node n2 = new Node(1, "test-node2", 0);
        Node n3 = new Node(0, "test-node3", 1);
        Node n4 = new Node(1, "test-node4", 1);
        SearchCluster cluster = new SearchCluster("a", createDispatchConfig(n1, n2, n3, n4), null, null);
        LoadBalancer lb = new LoadBalancer(cluster, true);

        Optional<Group> grp = lb.takeGroup(null);
        assertTrue(grp.isPresent());
    }

    @Test
    public void requireThatLoadBalancerReturnsDifferentGroups() {
        Node n1 = new Node(0, "test-node1", 0);
        Node n2 = new Node(1, "test-node2", 1);
        SearchCluster cluster = new SearchCluster("a", createDispatchConfig(n1, n2), null,null);
        LoadBalancer lb = new LoadBalancer(cluster, true);

        // get first group
        Optional<Group> grp = lb.takeGroup(null);
        Group group = grp.get();
        int id1 = group.id();
        // release allocation
        lb.releaseGroup(group, true, 1.0);

        // get second group
        grp = lb.takeGroup(null);
        group = grp.get();
        assertNotEquals(id1, group.id());
    }

    @Test
    public void requireCorrectAverageSearchTimeDecay() {
        final double delta = 0.00001;

        GroupStatus gs = newGroupStatus(1);
        gs.setQueryStatistics(0, 1.0);
        updateSearchTime(gs, 1.0);
        assertEquals(1.0, gs.averageSearchTime(), delta);
        updateSearchTime(gs, 2.0);
        assertEquals(1.02326, gs.averageSearchTime(), delta);
        updateSearchTime(gs, 2.0);
        assertEquals(1.04545, gs.averageSearchTime(), delta);
        updateSearchTime(gs, 0.1);
        updateSearchTime(gs, 0.1);
        updateSearchTime(gs, 0.1);
        updateSearchTime(gs, 0.1);
        assertEquals(0.966667, gs.averageSearchTime(), delta);
        for (int i = 0; i < 10000; i++) {
            updateSearchTime(gs, 1.0);
        }
        assertEquals(1.0, gs.averageSearchTime(), delta);
        updateSearchTime(gs, 0.1);
        assertEquals(0.9991, gs.averageSearchTime(), delta);
        for (int i = 0; i < 10000; i++) {
            updateSearchTime(gs, 0.0);
        }
        assertEquals(0.001045, gs.averageSearchTime(), delta);
    }

    @Test
    public void requireEqualDistributionInFlatWeightListWithAdaptiveScheduler() {
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
    public void requireThatAdaptiveSchedulerObeysWeights() {
        List<GroupStatus> scoreboard = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            GroupStatus gs = newGroupStatus(i);
            gs.setQueryStatistics(1, 0.1 * (i + 1));
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

    private static void updateSearchTime(GroupStatus gs, double time) {
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
        };
    }

}
