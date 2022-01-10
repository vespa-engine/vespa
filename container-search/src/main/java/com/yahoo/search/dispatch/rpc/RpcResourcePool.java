// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch.rpc;

import com.google.common.collect.ImmutableMap;
import com.yahoo.component.annotation.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.compress.CompressionType;
import com.yahoo.compress.Compressor;
import com.yahoo.compress.Compressor.Compression;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.Query;
import com.yahoo.search.dispatch.FillInvoker;
import com.yahoo.search.dispatch.rpc.Client.NodeConnection;
import com.yahoo.vespa.config.search.DispatchConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * RpcResourcePool constructs {@link FillInvoker} objects that communicate with content nodes over RPC. It also contains
 * the RPC connection pool.
 *
 * @author ollivir
 */
public class RpcResourcePool extends AbstractComponent {

    /** The compression method which will be used with rpc dispatch. "lz4" (default) and "none" is supported. */
    public final static CompoundName dispatchCompression = new CompoundName("dispatch.compression");

    private final Compressor compressor = new Compressor(CompressionType.LZ4, 5, 0.95, 32);
    private final Random random = new Random();

    /** Connections to the search nodes this talks to, indexed by node id ("partid") */
    private final ImmutableMap<Integer, NodeConnectionPool> nodeConnectionPools;
    private final RpcClient client;

    RpcResourcePool(Map<Integer, NodeConnection> nodeConnections) {
        var builder = new ImmutableMap.Builder<Integer, NodeConnectionPool>();
        nodeConnections.forEach((key, connection) -> builder.put(key, new NodeConnectionPool(Collections.singletonList(connection))));
        this.nodeConnectionPools = builder.build();
        client = null;
    }

    @Inject
    public RpcResourcePool(DispatchConfig dispatchConfig) {
        super();
        client = new RpcClient("dispatch-client", dispatchConfig.numJrtTransportThreads());

        // Create rpc node connection pools indexed by the node distribution key
        var builder = new ImmutableMap.Builder<Integer, NodeConnectionPool>();
        var numConnections = dispatchConfig.numJrtConnectionsPerNode();
        for (var node : dispatchConfig.node()) {
            var connections = new ArrayList<NodeConnection>(numConnections);
            for (int i = 0; i < numConnections; i++) {
                connections.add(client.createConnection(node.host(), node.port()));
            }
            builder.put(node.key(), new NodeConnectionPool(connections));
        }
        this.nodeConnectionPools = builder.build();
    }

    public Compressor compressor() {
        return compressor;
    }

    public Compression compress(Query query, byte[] payload) {
        CompressionType compression = CompressionType.valueOf(query.properties().getString(dispatchCompression, "LZ4").toUpperCase());
        return compressor.compress(compression, payload);
    }

    public NodeConnection getConnection(int nodeId) {
        var pool = nodeConnectionPools.get(nodeId);
        if (pool == null) {
            return null;
        } else {
            return pool.nextConnection();
        }
    }

    @Override
    public void deconstruct() {
        super.deconstruct();
        nodeConnectionPools.values().forEach(NodeConnectionPool::release);
        if (client != null) {
            client.close();
        }
    }

    private class NodeConnectionPool {
        private final List<Client.NodeConnection> connections;

        NodeConnectionPool(List<NodeConnection> connections) {
            this.connections = connections;
        }

        Client.NodeConnection nextConnection() {
            int slot = random.nextInt(connections.size());
            return connections.get(slot);
        }

        void release() {
            connections.forEach(Client.NodeConnection::close);
        }
    }

}
