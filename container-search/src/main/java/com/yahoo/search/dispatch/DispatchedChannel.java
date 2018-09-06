package com.yahoo.search.dispatch;

import com.yahoo.prelude.fastsearch.FS4ResourcePool;
import com.yahoo.search.dispatch.SearchCluster.Group;
import com.yahoo.search.dispatch.SearchCluster.Node;

import java.util.Optional;

public class DispatchedChannel extends CloseableChannel {
    private final SearchCluster.Group group;
    private final LoadBalancer loadBalancer;
    private boolean groupAllocated = true;

    public DispatchedChannel(FS4ResourcePool fs4ResourcePool, LoadBalancer loadBalancer, Group group, Node node) {
        super(fs4ResourcePool.getBackend(node.hostname(), node.fs4port(), Optional.of(node.key())));

        this.loadBalancer = loadBalancer;
        this.group = group;
    }

    public DispatchedChannel(FS4ResourcePool fs4ResourcePool, LoadBalancer loadBalancer, Group group) {
        this(fs4ResourcePool, loadBalancer, group, group.nodes().iterator().next());
    }

    public void close() {
        if (groupAllocated) {
            groupAllocated = false;
            loadBalancer.releaseGroup(group);
        }
        super.close();
    }
}
