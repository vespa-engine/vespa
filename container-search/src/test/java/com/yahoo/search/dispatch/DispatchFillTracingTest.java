// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch;

import ai.vespa.telemetry.api.trace.TraceAttributes;
import com.yahoo.prelude.fastsearch.ClusterParams;
import com.yahoo.prelude.fastsearch.FastHit;
import com.yahoo.prelude.fastsearch.VespaBackend;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.test.SpanRecorder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the L6 {@code dispatch.fill} span created in {@link FillInvoker#fill}.
 *
 * <p>It is the fill-side twin of {@code dispatch.search} in {@link SearchInvoker#search}: one span per allocated
 * fill connection, covering both phases of the two-phase invoker - {@code sendFillRequest} then
 * {@code getFillResults}. One invoker is built and closed per PARTITION of the hits
 * ({@code IndexedBackend.doPartialFill:108}), and {@code VespaBackend.partitionHits:176} splits by the identity of
 * the {@link Query} each hit was tagged with, so one user-level fill can produce several of these spans.</p>
 *
 * @author onur
 */
class DispatchFillTracingTest {

    @Test
    void a_fill_produces_one_span_covering_both_phases() throws Exception {
        SpanRecorder recorder = SpanRecorder.underParentSpan("cluster.fill");
        RecordingFillInvoker invoker = new RecordingFillInvoker();

        recorder.record(() -> invoker.fill(new Result(new Query("?query=test")), "default"));

        SpanData span = recorder.spanNamed("dispatch.fill");
        assertEquals(SpanKind.INTERNAL, span.getKind());
        assertEquals("ai.vespa", span.getInstrumentationScopeInfo().getName());
        assertEquals(recorder.parentSpan().getSpanContext().getSpanId(), span.getParentSpanId(),
                     "dispatch.fill hangs under the cluster.fill span");
        assertEquals(List.of("send", "get"), invoker.phases, "the span must cover BOTH phases of the invoker");

        recorder.close();
    }

    @Test
    void nothing_is_recorded_when_telemetry_is_absent() throws Exception {
        SpanRecorder recorder = SpanRecorder.underParentSpan("cluster.fill");

        new RecordingFillInvoker().fill(new Result(new Query("?query=test")), "default");   // outside record()

        assertEquals(0, recorder.countSpans("dispatch.fill"));

        recorder.close();
    }

    @Test
    void a_returned_error_is_recorded_as_span_status_with_the_code_and_short_message() throws Exception {
        // Fill failures never throw - they are added to the result and returned - so without this the span
        // of a fill that fetched nothing would look successful.
        SpanRecorder recorder = SpanRecorder.underParentSpan("cluster.fill");
        FillInvoker invoker = failingInvoker(ErrorMessage.createTimeout("Summary data is incomplete: 2 outstanding"));

        recorder.record(() -> invoker.fill(new Result(new Query("?query=test")), "default"));

        SpanData span = recorder.spanNamed("dispatch.fill");
        assertEquals(StatusCode.ERROR, span.getStatus().getStatusCode());
        assertEquals(com.yahoo.container.protect.Error.TIMEOUT.code,
                     span.getAttributes().get(TraceAttributes.ERROR_CODE).intValue());
        assertEquals("Timed out", span.getAttributes().get(TraceAttributes.ERROR_MESSAGE),
                     "the SHORT message: the detailed one carries caller-supplied text and must never be used");

        recorder.close();
    }

    @Test
    void a_successful_fill_is_not_marked_as_an_error() throws Exception {
        SpanRecorder recorder = SpanRecorder.underParentSpan("cluster.fill");
        RecordingFillInvoker invoker = new RecordingFillInvoker();

        recorder.record(() -> invoker.fill(new Result(new Query("?query=test")), "default"));

        assertEquals(StatusCode.UNSET, recorder.spanNamed("dispatch.fill").getStatus().getStatusCode());

        recorder.close();
    }

    @Test
    void one_span_per_partition_of_the_hits() throws Exception {
        // VespaBackend.fill partitions the unfilled hits by the identity of the Query each was tagged with and
        // builds one invoker per partition, so two query clones must yield two dispatch.fill spans.
        SpanRecorder recorder = SpanRecorder.underParentSpan("cluster.fill");

        Query a = new Query("?query=a");
        Query b = new Query("?query=b");
        Result result = new Result(new Query("?query=outer"));
        for (Query q : List.of(a, a, b)) {
            FastHit hit = new FastHit();
            hit.setQuery(q);
            hit.setFillable();
            result.hits().add(hit);
        }
        InvokerRunningBackend backend = new InvokerRunningBackend();

        recorder.record(() -> backend.fill(result, "default"));

        assertEquals(2, backend.invokers, "two query identities => two partitions => two invokers");
        assertEquals(2, recorder.countSpans("dispatch.fill"));

        recorder.close();
    }

    @Test
    void the_span_is_created_even_when_the_invoker_finds_nothing_to_do() throws Exception {
        // RpcProtobufFillInvoker.sendFillRequest:93 returns immediately when the result is already filled, and
        // getFillResults:128 short-circuits too. The span is still created - allocating the invoker had a cost,
        // and suppressing it is not possible from the base class, which cannot see the subclass's state.
        SpanRecorder recorder = SpanRecorder.underParentSpan("cluster.fill");
        FillInvoker doingNothing = new FillInvoker() {
            @Override protected void sendFillRequest(Result result, String summaryClass) { }
            @Override protected void getFillResults(Result result, String summaryClass) { }
            @Override protected void release() { }
        };

        recorder.record(() -> doingNothing.fill(new Result(new Query("?query=test")), "default"));

        assertEquals(1, recorder.countSpans("dispatch.fill"));
        assertTrue(recorder.spanNamed("dispatch.fill").getEndEpochNanos() >= recorder.spanNamed("dispatch.fill").getStartEpochNanos());

        recorder.close();
    }

    /** Records which phases ran, so a test can prove the span covers both rather than only the first. */
    private static final class RecordingFillInvoker extends FillInvoker {
        final List<String> phases = new ArrayList<>();
        @Override protected void sendFillRequest(Result result, String summaryClass) { phases.add("send"); }
        @Override protected void getFillResults(Result result, String summaryClass) { phases.add("get"); }
        @Override protected void release() { }
    }

    /** An invoker that fails the way the real one does: adds an error to the result and returns normally. */
    private static FillInvoker failingInvoker(ErrorMessage error) {
        return new FillInvoker() {
            @Override protected void sendFillRequest(Result result, String summaryClass) { }
            @Override protected void getFillResults(Result result, String summaryClass) { result.hits().addError(error); }
            @Override protected void release() { }
        };
    }

    /** A backend whose doPartialFill runs a real FillInvoker, so VespaBackend's partitioning is exercised. */
    private static final class InvokerRunningBackend extends VespaBackend {

        int invokers = 0;

        InvokerRunningBackend() { super(new ClusterParams("container.0")); }

        @Override protected Result doSearch2(String schema, Query query) {
            throw new UnsupportedOperationException("this backend only fills");
        }

        @Override protected void doPartialFill(Result result, String summaryClass) {
            invokers++;
            new RecordingFillInvoker().fill(result, summaryClass);
        }
    }
}
