// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch.searchcluster;

public class SearchClusterTester {

    private final SearchCluster cluster;

    public SearchClusterTester(int groups, int nodesPerGroup) {
        cluster = new MockSearchCluster("1", groups, nodesPerGroup);
    }

    public SearchCluster cluster() { return cluster; }

    public void pingIterationCompleted() {
        cluster.pingIterationCompleted();
    }

    public Group group(int id) {
        return cluster.group(id);
    }

    public void setWorking(int group, int node, boolean working) {
        cluster.group(group).nodes().get(node).setWorking(working);
    }

    public void setDocsPerNode(int docs, int groupId) {
        for (Node node : cluster.group(groupId).nodes()) {
            node.setWorking(true);
            node.setActiveDocuments(docs);
        }
    }

}
