// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch;

import ai.vespa.cloud.ApplicationId;
import ai.vespa.cloud.Cloud;
import ai.vespa.cloud.Cluster;
import ai.vespa.cloud.Environment;
import ai.vespa.cloud.SystemInfo;
import ai.vespa.cloud.Zone;
import com.yahoo.container.handler.VipStatus;
import com.yahoo.search.cluster.ClusterMonitor;
import com.yahoo.search.dispatch.rpc.RpcInvokerFactory;
import com.yahoo.search.dispatch.rpc.RpcPingFactory;
import com.yahoo.search.dispatch.rpc.RpcResourcePool;
import com.yahoo.search.dispatch.searchcluster.AvailabilityPolicy;
import com.yahoo.search.dispatch.searchcluster.Node;
import com.yahoo.search.dispatch.searchcluster.SearchCluster;
import com.yahoo.vespa.config.search.DispatchConfig;
import com.yahoo.vespa.config.search.DispatchNodesConfig;

import java.util.List;

public class MockDispatcher extends Dispatcher {

    public final ClusterMonitor clusterMonitor;

    public static MockDispatcher create(List<Node> nodes) {
        var rpcResourcePool = new RpcResourcePool(toDispatchConfig(), toNodesConfig(nodes));
        return create(nodes, rpcResourcePool, new VipStatus(), "default");
    }

    public static MockDispatcher create(List<Node> nodes,
                                        RpcResourcePool rpcResourcePool,
                                        VipStatus vipStatus,
                                        String localAvailabilityZone) {
        var dispatchConfig = toDispatchConfig();
        var searchCluster = new SearchCluster("a",
                                              AvailabilityPolicy.from(dispatchConfig),
                                              nodes,
                                              vipStatus,
                                              new RpcPingFactory(rpcResourcePool));
        var systemInfo = new SystemInfo(new ApplicationId("tenant1", "application1", "default"),
                                        new Zone(Environment.prod, "region1"),
                                        new Cloud("cloud1"),
                                        "cluster1",
                                        new ai.vespa.cloud.Node(0, localAvailabilityZone));
        return new MockDispatcher(new ClusterMonitor<>(searchCluster, true),
                                  searchCluster,
                                  dispatchConfig,
                                  systemInfo,
                                  rpcResourcePool);
    }

    private MockDispatcher(ClusterMonitor clusterMonitor,
                           SearchCluster searchCluster,
                           DispatchConfig dispatchConfig,
                           SystemInfo systemInfo,
                           RpcResourcePool rpcResourcePool) {
        this(clusterMonitor,
             searchCluster,
             dispatchConfig,
             systemInfo,
             new RpcInvokerFactory(rpcResourcePool, searchCluster.groupList(), dispatchConfig));
    }

    private MockDispatcher(ClusterMonitor clusterMonitor,
                           SearchCluster searchCluster,
                           DispatchConfig dispatchConfig,
                           SystemInfo systemInfo,
                           RpcInvokerFactory invokerFactory) {
        super(clusterMonitor,
              searchCluster,
              dispatchConfig,
              systemInfo,
              invokerFactory);
        this.clusterMonitor = clusterMonitor;
    }

    public static DispatchConfig toDispatchConfig() {
        return new DispatchConfig.Builder().build();
    }
    public static DispatchNodesConfig toNodesConfig(List<Node> nodes) {
        DispatchNodesConfig.Builder dispatchConfigBuilder = new DispatchNodesConfig.Builder();
        int key = 0;
        for (Node node : nodes) {
            DispatchNodesConfig.Node.Builder dispatchConfigNodeBuilder = new DispatchNodesConfig.Node.Builder();
            dispatchConfigNodeBuilder.host(node.hostname());
            dispatchConfigNodeBuilder.port(0); // Mandatory, but currently not used here
            dispatchConfigNodeBuilder.group(node.group());
            dispatchConfigNodeBuilder.key(key++); // not used
            dispatchConfigBuilder.node(dispatchConfigNodeBuilder);
        }
        return dispatchConfigBuilder.build();
    }

}
