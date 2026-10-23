// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch.rpc;

import ai.vespa.telemetry.api.trace.TraceAttributes;
import com.google.common.collect.ImmutableMap;
import com.yahoo.compress.CompressionType;
import com.yahoo.prelude.fastsearch.ClusterParams;
import com.yahoo.prelude.fastsearch.VespaBackend;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.dispatch.searchcluster.Node;
import com.yahoo.search.test.SpanRecorder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Verifies the L5 {@code node.search} CLIENT span created in {@link RpcSearchInvoker}, one per content node.
 *
 * <p>The span is carried on the invoker instance, which is already per-query-per-node and is already the object
 * handed to the transport layer as the {@code ResponseReceiver} — so no lookup structure is needed. It is created
 * on the worker thread in {@code sendSearchRequest}, before the two early returns, and ended by {@code receive}
 * when the transport reports an outcome.</p>
 *
 * <p>{@code release()} ends it on exactly the two paths that create the span and then return without sending
 * anything: an unknown node connection, and a timeout detected before sending. It is NOT what covers a node that
 * goes silent — JRT calls the response waiter exactly once for every request it accepts, firing
 * {@code InvocationClient.run()} on the scheduled timeout, so a silent node reaches {@code receive} with a
 * TIMEOUT error and {@code release()} then finds the span already ended. The tests below that use a connection
 * which never replies are therefore exercising {@code release()} as a BACKSTOP, not reproducing a production
 * scenario.</p>
 *
 * @author onur
 */
class NodeSearchTracingTest {

    private final CompressService compressor = new CompressService();

    @Test
    void a_reply_produces_one_client_span_under_the_dispatch_span() throws Exception {
        SpanRecorder recorder = SpanRecorder.underParentSpan("dispatch.search");
        RpcSearchInvoker invoker = invokerReplying(Client.ResponseOrError.fromResponse(
                new Client.ProtobufResponse(CompressionType.NONE.getCode(), 0, new byte[0])));

        recorder.record(() -> invoker.sendSearchRequest(new Query("?query=test"), 1.0, null));

        SpanData span = recorder.spanNamed("node.search");
        assertEquals(SpanKind.CLIENT, span.getKind(), "an outbound request to a remote service");
        assertEquals("ai.vespa", span.getInstrumentationScopeInfo().getName());
        assertEquals(recorder.parentSpan().getSpanContext().getSpanId(), span.getParentSpanId(),
                     "the node span must be a child of the dispatch span");
        assertEquals(StatusCode.UNSET, span.getStatus().getStatusCode(), "a delivered reply is not an error");

        recorder.close();
    }

    @Test
    void the_span_identifies_which_node_it_went_to() throws Exception {
        SpanRecorder recorder = SpanRecorder.underParentSpan("dispatch.search");
        RpcSearchInvoker invoker = invokerReplying(Client.ResponseOrError.fromError("whatever"));

        recorder.record(() -> invoker.sendSearchRequest(new Query("?query=test"), 1.0, null));

        var attrs = recorder.spanNamed("node.search").getAttributes();
        assertEquals(7L, attrs.get(TraceAttributes.CONTENT_NODE_KEY).longValue(), "the distribution key operators work with");
        assertEquals("seven", attrs.get(TraceAttributes.CONTENT_NODE_HOST));
        assertEquals(1L, attrs.get(TraceAttributes.CONTENT_GROUP).longValue());
        assertEquals("vespa_jrt", attrs.get(TraceAttributes.RPC_SYSTEM));
        assertEquals("vespa.searchprotocol.search", attrs.get(TraceAttributes.RPC_METHOD_KEY));

        recorder.close();
    }

    @Test
    void a_span_ended_without_a_reply_is_STILL_identifiable() throws Exception {
        // The attributes are set at send time, so a span ended by release() - an unknown node, or a pre-send
        // timeout - is still named even though nothing ever came back from it.
        SpanRecorder recorder = SpanRecorder.underParentSpan("dispatch.search");
        RpcSearchInvoker invoker = invokerReplying(null);

        recorder.record(() -> {
            invoker.sendSearchRequest(new Query("?query=test"), 1.0, null);
            invoker.release();
        });

        var attrs = recorder.spanNamed("node.search").getAttributes();
        assertEquals(7L, attrs.get(TraceAttributes.CONTENT_NODE_KEY).longValue());
        assertEquals("seven", attrs.get(TraceAttributes.CONTENT_NODE_HOST));

        recorder.close();
    }

