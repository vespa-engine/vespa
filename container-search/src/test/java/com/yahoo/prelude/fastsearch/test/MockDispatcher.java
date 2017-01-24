package com.yahoo.prelude.fastsearch.test;

import com.yahoo.container.handler.VipStatus;
import com.yahoo.prelude.fastsearch.FS4ResourcePool;
import com.yahoo.search.Result;
import com.yahoo.search.dispatch.Dispatcher;
import com.yahoo.search.dispatch.SearchCluster;
import com.yahoo.vespa.config.search.DispatchConfig;

import java.util.Collections;
import java.util.List;

class MockDispatcher extends Dispatcher {

    public MockDispatcher(SearchCluster.Node node) {
        this(Collections.singletonList(node));
    }

    public MockDispatcher(List<SearchCluster.Node> nodes) {
        super(toDispatchConfig(nodes), new FS4ResourcePool(1), 1, new VipStatus());
    }

    public MockDispatcher(List<SearchCluster.Node> nodes, VipStatus vipStatus) {
        super(toDispatchConfig(nodes), new FS4ResourcePool(1), 1, vipStatus);
    }

    public MockDispatcher(List<SearchCluster.Node> nodes, FS4ResourcePool fs4ResourcePool, 
                          int containerClusterSize, VipStatus vipStatus) {
        super(toDispatchConfig(nodes), fs4ResourcePool, containerClusterSize, vipStatus);
    }

    private static DispatchConfig toDispatchConfig(List<SearchCluster.Node> nodes) {
        DispatchConfig.Builder dispatchConfigBuilder = new DispatchConfig.Builder();
        int key = 0;
        for (SearchCluster.Node node : nodes) {
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
