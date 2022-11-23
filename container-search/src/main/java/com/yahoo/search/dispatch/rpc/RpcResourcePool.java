// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch.rpc;

import com.google.common.collect.ImmutableMap;
import com.yahoo.search.dispatch.FillInvoker;
import com.yahoo.search.dispatch.rpc.Client.NodeConnection;
import com.yahoo.vespa.config.search.DispatchConfig;
import com.yahoo.vespa.config.search.DispatchNodesConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * RpcResourcePool constructs {@link FillInvoker} objects that communicate with content nodes over RPC. It also contains
 * the RPC connection pool.
 *
 * @author ollivir
 */
public class RpcResourcePool implements RpcConnectionPool, AutoCloseable {

    /** Connections to the search nodes this talks to, indexed by node id ("partid") */
    private final ImmutableMap<Integer, NodeConnectionPool> nodeConnectionPools;
    private final RpcClient rpcClient;

    RpcResourcePool(Map<Integer, NodeConnection> nodeConnections) {
        var builder = new ImmutableMap.Builder<Integer, NodeConnectionPool>();
        nodeConnections.forEach((key, connection) -> builder.put(key, new NodeConnectionPool(Collections.singletonList(connection))));
        this.nodeConnectionPools = builder.build();
        this.rpcClient = null;
    }

    public RpcResourcePool(DispatchConfig dispatchConfig, DispatchNodesConfig nodesConfig) {
        super();
        rpcClient = new RpcClient("dispatch-client", dispatchConfig.numJrtTransportThreads());

        // Create rpc node connection pools indexed by the node distribution key
        int numConnections = dispatchConfig.numJrtConnectionsPerNode();
        var builder = new ImmutableMap.Builder<Integer, NodeConnectionPool>();
        for (var node : nodesConfig.node()) {
            var connections = new ArrayList<NodeConnection>(numConnections);
            for (int i = 0; i < numConnections; i++) {
                connections.add(rpcClient.createConnection(node.host(), node.port()));
            }
            builder.put(node.key(), new NodeConnectionPool(connections));
        }
        this.nodeConnectionPools = builder.build();
    }

    @Override
    public NodeConnection getConnection(int nodeId) {
        var pool = nodeConnectionPools.get(nodeId);
        if (pool == null) {
            return null;
        } else {
            return pool.nextConnection();
        }
    }

    @Override
    public void close() {
        nodeConnectionPools.values().forEach(NodeConnectionPool::release);
        if (rpcClient != null) {
            rpcClient.close();
        }
    }

    private static class NodeConnectionPool {
        private final List<Client.NodeConnection> connections;

        NodeConnectionPool(List<NodeConnection> connections) {
            this.connections = connections;
        }

        Client.NodeConnection nextConnection() {
            int slot = ThreadLocalRandom.current().nextInt(connections.size());
            return connections.get(slot);
        }

        void release() {
            connections.forEach(Client.NodeConnection::close);
        }
    }

}
