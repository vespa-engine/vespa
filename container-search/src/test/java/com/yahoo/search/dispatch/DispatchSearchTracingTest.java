// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch;

import ai.vespa.telemetry.api.trace.TraceAttributes;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.result.Coverage;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.test.SpanRecorder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies the L5 {@code dispatch.search} span created in {@link SearchInvoker#search}.
 *
 * <p>The span sits on the BASE class deliberately: {@code search()} is invoked only on the ROOT of the invoker
 * tree, whatever its shape — an {@link InterleavedSearchInvoker} for a multi-node group, a bare
 * {@code RpcSearchInvoker} for a single-node one ({@code InvokerFactory:90-91} returns the leaf unwrapped), or a
 * {@link SearchErrorInvoker}. Children are driven through {@code sendSearchRequest}/{@code getSearchResult}, never
 * {@code search()}, so one call yields exactly one span in every shape.</p>
 *
 * <p>Note what this span is NOT: it is one span per (query, schema, grouping pass), not one per query. A query
 * resolving to N schemas dispatches N times concurrently, and grouping re-runs the chain once per pass.</p>
 *
 * @author onur
 */
class DispatchSearchTracingTest {

    @Test
    void one_dispatch_span_per_search_parented_under_the_current_span() throws Exception {
        SpanRecorder recorder = SpanRecorder.underParentSpan("searcher ClusterSearcher");
        SearchInvoker invoker = succeeding();

        recorder.record(() -> invoker.search(query("s1"), 1.0));

        SpanData span = recorder.spanNamed("dispatch.search");
        assertEquals(SpanKind.INTERNAL, span.getKind());
        assertEquals("ai.vespa", span.getInstrumentationScopeInfo().getName());
        assertEquals(recorder.parentSpan().getSpanContext().getSpanId(), span.getParentSpanId(),
                     "the dispatch span must be a child of the searcher span above it");
        assertEquals(StatusCode.UNSET, span.getStatus().getStatusCode(), "a clean dispatch is not an error");

        recorder.close();
    }

    @Test
    void the_dispatch_span_carries_the_schema_it_searched() throws Exception {
        SpanRecorder recorder = SpanRecorder.underParentSpan("searcher");
        SearchInvoker invoker = succeeding();

        recorder.record(() -> invoker.search(query("music"), 1.0));

        // Concurrent per-schema dispatches are otherwise indistinguishable from each other.
        assertEquals("music", recorder.spanNamed("dispatch.search").getAttributes().get(TraceAttributes.SCHEMA));
        recorder.close();
    }

    @Test
    void a_RETURNED_error_marks_the_span_error_with_its_code_and_message() throws Exception {
        SpanRecorder recorder = SpanRecorder.underParentSpan("searcher");
        // Dispatch failures do not throw; without an explicit status this span would look successful.
        SearchInvoker invoker = failingWith(ErrorMessage.createTimeout("no time left"));

        recorder.record(() -> invoker.search(query("s1"), 1.0));

        SpanData span = recorder.spanNamed("dispatch.search");
        assertEquals(StatusCode.ERROR, span.getStatus().getStatusCode());
        assertEquals(12L, span.getAttributes().get(TraceAttributes.ERROR_CODE).longValue(), "Error.TIMEOUT.code");
        // The SHORT message is a fixed literal per factory; the DETAILED one is caller-supplied and can carry
        // the request URI, so it is deliberately absent here.
        assertEquals("Timed out", span.getAttributes().get(TraceAttributes.ERROR_MESSAGE));

        recorder.close();
    }

    @Test
    void a_THROWN_exception_propagates_and_marks_the_span_error() {
        SpanRecorder recorder = SpanRecorder.underParentSpan("searcher");
        SearchInvoker invoker = throwing(new IOException("connection reset"));

        IOException thrown = assertThrows(IOException.class,
                                          () -> recorder.record(() -> invoker.search(query("s1"), 1.0)));

        assertEquals("connection reset", thrown.getMessage(), "instrumentation must not swallow or wrap it");
        SpanData span = recorder.spanNamed("dispatch.search");
        assertEquals(StatusCode.ERROR, span.getStatus().getStatusCode());
        assertEquals(1, span.getEvents().size(), "the exception must be recorded on the span");

        recorder.close();
    }

    @Test
    void the_dispatch_span_carries_the_coverage_of_the_result() throws Exception {
        SpanRecorder recorder = SpanRecorder.underParentSpan("searcher");
        // Coverage is set by InterleavedSearchInvoker for a group and by ProtobufSerialization for a single
        // node, so it is available in both invoker shapes; the fake stands in for either.
        Coverage coverage = new Coverage(90, 100, 2);
        SearchInvoker invoker = new FakeInvoker(null, null, coverage);

        recorder.record(() -> invoker.search(query("s1"), 1.0));

        var attrs = recorder.spanNamed("dispatch.search").getAttributes();
        assertEquals(90L, attrs.get(TraceAttributes.COVERAGE_PERCENTAGE).longValue());
        assertEquals(2L, attrs.get(TraceAttributes.COVERAGE_NODES).longValue());
        assertNotNull(attrs.get(TraceAttributes.COVERAGE_DEGRADED));

        recorder.close();
    }

    @Test
    void no_coverage_means_NO_coverage_attributes_rather_than_zeros() throws Exception {
        SpanRecorder recorder = SpanRecorder.underParentSpan("searcher");
        SearchInvoker invoker = succeeding();   // its Result carries no Coverage

        recorder.record(() -> invoker.search(query("s1"), 1.0));

        var attrs = recorder.spanNamed("dispatch.search").getAttributes();
        assertNull(attrs.get(TraceAttributes.COVERAGE_PERCENTAGE), "absent, not a misleading zero");
        assertNull(attrs.get(TraceAttributes.COVERAGE_NODES));

        recorder.close();
    }

    @Test
    void a_wrapping_invoker_still_yields_exactly_one_dispatch_span() throws Exception {
        SpanRecorder recorder = SpanRecorder.underParentSpan("searcher");
        // Mimics InterleavedSearchInvoker: it drives children via sendSearchRequest/getSearchResult, NOT search(),
        // so the children must contribute no span of their own. This pins the placement on the base class.
        SearchInvoker root = new WrappingInvoker(List.of(succeeding(), succeeding(), succeeding()));

        recorder.record(() -> root.search(query("s1"), 1.0));

        assertEquals(1, recorder.countSpans("dispatch.search"),
                     "one span per search() call, regardless of how many nodes the root fans out to");
        recorder.close();
    }

    private static Query query(String schema) {
        Query query = new Query("?query=test");
        query.getModel().setRestrict(schema);
        return query;
    }

    private static SearchInvoker succeeding() { return new FakeInvoker(null, null, null); }
    private static SearchInvoker failingWith(ErrorMessage error) { return new FakeInvoker(error, null, null); }
    private static SearchInvoker throwing(IOException toThrow) { return new FakeInvoker(null, toThrow, null); }

    /** A leaf invoker with no RPC: it either returns a clean result, returns one carrying an error, or throws. */
    private static final class FakeInvoker extends SearchInvoker {

        private final ErrorMessage error;
        private final IOException toThrow;
        private final Coverage coverage;
        private Query query;

        FakeInvoker(ErrorMessage error, IOException toThrow, Coverage coverage) {
            super(Optional.empty());
            this.error = error;
            this.toThrow = toThrow;
            this.coverage = coverage;
        }

        @Override protected Object sendSearchRequest(Query query, double contentShare, Object context) throws IOException {
            this.query = query;
            if (toThrow != null) throw toThrow;
            return context;
        }

        @Override protected InvokerResult getSearchResult() {
            Result result = error == null ? new Result(query) : new Result(query, error);
            if (coverage != null) result.setCoverage(coverage);
            return new InvokerResult(result);
        }

        @Override protected void release() { }
    }

    /** Mimics InterleavedSearchInvoker's shape: fans out to children without calling search() on them. */
    private static final class WrappingInvoker extends SearchInvoker {

        private final List<SearchInvoker> children;
        private Query query;

        WrappingInvoker(List<SearchInvoker> children) {
            super(Optional.empty());
            this.children = children;
        }

        @Override protected Object sendSearchRequest(Query query, double contentShare, Object context) throws IOException {
            this.query = query;
            for (SearchInvoker child : children)
                context = child.sendSearchRequest(query, contentShare, context);
            return context;
        }

        @Override protected InvokerResult getSearchResult() throws IOException {
            for (SearchInvoker child : children)
                child.getSearchResult();
            return new InvokerResult(new Result(query));
        }

        @Override protected void release() { children.forEach(SearchInvoker::close); }
    }
}
