// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch;

import com.yahoo.search.dispatch.SearchCluster.Group;
import com.yahoo.search.dispatch.SearchCluster.Node;
import junit.framework.AssertionFailedError;
import org.hamcrest.Matchers;
import org.junit.Test;

import java.util.Arrays;
import java.util.Optional;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class LoadBalancerTest {
    @Test
    public void requreThatLoadBalancerServesSingleNodeSetups() {
        Node n1 = new SearchCluster.Node(0, "test-node1", 0, 0);
        SearchCluster cluster = new SearchCluster(88.0, Arrays.asList(n1), null, 1, null);
        LoadBalancer lb = new LoadBalancer(cluster);

        Optional<Group> grp = lb.getGroupForQuery(null);
        Group group = grp.orElseGet(() -> {
            throw new AssertionFailedError("Expected a SearchCluster.Group");
        });
        assertThat(group.nodes().size(), Matchers.equalTo(1));
    }

    @Test
    public void requreThatLoadBalancerIgnoresMultiGroupSetups() {
        Node n1 = new SearchCluster.Node(0, "test-node1", 0, 0);
        Node n2 = new SearchCluster.Node(1, "test-node2", 1, 1);
        SearchCluster cluster = new SearchCluster(88.0, Arrays.asList(n1, n2), null, 1, null);
        LoadBalancer lb = new LoadBalancer(cluster);

        Optional<Group> grp = lb.getGroupForQuery(null);
        assertThat(grp.isPresent(), is(false));
    }

    @Test
    public void requreThatLoadBalancerIgnoresClusteredSingleGroup() {
        Node n1 = new SearchCluster.Node(0, "test-node1", 0, 0);
        Node n2 = new SearchCluster.Node(1, "test-node2", 1, 0);
        SearchCluster cluster = new SearchCluster(88.0, Arrays.asList(n1, n2), null, 2, null);
        LoadBalancer lb = new LoadBalancer(cluster);

        Optional<Group> grp = lb.getGroupForQuery(null);
        assertThat(grp.isPresent(), is(false));
    }

    @Test
    public void requreThatLoadBalancerIgnoresClusteredGroups() {
        Node n1 = new SearchCluster.Node(0, "test-node1", 0, 0);
        Node n2 = new SearchCluster.Node(1, "test-node2", 1, 0);
        Node n3 = new SearchCluster.Node(0, "test-node3", 0, 1);
        Node n4 = new SearchCluster.Node(1, "test-node4", 1, 1);
        SearchCluster cluster = new SearchCluster(88.0, Arrays.asList(n1, n2, n3, n4), null, 2, null);
        LoadBalancer lb = new LoadBalancer(cluster);

        Optional<Group> grp = lb.getGroupForQuery(null);
        assertThat(grp.isPresent(), is(false));
    }
}