    @Test
    void an_error_reply_marks_the_span_error_with_the_transport_message() throws Exception {
        SpanRecorder recorder = SpanRecorder.underParentSpan("dispatch.search");
        RpcSearchInvoker invoker = invokerReplying(Client.ResponseOrError.fromError("connection closed"));

        recorder.record(() -> invoker.sendSearchRequest(new Query("?query=test"), 1.0, null));

        SpanData span = recorder.spanNamed("node.search");
        assertEquals(StatusCode.ERROR, span.getStatus().getStatusCode());
        assertEquals("connection closed", span.getStatus().getDescription());

        recorder.close();
    }

    @Test
    void a_span_with_no_reply_is_ended_by_release_naming_the_node() throws Exception {
        SpanRecorder recorder = SpanRecorder.underParentSpan("dispatch.search");
        RpcSearchInvoker invoker = invokerReplying(null);   // a connection that never replies: release() is the backstop

        recorder.record(() -> {
            invoker.sendSearchRequest(new Query("?query=test"), 1.0, null);
            invoker.release();                              // as InterleavedSearchInvoker.release would
        });

        SpanData span = recorder.spanNamed("node.search");
        assertEquals(StatusCode.ERROR, span.getStatus().getStatusCode());
        assertTrue(span.getStatus().getDescription().startsWith("ended without a response"), "neutral wording: covers the unknown-node and pre-send-timeout paths alike");
        assertTrue(span.getStatus().getDescription().contains("7"), "names the distribution key");
        assertTrue(span.getStatus().getDescription().contains("seven"), "names the host");

        recorder.close();
    }

    @Test
    void release_after_a_reply_does_not_restamp_the_finished_span() throws Exception {
        SpanRecorder recorder = SpanRecorder.underParentSpan("dispatch.search");
        RpcSearchInvoker invoker = invokerReplying(Client.ResponseOrError.fromResponse(
                new Client.ProtobufResponse(CompressionType.NONE.getCode(), 0, new byte[0])));

        recorder.record(() -> {
            invoker.sendSearchRequest(new Query("?query=test"), 1.0, null);
            // release() also runs for invokers that DID answer: InterleavedSearchInvoker.ejectInvoker calls it
            // on every invoker it consumes. It must not turn a successful span into an error.
            invoker.release();
        });

        SpanData span = recorder.spanNamed("node.search");
        assertEquals(StatusCode.UNSET, span.getStatus().getStatusCode(),
                     "the first ending wins; release() must find it already ended and do nothing");

        recorder.close();
    }

    @Test
    void an_unknown_node_still_yields_exactly_one_span_ended_by_release() throws Exception {
        SpanRecorder recorder = SpanRecorder.underParentSpan("dispatch.search");
        // An empty pool: getConnection returns null, so sendSearchRequest takes the unknown-node early return.
        RpcSearchInvoker invoker = new RpcSearchInvoker(mockSearcher(), compressor, node(),
                                                        new RpcResourcePool(ImmutableMap.of()), 1000);

        recorder.record(() -> {
            invoker.sendSearchRequest(new Query("?query=test"), 1.0, null);
            invoker.release();
        });

        assertEquals(1, recorder.countSpans("node.search"), "one span per node, however the attempt ended");
        assertEquals(StatusCode.ERROR, recorder.spanNamed("node.search").getStatus().getStatusCode());

        recorder.close();
    }

    private static Node node() { return new Node("test", 7, "seven", 1, true); }

    /** An invoker whose connection hands {@code reply} straight back, or nothing at all when it is null. */
    private RpcSearchInvoker invokerReplying(Client.ResponseOrError<Client.ProtobufResponse> reply) {
        Client.NodeConnection connection = new Client.NodeConnection() {
            @Override
            public void request(String rpcMethod, CompressionType compression, int uncompressedLength,
                                byte[] compressedPayload, Client.ResponseReceiver responseReceiver, double timeoutSeconds) {
                if (reply != null) responseReceiver.receive(reply);
            }
            @Override public void close() { }
        };
        return new RpcSearchInvoker(mockSearcher(), compressor, node(),
                                    new RpcResourcePool(ImmutableMap.of(node().key(), connection)), 1000);
    }

    private VespaBackend mockSearcher() {
        return new VespaBackend(new ClusterParams("container.0")) {
            @Override protected Result doSearch2(String schema, Query query) { fail("Unexpected call"); return null; }
            @Override protected void doPartialFill(Result result, String summaryClass) { fail("Unexpected call"); }
        };
    }
}
