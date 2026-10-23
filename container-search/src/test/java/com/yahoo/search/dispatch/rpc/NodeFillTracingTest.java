// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch.rpc;

import ai.vespa.telemetry.api.trace.TraceAttributes;
import com.yahoo.compress.CompressionType;
import com.yahoo.prelude.fastsearch.DocumentDatabase;
import com.yahoo.prelude.fastsearch.FastHit;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.schema.DocumentSummary;
import com.yahoo.search.schema.Schema;
import com.yahoo.search.test.SpanRecorder;
import com.yahoo.slime.BinaryFormat;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the L6 {@code node.fill} CLIENT span created in {@link RpcProtobufFillInvoker#sendDocsumsRequest},
 * one per docsum request that actually goes out on the wire.
 *
 * <p>Unlike the search path there is no per-node invoker object to hold the span on: a single
 * {@code RpcProtobufFillInvoker} fans out to every node that owns hits, and the response receiver handed to JRT
 * is a lambda. The span is therefore CAPTURED by that lambda. That also handles the retry round for free, since
 * {@code maybeRetry} re-enters the same method and each invocation gets its own span - anything keyed by node id
 * would have had its first-wave span overwritten.</p>
 *
 * <p>The two paths that send nothing - an unknown node, and a timeout detected before sending - deliberately
 * create no span, because {@code node.fill} is CLIENT-kind and represents a request that was really made.</p>
 *
 * @author onur
 */
class NodeFillTracingTest {

    @Test
    void one_client_span_per_node_that_is_asked() throws Exception {
        SpanRecorder recorder = SpanRecorder.underParentSpan("cluster.fill");
        RpcProtobufFillInvoker invoker = invokerOver(Map.of(0, replyingWithError("nope"),
                                                            3, replyingWithError("nope")));

        recorder.record(() -> invoker.fill(resultWithHitsOn(0, 3), "default"));

        assertEquals(2, recorder.countSpans("node.fill"), "one per node holding hits");
        SpanData span = recorder.spans().stream().filter(s -> s.getName().equals("node.fill")).findFirst().orElseThrow();
        assertEquals(SpanKind.CLIENT, span.getKind(), "an outbound request to a remote service");
        assertEquals("ai.vespa", span.getInstrumentationScopeInfo().getName());

        recorder.close();
    }

    @Test
    void the_node_span_hangs_under_the_dispatch_fill_span() throws Exception {
        SpanRecorder recorder = SpanRecorder.underParentSpan("cluster.fill");
        RpcProtobufFillInvoker invoker = invokerOver(Map.of(0, replyingWithError("nope")));

        recorder.record(() -> invoker.fill(resultWithHitsOn(0), "default"));

        assertEquals(recorder.spanNamed("dispatch.fill").getSpanId(),
                     recorder.spanNamed("node.fill").getParentSpanId());

        recorder.close();
    }

    @Test
    void a_transport_error_marks_the_span_error_with_the_message() throws Exception {
        SpanRecorder recorder = SpanRecorder.underParentSpan("cluster.fill");
        RpcProtobufFillInvoker invoker = invokerOver(Map.of(0, replyingWithError("connection closed")));

        recorder.record(() -> invoker.fill(resultWithHitsOn(0), "default"));

        SpanData span = recorder.spanNamed("node.fill");
        assertEquals(StatusCode.ERROR, span.getStatus().getStatusCode());
        assertEquals("connection closed", span.getStatus().getDescription());

        recorder.close();
    }

    @Test
    void a_delivered_reply_is_not_an_error() throws Exception {
        SpanRecorder recorder = SpanRecorder.underParentSpan("cluster.fill");
        RpcProtobufFillInvoker invoker = invokerOver(Map.of(0, replyingWith(emptyDocsumReply())));

        recorder.record(() -> invoker.fill(resultWithHitsOn(0), "default"));

        assertEquals(StatusCode.UNSET, recorder.spanNamed("node.fill").getStatus().getStatusCode());

        recorder.close();
    }

    @Test
    void an_unknown_node_produces_no_span_because_nothing_was_sent() throws Exception {
        SpanRecorder recorder = SpanRecorder.underParentSpan("cluster.fill");
        RpcProtobufFillInvoker invoker = invokerOver(Map.of());   // empty pool: getConnection returns null

        recorder.record(() -> invoker.fill(resultWithHitsOn(0), "default"));

        assertEquals(0, recorder.countSpans("node.fill"),
                     "node.fill is CLIENT-kind: no request went out, so there is no client span to report");
        assertEquals(1, recorder.countSpans("dispatch.fill"), "the dispatch span still covers the attempt");

        recorder.close();
    }

    @Test
    void nothing_is_recorded_when_telemetry_is_absent() throws Exception {
        SpanRecorder recorder = SpanRecorder.underParentSpan("cluster.fill");
        RpcProtobufFillInvoker invoker = invokerOver(Map.of(0, replyingWithError("nope")));

        invoker.fill(resultWithHitsOn(0), "default");    // outside record(): no telemetry in the context

        assertEquals(0, recorder.countSpans("node.fill"));

        recorder.close();
    }

    @Test
    void the_retry_wave_produces_its_own_spans_marked_as_retries() throws Exception {
        // A node answering with an EMPTY docsum is what a redistribution looks like from the container. Vespa
        // does not know where the document moved to, so it re-asks every OTHER known node - maybeRetry:346.
        SpanRecorder recorder = SpanRecorder.underParentSpan("cluster.fill");
        RpcProtobufFillInvoker invoker = invokerOver(Map.of(0, replyingWith(emptyDocsumsFor(1)),
                                                            1, replyingWith(emptyDocsumsFor(1))));

        recorder.record(() -> invoker.fill(resultWithHitsOn(0), "default"));

        List<SpanData> nodeSpans = recorder.spans().stream().filter(s -> s.getName().equals("node.fill")).toList();
        assertEquals(2, nodeSpans.size(), "one span for the first wave to node 0, one for the retry to node 1");
        assertEquals(List.of(false, true), nodeSpans.stream().map(s -> s.getAttributes().get(TraceAttributes.FILL_RETRY)).toList(),
                     "the first wave must NOT be marked a retry and the second must be - this pins the invariant " +
                     "the derived flag relies on: a bucket whose hits do not belong to its node is a retry");

        recorder.close();
    }

    @Test
    void the_dispatch_span_records_that_a_retry_happened_and_how_wide_it_went() throws Exception {
        SpanRecorder recorder = SpanRecorder.underParentSpan("cluster.fill");
        RpcProtobufFillInvoker invoker = invokerOver(Map.of(0, replyingWith(emptyDocsumsFor(1)),
                                                            1, replyingWith(emptyDocsumsFor(1))));

        recorder.record(() -> invoker.fill(resultWithHitsOn(0), "default"));

        var attrs = recorder.spanNamed("dispatch.fill").getAttributes();
        assertEquals(1L, attrs.get(TraceAttributes.FILL_SKIPPED).longValue(), "one hit came back with no docsum");
        assertEquals(true, attrs.get(TraceAttributes.FILL_RETRIED));
        assertEquals(1L, attrs.get(TraceAttributes.FILL_RETRY_NODES).longValue(), "every OTHER known node: node 1 only");
        assertEquals(1L, attrs.get(TraceAttributes.FILL_UNFILLED).longValue(), "the retry did not find it either");

        recorder.close();
    }

    @Test
    void a_retry_declined_by_the_limit_is_recorded_as_retried_false() throws Exception {
        // retryLimit = min(docsumRetryLimit 10, 0.5 * numHitsToFill + 1). With two hits that is 2.0, and two
        // skipped hits is not < 2.0, so Vespa gives up - those docsums are lost for this query.
        SpanRecorder recorder = SpanRecorder.underParentSpan("cluster.fill");
        RpcProtobufFillInvoker invoker = invokerOver(Map.of(0, replyingWith(emptyDocsumsFor(2)),
                                                            1, replyingWith(emptyDocsumsFor(2))));

        recorder.record(() -> invoker.fill(resultWithHitsOn(0, 0), "default"));

        var attrs = recorder.spanNamed("dispatch.fill").getAttributes();
        assertEquals(2L, attrs.get(TraceAttributes.FILL_SKIPPED).longValue());
        assertEquals(false, attrs.get(TraceAttributes.FILL_RETRIED), "considered and declined, which is not the same as never needed");
        assertEquals(1, recorder.countSpans("node.fill"), "no second wave was sent");

        recorder.close();
    }

    @Test
    void a_fill_that_needs_no_retry_records_no_retry_attributes_at_all() throws Exception {
        SpanRecorder recorder = SpanRecorder.underParentSpan("cluster.fill");
        RpcProtobufFillInvoker invoker = invokerOver(Map.of(0, replyingWithError("nope")));

        recorder.record(() -> invoker.fill(resultWithHitsOn(0), "default"));

        var attrs = recorder.spanNamed("dispatch.fill").getAttributes();
        assertNull(attrs.get(TraceAttributes.FILL_RETRIED), "absent means the question never arose, which differs from false");
        assertNull(attrs.get(TraceAttributes.FILL_SKIPPED));

        recorder.close();
    }

    @Test
    void the_node_span_identifies_which_node_it_went_to_and_how_much_it_asked_for() throws Exception {
        SpanRecorder recorder = SpanRecorder.underParentSpan("cluster.fill");
        RpcProtobufFillInvoker invoker = invokerOver(Map.of(7, replyingWithError("nope")));

        recorder.record(() -> invoker.fill(resultWithHitsOn(7, 7), "default"));

        var attrs = recorder.spanNamed("node.fill").getAttributes();
        assertEquals(7L, attrs.get(TraceAttributes.CONTENT_NODE_KEY).longValue(), "the distribution key operators work with");
        assertEquals("vespa_jrt", attrs.get(TraceAttributes.RPC_SYSTEM));
        assertEquals("vespa.searchprotocol.getDocsums", attrs.get(TraceAttributes.RPC_METHOD_KEY));
        assertEquals(2L, attrs.get(TraceAttributes.FILL_HITS_REQUESTED).longValue(), "both hits live on node 7");

        recorder.close();
    }

    @Test
    void the_dispatch_span_carries_the_fan_out_and_the_hit_counts() throws Exception {
        SpanRecorder recorder = SpanRecorder.underParentSpan("cluster.fill");
        RpcProtobufFillInvoker invoker = invokerOver(Map.of(0, replyingWithError("nope"),
                                                            3, replyingWithError("nope")));

        recorder.record(() -> invoker.fill(resultWithHitsOn(0, 0, 3), "default"));

        var attrs = recorder.spanNamed("dispatch.fill").getAttributes();
        assertEquals(2L, attrs.get(TraceAttributes.FILL_NODES).longValue(), "hits live on two of the nodes");
        assertEquals(3L, attrs.get(TraceAttributes.FILL_HITS).longValue(), "three fillable hits in this partition");
        assertEquals(0L, attrs.get(TraceAttributes.FILL_HITS_FILLED).longValue(), "both nodes errored, so nothing was filled");

        recorder.close();
    }

    @Test
    void the_dispatch_span_names_the_schema_only_when_the_query_restricts_to_exactly_one() throws Exception {
        // VespaBackend.getDocumentDatabase falls back to the FIRST configured database when the restrict is
        // ambiguous, so the schema is read from the query instead and simply left unset when it is not certain.
        SpanRecorder recorder = SpanRecorder.underParentSpan("cluster.fill");
        RpcProtobufFillInvoker one = invokerOver(Map.of(0, replyingWithError("nope")));
        RpcProtobufFillInvoker two = invokerOver(Map.of(0, replyingWithError("nope")));

        recorder.record(() -> {
            one.fill(resultFor(new Query("?query=test&restrict=music"), 0), "default");
            two.fill(resultFor(new Query("?query=test&restrict=music,books"), 0), "default");
        });

        List<SpanData> dispatch = recorder.spans().stream().filter(s -> s.getName().equals("dispatch.fill")).toList();
        assertEquals(2, dispatch.size());
        assertEquals("music", dispatch.get(0).getAttributes().get(TraceAttributes.SCHEMA));
        assertNull(dispatch.get(1).getAttributes().get(TraceAttributes.SCHEMA), "two schemas: unset beats a confidently wrong one");

        recorder.close();
    }

    // ---- fixture ------------------------------------------------------------------------------------------

    private static final CompressService COMPRESSOR = new CompressService();

    private RpcProtobufFillInvoker invokerOver(Map<Integer, Client.NodeConnection> connections) {
        return new RpcProtobufFillInvoker(new RpcResourcePool(new HashMap<>(connections)),
                                          COMPRESSOR,
                                          new DocumentDatabase(schemaWithDefaultSummary()),
                                          "container.0",
                                          RpcProtobufFillInvoker.DecodePolicy.EAGER,
                                          false);
    }

    /** A schema declaring a "default" document summary, which is the class the tests ask to fill. */
    private static Schema schemaWithDefaultSummary() {
        return new Schema.Builder("test")
                .add(new DocumentSummary.Builder("default").addField("title", "string").build())
                .build();
    }

    /** A result whose fillable hits are spread over the given distribution keys, one hit each. */
    private static Result resultWithHitsOn(int... distributionKeys) {
        return resultFor(new Query("?query=test"), distributionKeys);
    }

    private static Result resultFor(Query query, int... distributionKeys) {
        Result result = new Result(query);
        for (int key : distributionKeys) {
            FastHit hit = new FastHit(new byte[12], 1.0, OptionalInt.empty(), 0, key);
            hit.setFillable();
            result.hits().add(hit);
        }
        return result;
    }

    private static Client.NodeConnection replyingWithError(String error) {
        return replying(Client.ResponseOrError.fromError(error));
    }

    private static Client.NodeConnection replyingWith(Client.ProtobufResponse response) {
        return replying(Client.ResponseOrError.fromResponse(response));
    }

    private static Client.NodeConnection replying(Client.ResponseOrError<Client.ProtobufResponse> reply) {
        return new Client.NodeConnection() {
            @Override
            public void request(String rpcMethod, CompressionType compression, int uncompressedLength,
                                byte[] compressedPayload, Client.ResponseReceiver receiver, double timeoutSeconds) {
                receiver.receive(reply);
            }
            @Override public void close() { }
        };
    }

    /**
     * A reply carrying a docsums ARRAY of n entries, none of which has a "docsum" field - which is how the
     * content layer reports "I no longer hold this document". An entirely empty reply will NOT do: fill()
     * returns early when the docsums field itself is invalid, producing no skipped hits and hence no retry.
     */
    private static Client.ProtobufResponse emptyDocsumsFor(int n) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        Cursor docsums = root.setArray("docsums");
        for (int i = 0; i < n; i++) docsums.addObject();
        byte[] payload = ai.vespa.searchlib.searchprotocol.protobuf.SearchProtocol.DocsumReply.newBuilder()
                .setSlimeSummaries(com.google.protobuf.ByteString.copyFrom(BinaryFormat.encode(slime)))
                .build().toByteArray();
        return new Client.ProtobufResponse(CompressionType.NONE.getCode(), payload.length, payload);
    }

    private static Client.ProtobufResponse emptyDocsumReply() {
        byte[] payload = ai.vespa.searchlib.searchprotocol.protobuf.SearchProtocol.DocsumReply.newBuilder()
                                                                                             .build()
                                                                                             .toByteArray();
        return new Client.ProtobufResponse(CompressionType.NONE.getCode(), payload.length, payload);
    }
}
