// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * @author ollivir
 */
public class MockSearchCluster extends SearchCluster {
    private final int numGroups;
    private final int numNodesPerGroup;
    private final ImmutableMap<Integer, Group> groups;
    private final ImmutableMultimap<String, Node> nodesByHost;

    public MockSearchCluster(int groups, int nodesPerGroup) {
        super(100, Collections.emptyList(), null, 1, null);

        ImmutableMap.Builder<Integer, Group> groupBuilder = ImmutableMap.builder();
        ImmutableMultimap.Builder<String, Node> hostBuilder = ImmutableMultimap.builder();
        int dk = 1;
        for (int group = 0; group < groups; group++) {
            List<Node> nodes = new ArrayList<>();
            for (int node = 0; node < nodesPerGroup; node++) {
                Node n = new Node(dk, "host" + dk, -1, group);
                n.setWorking(true);
                nodes.add(n);
                hostBuilder.put(n.hostname(), n);
                dk++;
            }
            groupBuilder.put(group, new Group(group, nodes));
        }
        this.groups = groupBuilder.build();
        this.nodesByHost = hostBuilder.build();
        this.numGroups = groups;
        this.numNodesPerGroup = nodesPerGroup;
    }

    @Override
    public int size() {
        return numGroups * numNodesPerGroup;
    }

    public ImmutableMap<Integer, Group> groups() {
        return groups;
    }

    public int groupSize() {
        return numNodesPerGroup;
    }

    public Optional<Group> group(int n) {
        if(n < numGroups) {
            return Optional.of(groups.get(n));
        } else {
            return Optional.empty();
        }
    }

    public ImmutableMultimap<String, Node> nodesByHost() {
        return nodesByHost;
    }

    public Optional<Node> directDispatchTarget() {
        return Optional.empty();
    }

    public void working(Node node) {
        node.setWorking(true);
    }

    public void failed(Node node) {
        node.setWorking(false);
    }
}
