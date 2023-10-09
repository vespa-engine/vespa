// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch.rpc;

import com.yahoo.compress.Compressor;
import com.yahoo.prelude.fastsearch.VespaBackEndSearcher;
import com.yahoo.search.Query;
import com.yahoo.search.dispatch.InvokerResult;
import com.yahoo.search.dispatch.SearchInvoker;
import com.yahoo.search.dispatch.rpc.Client.ProtobufResponse;
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
public class RpcSearchInvoker extends SearchInvoker implements Client.ResponseReceiver {

    private static final String RPC_METHOD = "vespa.searchprotocol.search";

    private final VespaBackEndSearcher searcher;
    private final Node node;
    private final RpcConnectionPool resourcePool;
    private final BlockingQueue<Client.ResponseOrError<ProtobufResponse>> responses;
    private final int maxHits;
    private final CompressPayload compressor;

    private Query query;

    RpcSearchInvoker(VespaBackEndSearcher searcher, CompressPayload compressor, Node node, RpcConnectionPool resourcePool, int maxHits) {
        super(Optional.of(node));
        this.searcher = searcher;
        this.node = node;
        this.resourcePool = resourcePool;
        this.responses = new LinkedBlockingQueue<>(1);
        this.maxHits = maxHits;
        this.compressor = compressor;
    }

    @Override
    protected Object sendSearchRequest(Query query, Object incomingContext) {
        this.query = query;

        Client.NodeConnection nodeConnection = resourcePool.getConnection(node.key());
        if (nodeConnection == null) {
            responses.add(Client.ResponseOrError.fromError("Could not send search to unknown node " + node.key()));
            responseAvailable();
            return incomingContext;
        }
        query.trace(false, 5, "Sending search request with jrt/protobuf to node with dist key ", node.key());

        var timeout = TimeoutHelper.calculateTimeout(query);
        if (timeout.timedOut()) {
            // Need to produce an error response her in case of JVM system clock being adjusted
            // Timeout mechanism relies on System.currentTimeMillis(), not System.nanoTime() :(
            responses.add(Client.ResponseOrError.fromTimeoutError("Timeout before sending request to " + getName()));
            responseAvailable();
            return incomingContext;
        }
        RpcContext context = getContext(incomingContext, timeout.request());
        nodeConnection.request(RPC_METHOD,
                               context.compressedPayload.type(),
                               context.compressedPayload.uncompressedSize(),
                               context.compressedPayload.data(),
                               this,
                               timeout.client());
        return context;
    }

    private RpcContext getContext(Object incomingContext, double requestTimeout) {
        if (incomingContext instanceof RpcContext)
            return (RpcContext)incomingContext;

        return new RpcContext(compressor, query,
                              ProtobufSerialization.serializeSearchRequest(query,
                                                                           Math.min(query.getHits(), maxHits),
                                                                           searcher.getServerId(), requestTimeout));
    }

    @Override
    protected InvokerResult getSearchResult(Execution execution) throws IOException {
        long timeLeftMs = query.getTimeLeft();
        if (timeLeftMs <= 0) {
            return errorResult(query, ErrorMessage.createTimeout("Timeout while waiting for " + getName()));
        }
        Client.ResponseOrError<ProtobufResponse> response = null;
        try {
            response = responses.poll(timeLeftMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            // handled as timeout
        }
        if (response == null) {
            return errorResult(query, ErrorMessage.createTimeout("Timeout while waiting for " + getName()));
        }
        if (response.timeout()) {
            return errorResult(query, ErrorMessage.createTimeout(response.error().get()));
        }
        if (response.error().isPresent()) {
            return errorResult(query, ErrorMessage.createBackendCommunicationError(response.error().get()));
        }
        if (response.response().isEmpty()) {
            return errorResult(query, ErrorMessage.createInternalServerError("Neither error nor result available"));
        }

        ProtobufResponse protobufResponse = response.response().get();
        byte[] payload = compressor.decompress(protobufResponse);
        return ProtobufSerialization.deserializeToSearchResult(payload, query, searcher, node.pathIndex(), node.key());
    }

    @Override
    protected void release() {
        // nothing to release
    }

    public void receive(Client.ResponseOrError<ProtobufResponse> response) {
        responses.add(response);
        responseAvailable();
    }

    private String getName() {
        return searcher.getName();
    }

    static class RpcContext {

        final Compressor.Compression compressedPayload;

        RpcContext(CompressPayload compressor, Query query, byte[] payload) {
            compressedPayload = compressor.compress(query, payload);
        }

    }

}
