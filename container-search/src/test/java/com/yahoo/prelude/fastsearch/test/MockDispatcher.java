// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch.test;

import com.yahoo.container.handler.VipStatus;
import com.yahoo.prelude.fastsearch.FS4ResourcePool;
import com.yahoo.search.Result;
import com.yahoo.search.dispatch.Dispatcher;
import com.yahoo.search.dispatch.searchcluster.Node;
import com.yahoo.vespa.config.search.DispatchConfig;

import java.util.Collections;
import java.util.List;

class MockDispatcher extends Dispatcher {

    public MockDispatcher(Node node) {
        this(node.hostname(), Collections.singletonList(node));
    }

    public MockDispatcher(String clusterId, List<Node> nodes) {
        this(clusterId, nodes, new FS4ResourcePool("container.0", 1), 1, new VipStatus());
    }

    public MockDispatcher(String clusterId, List<Node> nodes, FS4ResourcePool fs4ResourcePool,
                          int containerClusterSize, VipStatus vipStatus) {
        super(clusterId, toDispatchConfig(nodes), fs4ResourcePool, containerClusterSize, vipStatus, new MockMetric());
    }

    private static DispatchConfig toDispatchConfig(List<Node> nodes) {
        DispatchConfig.Builder dispatchConfigBuilder = new DispatchConfig.Builder();
        int key = 0;
        for (Node node : nodes) {
            DispatchConfig.Node.Builder dispatchConfigNodeBuilder = new DispatchConfig.Node.Builder();
            dispatchConfigNodeBuilder.host(node.hostname());
            dispatchConfigNodeBuilder.fs4port(node.fs4port());
            dispatchConfigNodeBuilder.port(0); // Mandatory, but currently not used here
            dispatchConfigNodeBuilder.group(node.group());
            dispatchConfigNodeBuilder.key(key++); // not used
            dispatchConfigBuilder.node(dispatchConfigNodeBuilder);
        }
        return new DispatchConfig(dispatchConfigBuilder);
    }

    public void fill(Result result, String summaryClass) {
    }
}
