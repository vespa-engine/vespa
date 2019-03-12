// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch.rpc;

import com.yahoo.compress.CompressionType;
import com.yahoo.compress.Compressor;
import com.yahoo.fs4.QueryPacket;
import com.yahoo.prelude.fastsearch.FastHit;
import com.yahoo.prelude.fastsearch.VespaBackEndSearcher;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.dispatch.SearchInvoker;
import com.yahoo.search.dispatch.rpc.Client.SearchResponse;
import com.yahoo.search.dispatch.searchcluster.Node;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.searchchain.Execution;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * {@link SearchInvoker} implementation using RPC
 *
 * @author ollivir
 */
public class RpcSearchInvoker extends SearchInvoker {
    private final VespaBackEndSearcher searcher;
    private final Node node;
    private final RpcResourcePool resourcePool;
    private final BlockingQueue<Client.SearchResponseOrError> responses;

    private Query query;

    RpcSearchInvoker(VespaBackEndSearcher searcher, Node node, RpcResourcePool resourcePool) {
        super(Optional.of(node));
        this.searcher = searcher;
        this.node = node;
        this.resourcePool = resourcePool;
        this.responses = new LinkedBlockingQueue<>(1);
    }

    @Override
    protected void sendSearchRequest(Query query, QueryPacket queryPacket) throws IOException {
        this.query = query;

        CompressionType compression = CompressionType
                .valueOf(query.properties().getString(RpcResourcePool.dispatchCompression, "LZ4").toUpperCase());

        Client.NodeConnection nodeConnection = resourcePool.nodeConnections().get(node.key());
        if (nodeConnection == null) {
            responses.add(Client.SearchResponseOrError.fromError("Could send search to unknown node " + node.key()));
            responseAvailable();
            return;
        }

        var payload = ProtobufSerialization.serializeQuery(query, searcher.getServerId(), true);
        double timeoutSeconds = ((double) query.getTimeLeft() - 3.0) / 1000.0;
        Compressor.Compression compressionResult = resourcePool.compressor().compress(compression, payload);
        resourcePool.client().search(nodeConnection, compressionResult.type(), payload.length, compressionResult.data(), this,
                timeoutSeconds);
    }

    @Override
    protected Result getSearchResult(Execution execution) throws IOException {
        long timeLeftMs = query.getTimeLeft();
        if (timeLeftMs <= 0) {
            return errorResult(query, ErrorMessage.createTimeout("Timeout while waiting for " + getName()));
        }
        Client.SearchResponseOrError response = null;
        try {
            response = responses.poll(timeLeftMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            // handled as timeout
        }
        if (response == null) {
            return errorResult(query, ErrorMessage.createTimeout("Timeout while waiting for " + getName()));
        }
        if (response.error().isPresent()) {
            return errorResult(query, ErrorMessage.createBackendCommunicationError(response.error().get()));
        }
        if (response.response().isEmpty()) {
            return errorResult(query, ErrorMessage.createInternalServerError("Neither error nor result available"));
        }

        SearchResponse searchResponse = response.response().get();
        CompressionType compression = CompressionType.valueOf(searchResponse.compression());
        byte[] payload = resourcePool.compressor().decompress(searchResponse.compressedPayload(), compression,
                searchResponse.uncompressedSize());
        var result = ProtobufSerialization.deserializeToResult(payload, query, searcher);
        result.hits().unorderedIterator().forEachRemaining(hit -> {
            if(hit instanceof FastHit) {
                FastHit fhit = (FastHit) hit;
                fhit.setPartId(node.pathIndex());
                fhit.setDistributionKey(node.key());
            }
            hit.setSource(getName());
        });

        return result;
    }

    @Override
    protected void release() {
        // nothing to release
    }

    public void receive(Client.SearchResponseOrError response) {
        responses.add(response);
        responseAvailable();
    }

    private String getName() {
        return searcher.getName();
    }

}
