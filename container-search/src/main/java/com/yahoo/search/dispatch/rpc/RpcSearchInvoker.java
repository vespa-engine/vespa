// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch.rpc;

import ai.vespa.telemetry.api.trace.TraceAttributes;
import ai.vespa.telemetry.api.trace.OtelTracing;
import com.yahoo.compress.Compressor;
import com.yahoo.prelude.fastsearch.VespaBackend;
import com.yahoo.search.Query;
import com.yahoo.search.dispatch.InvokerResult;
import com.yahoo.search.dispatch.SearchInvoker;
import com.yahoo.search.dispatch.rpc.Client.ProtobufResponse;
import com.yahoo.search.dispatch.searchcluster.Node;
import com.yahoo.search.result.ErrorMessage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;


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

    private final VespaBackend searcher;
    private final Node node;
    private final RpcConnectionPool resourcePool;
    private final BlockingQueue<Client.ResponseOrError<ProtobufResponse>> responses;
    private final int maxHits;
    private final CompressPayload compressor;

    private Query query;

    /** This invoker is already per-query-per-node, so it is the carrier: no lookup structure is needed.
     *  Created on the worker thread, ended on whichever thread learns the outcome — Span is thread-safe. */
    private Span span = Span.getInvalid();

    RpcSearchInvoker(VespaBackend searcher, CompressPayload compressor, Node node, RpcConnectionPool resourcePool, int maxHits) {
        super(Optional.of(node));
        this.searcher = searcher;
        this.node = node;
        this.resourcePool = resourcePool;
        this.responses = new LinkedBlockingQueue<>(1);
        this.maxHits = maxHits;
        this.compressor = compressor;
    }

    @Override
    protected Object sendSearchRequest(Query query, double contentShare, Object incomingContext) {
        this.query = query;
        span = OtelTracing.startSpan(Context.current(), "node.search", SpanKind.CLIENT);
        // Set on the span FIELD, not Span.current(): this span is deliberately never made current. Every value
        // here is a field read or a constant, so no isRecording() guard is warranted.
        span.setAttribute(TraceAttributes.CONTENT_CLUSTER,   searcher.getName())
            .setAttribute(TraceAttributes.CONTENT_NODE_KEY,  node.key())
            .setAttribute(TraceAttributes.CONTENT_NODE_HOST, node.hostname())
            .setAttribute(TraceAttributes.CONTENT_GROUP,     node.group())
            .setAttribute(TraceAttributes.RPC_SYSTEM,        "vespa_jrt")
            .setAttribute(TraceAttributes.RPC_METHOD_KEY,    RPC_METHOD);

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
        SerializedQuery serializedQuery = getSerializedQuery(incomingContext, contentShare, timeout.request());
        nodeConnection.request(RPC_METHOD,
                               serializedQuery.compressedPayload.type(),
                               serializedQuery.compressedPayload.uncompressedSize(),
                               serializedQuery.compressedPayload.data(),
                               this,
                               timeout.client());
        return serializedQuery;
    }

    @Override
    protected InvokerResult getSearchResult() throws IOException {
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
        return ProtobufSerialization.deserializeToSearchResult(payload, query, searcher, node);
    }

    @Override
    protected void release() {
        // Ends the span on the two paths above that create it and then return WITHOUT sending anything: an
        // unknown node connection, and a timeout detected before sending. Those never reach the transport, so
        // receive() is never called for them and nothing else would end their span.
        //
        // It is NOT what covers a node that goes silent. JRT guarantees the response waiter is called exactly
        // once for every request it accepts - InvocationClient.run() fires on the scheduled timeout and calls
        // handleRequestDone - so a silent node still reaches receive(), with a TIMEOUT error. This method is
        // then a no-op for it, since the span has already ended.
        //
        // Ends late - release() runs from close(), after the search has returned - which is accepted: on the
        // paths it actually covers, no request was ever sent, so there is no round trip being mis-measured.
        endSpan("ended without a response from node " + node.key() + " on " + node.hostname());
    }

    /**
     * Ends the span once, whichever path gets here first. Idempotent by construction: {@code isRecording()}
     * is false once a span has ended (SdkSpan:586-590) and a second {@code end()} is a logged no-op
     * (SdkSpan:559-564). That matters because release() also runs for invokers that DID answer —
     * InterleavedSearchInvoker.ejectInvoker calls it on every invoker it consumes — and must not restamp
     * a finished span with an error.
     */
    private void endSpan(String error) {
        if ( ! span.isRecording()) return;
        if (error != null) span.setStatus(StatusCode.ERROR, error);
        span.end();
    }

    public void receive(Client.ResponseOrError<ProtobufResponse> response) {
        endSpan(response.error().orElse(null));   // may run on a JRT transport thread
        responses.add(response);
        responseAvailable();
    }

    private String getName() {
        return searcher.getName();
    }

    private SerializedQuery getSerializedQuery(Object incomingContext, double contentShare, double requestTimeout) {
        if (incomingContext instanceof SerializedQuery serializedQuery
            && newSerializationWillBeSimilar(contentShare, serializedQuery))
            return serializedQuery;
        return new SerializedQuery(compressor, query, contentShare,
                                   ProtobufSerialization.serializeSearchRequest(query,
                                                                                Math.min(query.getHits(), maxHits),
                                                                                searcher.getServerId(), contentShare,
                                                                                requestTimeout));
    }

    private boolean newSerializationWillBeSimilar(double newContentShare, SerializedQuery serializedQuery) {
        double maxContentShare = Math.max(newContentShare, serializedQuery.contentShare);
        if (maxContentShare == 0) return true;
        return Math.abs(newContentShare - serializedQuery.contentShare) / maxContentShare < 0.05;
    }

    static class SerializedQuery {

        final double contentShare;
        final Compressor.Compression compressedPayload;

        SerializedQuery(CompressPayload compressor, Query query, double contentShare, byte[] payload) {
            this.contentShare = contentShare;
            compressedPayload = compressor.compress(query, payload);
        }

    }

}
