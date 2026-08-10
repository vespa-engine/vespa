// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch;

import com.yahoo.search.dispatch.searchcluster.Group;
import com.yahoo.search.dispatch.searchcluster.Node;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author bratseth
 */
public class LoadBalancerTester {

    private final int requests = 100;
    private final LoadBalancer.Policy policy;

    public LoadBalancerTester(LoadBalancer.Policy policy) {
        this.policy = policy;
    }

    void assertAzAwareLoadBalancing() {
        Node n1 = new Node("test", 0, "test-node1", 0, true, "az1");
        Node n2 = new Node("test", 0, "test-node2", 1, true, "az1");
        Node n3 = new Node("test", 1, "test-node3", 2, true, "az2");
        Node n4 = new Node("test", 1, "test-node4", 3, true, "az2");
        Group g0 = new Group(0, List.of(n1));
        Group g1 = new Group(1, List.of(n2));
        Group g2 = new Group(2, List.of(n3));
        Group g3 = new Group(3, List.of(n4));

        g0.setHasSufficientCoverage(true);
        g1.setHasSufficientCoverage(true);
        g2.setHasSufficientCoverage(true);
        g3.setHasSufficientCoverage(true);
        assertLb(List.of(50, 50,  0,  0), loadBalance(List.of(g0, g1, g2, g3), "az1"));
        assertLb(List.of( 0,  0, 50, 50), loadBalance(List.of(g0, g1, g2, g3), "az2"));

        g0.setHasSufficientCoverage(false);
        g1.setHasSufficientCoverage(true);
        g2.setHasSufficientCoverage(true);
        g3.setHasSufficientCoverage(true);
        assertLb(List.of(0, 100,  0,  0), loadBalance(List.of(g0, g1, g2, g3), "az1"));
        assertLb(List.of(0,   0, 50, 50), loadBalance(List.of(g0, g1, g2, g3), "az2"));

        g0.setHasSufficientCoverage(false);
        g1.setHasSufficientCoverage(false);
        g2.setHasSufficientCoverage(true);
        g3.setHasSufficientCoverage(true);
        assertLb(List.of(0, 0, 50, 50), loadBalance(List.of(g0, g1, g2, g3), "az1"));
        assertLb(List.of(0, 0, 50, 50), loadBalance(List.of(g0, g1, g2, g3), "az2"));

        g0.setHasSufficientCoverage(false);
        g1.setHasSufficientCoverage(false);
        g2.setHasSufficientCoverage(false);
        g3.setHasSufficientCoverage(true);
        assertLb(List.of(0, 0, 0, 100), loadBalance(List.of(g0, g1, g2, g3), "az1"));
        assertLb(List.of(0, 0, 0, 100), loadBalance(List.of(g0, g1, g2, g3), "az2"));

        g0.setHasSufficientCoverage(false);
        g1.setHasSufficientCoverage(false);
        g2.setHasSufficientCoverage(false);
        g3.setHasSufficientCoverage(false);
        assertLb(List.of(50, 50,  0,  0), loadBalance(List.of(g0, g1, g2, g3), "az1"));
        assertLb(List.of( 0,  0, 50, 50), loadBalance(List.of(g0, g1, g2, g3), "az2"));

        // rejections

        g0.setHasSufficientCoverage(true);
        g1.setHasSufficientCoverage(true);
        g2.setHasSufficientCoverage(true);
        g3.setHasSufficientCoverage(true);
        assertLb(List.of(0, 100,  0,  0), loadBalance(List.of(g0, g1, g2, g3), List.of(g0), "az1"));
        assertLb(List.of(0,   0, 50, 50), loadBalance(List.of(g0, g1, g2, g3), List.of(g0, g1), "az1"));
        assertLb(List.of(0,   0,  0,  0), loadBalance(List.of(g0, g1, g2, g3), List.of(g0, g1, g2, g3), "az1"));

        g0.setHasSufficientCoverage(true);
        g1.setHasSufficientCoverage(true);
        g2.setHasSufficientCoverage(false);
        g3.setHasSufficientCoverage(false);
        assertLb(List.of(0, 0, 50, 50), loadBalance(List.of(g0, g1, g2, g3), List.of(g0, g1), "az1"));

        g0.setHasSufficientCoverage(false);
        g1.setHasSufficientCoverage(false);
        g2.setHasSufficientCoverage(false);
        g3.setHasSufficientCoverage(false);
        assertLb(List.of(0, 100,  0,  0), loadBalance(List.of(g0, g1, g2, g3), List.of(g0), "az1"));
        assertLb(List.of(0,   0, 50, 50), loadBalance(List.of(g0, g1, g2, g3), List.of(g0, g1), "az1"));
    }

    /**
     * Asserts that the expected number of requests per group matches the actual.
     * If the policy is round-robin, exaoct match is expected, otherwise this is ... smarter.
     */
    private void assertLb(List<Integer> expected, List<Integer> actual) {
        if ( policy == LoadBalancer.Policy.ROUNDROBIN) {
            assertEquals(expected, actual);
        }
        else {
            int totalRequests = 0;
            for (int i = 0; i < expected.size(); i++) {
                totalRequests+= actual.get(i);
                if (expected.get(i) == 0 || expected.get(i) == requests)
                    assertEquals(expected.get(i), actual.get(i), "Requests to group " + i);
                else
                    assertEquals(round(actual.get(i)), round(expected.get(i)), "Requests to group " + i + " (rounded)");
            }
            assertEquals(expected.stream().mapToInt(e -> e).sum(), totalRequests, "Total requests");
        }
    }

    private int round(int value) {
        return (int)Math.round(value / 5.0);
    }

    private List<Integer> loadBalance(List<Group> groups, String localAZ) {
        return loadBalance(groups, List.of(), localAZ);
    }

    private List<Integer> loadBalance(List<Group> groups, List<Group> rejected, String localAZ) {
        LoadBalancer lb = new LoadBalancer(groups, policy, localAZ, 1);
        List<Integer> requestCounts = new ArrayList<>(groups.size());
        for (int i = 0; i < groups.size(); i++)
            requestCounts.add(0);
        for (int i = 0; i < requests; i++) {
            Optional<Group> group = lb.takeAnyGroupNotIn(rejected.stream().map(Group::id).collect(Collectors.toSet()));
            if (group.isEmpty()) continue;
            requestCounts.set(group.get().id(), requestCounts.get(group.get().id()) + 1);
            lb.releaseGroup(group.get(), true, RequestDuration.of(Duration.ofMillis(1)));
        }
        return requestCounts;
    }

}
