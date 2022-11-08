// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch.test;

import com.yahoo.container.handler.VipStatus;
import com.yahoo.search.cluster.ClusterMonitor;
import com.yahoo.search.dispatch.Dispatcher;
import com.yahoo.search.dispatch.rpc.RpcInvokerFactory;
import com.yahoo.search.dispatch.rpc.RpcPingFactory;
import com.yahoo.search.dispatch.rpc.RpcResourcePool;
import com.yahoo.search.dispatch.searchcluster.Node;
import com.yahoo.search.dispatch.searchcluster.SearchCluster;
import com.yahoo.vespa.config.search.DispatchConfig;

import java.util.List;

class MockDispatcher extends Dispatcher {

    public final ClusterMonitor clusterMonitor;

    public static MockDispatcher create(List<Node> nodes) {
        var rpcResourcePool = new RpcResourcePool(toDispatchConfig(nodes));

        return create(nodes, rpcResourcePool, new VipStatus());
    }

    public static MockDispatcher create(List<Node> nodes, RpcResourcePool rpcResourcePool, VipStatus vipStatus) {
        var dispatchConfig = toDispatchConfig(nodes);
        var searchCluster = new SearchCluster("a", dispatchConfig, vipStatus, new RpcPingFactory(rpcResourcePool));
        return new MockDispatcher(new ClusterMonitor<>(searchCluster, true), searchCluster, dispatchConfig, rpcResourcePool);
    }

    private MockDispatcher(ClusterMonitor clusterMonitor, SearchCluster searchCluster, DispatchConfig dispatchConfig, RpcResourcePool rpcResourcePool) {
        this(clusterMonitor, searchCluster, dispatchConfig, new RpcInvokerFactory(rpcResourcePool, searchCluster));
    }

    private MockDispatcher(ClusterMonitor clusterMonitor, SearchCluster searchCluster, DispatchConfig dispatchConfig, RpcInvokerFactory invokerFactory) {
        super(clusterMonitor, searchCluster, dispatchConfig, invokerFactory);
        this.clusterMonitor = clusterMonitor;
    }

    static DispatchConfig toDispatchConfig(List<Node> nodes) {
        DispatchConfig.Builder dispatchConfigBuilder = new DispatchConfig.Builder();
        int key = 0;
        for (Node node : nodes) {
            DispatchConfig.Node.Builder dispatchConfigNodeBuilder = new DispatchConfig.Node.Builder();
            dispatchConfigNodeBuilder.host(node.hostname());
            dispatchConfigNodeBuilder.port(0); // Mandatory, but currently not used here
            dispatchConfigNodeBuilder.group(node.group());
            dispatchConfigNodeBuilder.key(key++); // not used
            dispatchConfigBuilder.node(dispatchConfigNodeBuilder);
        }
        return new DispatchConfig(dispatchConfigBuilder);
    }

}
