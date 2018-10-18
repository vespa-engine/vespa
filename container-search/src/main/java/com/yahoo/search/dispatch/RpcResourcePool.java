// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch;

import com.google.common.collect.ImmutableMap;
import com.yahoo.compress.Compressor;
import com.yahoo.prelude.fastsearch.DocumentDatabase;
import com.yahoo.prelude.fastsearch.VespaBackEndSearcher;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.Query;
import com.yahoo.vespa.config.search.DispatchConfig;

import java.util.Map;
import java.util.Optional;

/**
 * RpcResourcePool constructs {@link FillInvoker} objects that communicate with content nodes over RPC. It also contains
 * the RPC connection pool.
 *
 * @author ollivir
 */
public class RpcResourcePool {
    /** The compression method which will be used with rpc dispatch. "lz4" (default) and "none" is supported. */
    public final static CompoundName dispatchCompression = new CompoundName("dispatch.compression");

    /** Unless turned off this will fill summaries by dispatching directly to search nodes over RPC when possible */
    private final static CompoundName dispatchSummaries = new CompoundName("dispatch.summaries");

    private final Compressor compressor = new Compressor();
    private final Client client;

    /** Connections to the search nodes this talks to, indexed by node id ("partid") */
    private final ImmutableMap<Integer, Client.NodeConnection> nodeConnections;

    public RpcResourcePool(Client client, Map<Integer, Client.NodeConnection> nodeConnections) {
        this.client = client;
        this.nodeConnections = ImmutableMap.copyOf(nodeConnections);
    }

    public RpcResourcePool(DispatchConfig dispatchConfig) {
        this.client = new RpcClient();

        // Create node rpc connections, indexed by the node distribution key
        ImmutableMap.Builder<Integer, Client.NodeConnection> nodeConnectionsBuilder = new ImmutableMap.Builder<>();
        for (DispatchConfig.Node node : dispatchConfig.node()) {
            nodeConnectionsBuilder.put(node.key(), client.createConnection(node.host(), node.port()));
        }
        this.nodeConnections = nodeConnectionsBuilder.build();
    }

    public Optional<FillInvoker> getFillInvoker(Query query, VespaBackEndSearcher searcher, DocumentDatabase documentDb) {
        if (query.properties().getBoolean(dispatchSummaries, true)
            && ! searcher.summaryNeedsQuery(query)
            && query.getRanking().getLocation() == null
            && ! searcher.getCacheControl().useCache(query))
        {
            return Optional.of(new RpcFillInvoker(this, documentDb));
        } else {
            return Optional.empty();
        }
    }

    // for testing
    public FillInvoker getFillInvoker(DocumentDatabase documentDb) {
        return new RpcFillInvoker(this, documentDb);
    }

    public Compressor compressor() {
        return compressor;
    }

    public Client client() {
        return client;
    }

    public ImmutableMap<Integer, Client.NodeConnection> nodeConnections() {
        return nodeConnections;
    }

    public void release() {
        for (Client.NodeConnection nodeConnection : nodeConnections.values()) {
            nodeConnection.close();
        }
    }
}
