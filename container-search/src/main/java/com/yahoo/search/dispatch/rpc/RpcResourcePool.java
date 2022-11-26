// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch.rpc;

import com.yahoo.search.dispatch.FillInvoker;
import com.yahoo.search.dispatch.rpc.Client.NodeConnection;
import com.yahoo.vespa.config.search.DispatchConfig;
import com.yahoo.vespa.config.search.DispatchNodesConfig;

import java.util.ArrayList;
import java.util.HashMap;
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
    private volatile Map<Integer, NodeConnectionPool> nodeConnectionPools = Map.of();
    private final int numConnections;
    private final RpcClient rpcClient;

    RpcResourcePool(Map<Integer, NodeConnection> nodeConnections) {
        var builder = new HashMap<Integer, NodeConnectionPool>();
        nodeConnections.forEach((key, connection) -> builder.put(key, new NodeConnectionPool(List.of(connection))));
        this.nodeConnectionPools = Map.copyOf(builder);
        this.rpcClient = null;
        this.numConnections = 1;
    }

    public RpcResourcePool(DispatchConfig dispatchConfig, DispatchNodesConfig nodesConfig) {
        super();
        rpcClient = new RpcClient("dispatch-client", dispatchConfig.numJrtTransportThreads());
        numConnections = dispatchConfig.numJrtConnectionsPerNode();
        updateNodes(nodesConfig);
    }

    public void updateNodes(DispatchNodesConfig nodesConfig) {
        var builder = new HashMap<Integer, NodeConnectionPool>();
        for (var node : nodesConfig.node()) {
            var prev = nodeConnectionPools.get(node.key());
            NodeConnection nc = prev != null ? prev.nextConnection() : null;
            if (nc instanceof RpcClient.RpcNodeConnection rpcNodeConnection
                    && rpcNodeConnection.getPort() == node.port()
                    && rpcNodeConnection.getHostname().equals(node.host()))
            {
                builder.put(node.key(), prev);
            } else {
                if (prev != null) prev.release();
                var connections = new ArrayList<NodeConnection>(numConnections);
                for (int i = 0; i < numConnections; i++) {
                    connections.add(rpcClient.createConnection(node.host(), node.port()));
                }
                builder.put(node.key(), new NodeConnectionPool(connections));
            }
        }
        this.nodeConnectionPools = Map.copyOf(builder);
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
