// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch.rpc;

import com.yahoo.search.dispatch.FillInvoker;
import com.yahoo.search.dispatch.rpc.Client.NodeConnection;
import com.yahoo.search.dispatch.rpc.RpcClient.RpcNodeConnection;
import com.yahoo.vespa.config.search.DispatchConfig;
import com.yahoo.vespa.config.search.DispatchNodesConfig;
import com.yahoo.vespa.config.search.DispatchNodesConfig.Node;

import java.util.ArrayList;
import java.util.Collection;
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
public class RpcResourcePool implements RpcConnectionPool {

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
        rpcClient = new RpcClient("dispatch-client", dispatchConfig.numJrtTransportThreads());
        numConnections = dispatchConfig.numJrtConnectionsPerNode();
        updateNodes(nodesConfig).forEach(pool -> {
            try { pool.close(); } catch (Exception ignored) { } // Shouldn't throw.
        });
    }

    @Override
    public Collection<? extends AutoCloseable> updateNodes(DispatchNodesConfig nodesConfig) {
        Map<Integer, NodeConnectionPool> currentPools = new HashMap<>(nodeConnectionPools);
        Map<Integer, NodeConnectionPool> nextPools = new HashMap<>();
        // Who can be reused
        for (Node node : nodesConfig.node()) {
            if (   currentPools.containsKey(node.key())
                && currentPools.get(node.key()).nextConnection() instanceof RpcNodeConnection rpcNodeConnection
                && rpcNodeConnection.getPort() == node.port()
                && rpcNodeConnection.getHostname().equals(node.host()))
            {
                nextPools.put(node.key(), currentPools.remove(node.key()));
            } else {
                ArrayList<NodeConnection> connections = new ArrayList<>(numConnections);
                for (int i = 0; i < numConnections; i++) {
                    connections.add(rpcClient.createConnection(node.host(), node.port()));
                }
                nextPools.put(node.key(), new NodeConnectionPool(connections));
            }
        }
        this.nodeConnectionPools = Map.copyOf(nextPools);
        return currentPools.values();
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
        nodeConnectionPools.values().forEach(NodeConnectionPool::close);
        if (rpcClient != null) {
            rpcClient.close();
        }
    }

    private static class NodeConnectionPool implements AutoCloseable {
        private final List<Client.NodeConnection> connections;

        NodeConnectionPool(List<NodeConnection> connections) {
            this.connections = connections;
        }

        Client.NodeConnection nextConnection() {
            int slot = ThreadLocalRandom.current().nextInt(connections.size());
            return connections.get(slot);
        }

        public void close() {
            connections.forEach(Client.NodeConnection::close);
        }
    }

}
