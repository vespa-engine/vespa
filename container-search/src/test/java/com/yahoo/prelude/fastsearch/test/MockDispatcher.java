// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch.test;

import com.yahoo.container.handler.VipStatus;
import com.yahoo.prelude.fastsearch.FS4InvokerFactory;
import com.yahoo.prelude.fastsearch.FS4ResourcePool;
import com.yahoo.search.Result;
import com.yahoo.search.dispatch.Dispatcher;
import com.yahoo.search.dispatch.rpc.RpcInvokerFactory;
import com.yahoo.search.dispatch.rpc.RpcResourcePool;
import com.yahoo.search.dispatch.searchcluster.Node;
import com.yahoo.search.dispatch.searchcluster.SearchCluster;
import com.yahoo.vespa.config.search.DispatchConfig;

import java.util.List;

class MockDispatcher extends Dispatcher {
    public static MockDispatcher create(List<Node> nodes) {
        var fs4ResourcePool = new FS4ResourcePool("container.0", 1);
        var rpcResourcePool = new RpcResourcePool(toDispatchConfig(nodes));

        return create(nodes, fs4ResourcePool, rpcResourcePool, 1, new VipStatus());
    }

    public static MockDispatcher create(List<Node> nodes, FS4ResourcePool fs4ResourcePool, RpcResourcePool rpcResourcePool,
            int containerClusterSize, VipStatus vipStatus) {
        var dispatchConfig = toDispatchConfig(nodes);
        var searchCluster = new SearchCluster("a", dispatchConfig, containerClusterSize, vipStatus);
        return new MockDispatcher(searchCluster, dispatchConfig, fs4ResourcePool, rpcResourcePool);
    }

    private MockDispatcher(SearchCluster searchCluster, DispatchConfig dispatchConfig, FS4ResourcePool fs4ResourcePool,
            RpcResourcePool rpcResourcePool) {
        super(searchCluster, dispatchConfig, new FS4InvokerFactory(fs4ResourcePool, searchCluster),
                new RpcInvokerFactory(rpcResourcePool, searchCluster, dispatchConfig.dispatchWithProtobuf()), new MockMetric());
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
