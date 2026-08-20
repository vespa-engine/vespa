// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.cluster;

import ai.vespa.telemetry.api.trace.OtelTracing;
import ai.vespa.telemetry.api.trace.TraceAttributes;
import com.yahoo.prelude.fastsearch.ClusterParams;
import com.yahoo.prelude.fastsearch.FastHit;
import com.yahoo.prelude.fastsearch.VespaBackend;
import com.yahoo.search.dispatch.FillInvoker;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.schema.Cluster;
import com.yahoo.search.schema.Schema;
import com.yahoo.search.schema.SchemaInfo;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.test.SpanRecorder;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies L4 fork context propagation at {@link ClusterSearcher#searchMultipleDocumentTypes}: when a query
 * resolves to more than one schema, each schema is searched on a pool thread, and work below that fork must
 * still see the trace context.
 *
 * <p>Nothing between the fork and the backend creates a span today, so this test stands in for the L5 dispatch
 * and per-node spans that will be created there: the fake backend creates one, exactly where a real
 * {@code dispatch.search} span would be.</p>
 *
 * <p>Also verifies the L6 {@code cluster.fill} span at {@link ClusterSearcher#fill(Result, String, Execution)}.
 * That is the top of the fill side of the dispatch layer: one span per fill against one content cluster, under
 * which the per-invoker {@code dispatch.fill} and per-node {@code node.fill} spans hang.</p>
 *
 * @author onur
 */
class ClusterSearcherTracingTest {

    @Test
    void the_multi_schema_fork_carries_the_trace_context_to_the_pool_thread() throws Exception {
        SpanRecorder recorder = SpanRecorder.underParentSpan("forking");

        SpanCreatingBackend backend = new SpanCreatingBackend();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        // Two backends => doSearch takes the multi-schema branch (ClusterSearcher:226) and forks per schema
        ClusterSearcher searcher = new ClusterSearcher(schemaInfo("s1", "s2"),
                                                       Map.of("s1", backend, "s2", backend),
                                                       executor);
        try {
            recorder.record(() -> searcher.search(new Query("?query=test"),
                                                  new Execution(Execution.Context.createContextStub())));
        } finally {
            executor.shutdown();
            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS), "executor did not terminate");
        }

        assertFalse(backend.threads.contains(Thread.currentThread().getName()),
                    "the per-schema searches must really run on the pool thread, or this test proves nothing");

        for (String schema : List.of("s1", "s2")) {
            SpanData backendSpan = recorder.spanNamed("backend " + schema);
            assertEquals(recorder.parentSpan().getSpanContext().getTraceId(), backendSpan.getTraceId(),
                         "a span created below the fork must stay in the forking thread's trace");
            assertEquals(recorder.parentSpan().getSpanContext().getSpanId(), backendSpan.getParentSpanId(),
                         "and be a child of the span that was current when the task was submitted");
        }

        recorder.close();
    }

    @Test
    void a_fill_produces_one_cluster_fill_span() throws Exception {
        SpanRecorder recorder = SpanRecorder.underParentSpan("fill default");   // as the L3 span in ensureFilled

        FillSpanBackend backend = new FillSpanBackend();
        ClusterSearcher searcher = new ClusterSearcher(schemaInfo("s1"), Map.of("s1", backend), null);

        recorder.record(() -> searcher.fill(new Result(new Query("?query=test")), "default",
                                            new Execution(Execution.Context.createContextStub())));

        SpanData span = recorder.spanNamed("cluster.fill");
        assertEquals("ai.vespa", span.getInstrumentationScopeInfo().getName());
        assertEquals(recorder.parentSpan().getSpanContext().getSpanId(), span.getParentSpanId(),
                     "cluster.fill must hang under the L3 fill span that drove the fill");

        recorder.close();
    }

    @Test
    void work_done_by_the_backend_hangs_under_the_cluster_fill_span() throws Exception {
        // The property the rest of L6 depends on: cluster.fill is CURRENT while the backends fill, so
        // dispatch.fill and node.fill become its children with no context plumbing of their own.
        SpanRecorder recorder = SpanRecorder.underParentSpan("fill default");

        FillSpanBackend backend = new FillSpanBackend();
        ClusterSearcher searcher = new ClusterSearcher(schemaInfo("s1"), Map.of("s1", backend), null);

        recorder.record(() -> searcher.fill(new Result(new Query("?query=test")), "default",
                                            new Execution(Execution.Context.createContextStub())));

        assertEquals(1, backend.fills.get(), "the backend must actually have been asked to fill");
        assertEquals(recorder.spanNamed("cluster.fill").getSpanId(),
                     recorder.spanNamed("backend fill").getParentSpanId());

        recorder.close();
    }

    @Test
    void the_span_covers_the_no_backend_branch_too() throws Exception {
        // A fill that can serve nothing still took time and still produced an error, which is exactly the
        // case worth seeing in a trace - so the span must cover the error branch, not just the happy one.
        SpanRecorder recorder = SpanRecorder.underParentSpan("fill default");

        ClusterSearcher searcher = new ClusterSearcher(schemaInfo(), Map.of(), null);
        Result result = new Result(new Query("?query=test"));

        recorder.record(() -> searcher.fill(result, "default", new Execution(Execution.Context.createContextStub())));

        assertNotNull(result.hits().getErrorHit(), "no backends in service must still be reported as an error");
        assertEquals(1, recorder.countSpans("cluster.fill"));

        recorder.close();
    }

    @Test
    void nothing_is_recorded_when_telemetry_is_absent() throws Exception {
        // Without a Telemetry in the current context the tracer is the no-op one, so the span is never created
        // rather than created and dropped. Run OUTSIDE recorder.record() to get that state.
        SpanRecorder recorder = SpanRecorder.underParentSpan("fill default");

        ClusterSearcher searcher = new ClusterSearcher(schemaInfo("s1"), Map.of("s1", new FillSpanBackend()), null);
        searcher.fill(new Result(new Query("?query=test")), "default",
                      new Execution(Execution.Context.createContextStub()));

        assertEquals(0, recorder.countSpans("cluster.fill"));

        recorder.close();
    }

    @Test
    void dispatch_fill_spans_hang_under_the_cluster_fill_span() throws Exception {
        // The real nesting, through the real code path: ClusterSearcher.fill -> VespaBackend.fill ->
        // partitionHits -> doPartialFill -> FillInvoker.fill. Chunk 1 only proved that SOME work done by the
        // backend nests under cluster.fill; this proves it for the actual L6 span.
        SpanRecorder recorder = SpanRecorder.underParentSpan("searcher.ensureFilled");

        Result result = new Result(new Query("?query=test"));
        Query tag = new Query("?query=test");
        for (int i = 0; i < 2; i++) {           // same query identity => one partition => one invoker
            FastHit hit = new FastHit();
            hit.setQuery(tag);
            hit.setFillable();
            result.hits().add(hit);
        }
        InvokerRunningBackend backend = new InvokerRunningBackend();
        ClusterSearcher searcher = new ClusterSearcher(schemaInfo("s1"), Map.of("s1", backend), null);

        recorder.record(() -> searcher.fill(result, "default", new Execution(Execution.Context.createContextStub())));

        assertEquals(1, recorder.countSpans("dispatch.fill"));
        assertEquals(recorder.spanNamed("cluster.fill").getSpanId(),
                     recorder.spanNamed("dispatch.fill").getParentSpanId());

        recorder.close();
    }

    @Test
    void the_cluster_fill_span_names_the_cluster_the_summary_class_and_the_backend_count() throws Exception {
        SpanRecorder recorder = SpanRecorder.underParentSpan("searcher.ensureFilled");

        ClusterSearcher searcher = new ClusterSearcher(schemaInfo("s1"), Map.of("s1", new FillSpanBackend()), null);

        recorder.record(() -> searcher.fill(new Result(new Query("?query=test")), "default",
                                            new Execution(Execution.Context.createContextStub())));

        var attrs = recorder.spanNamed("cluster.fill").getAttributes();
        assertEquals("testScenario", attrs.get(TraceAttributes.CONTENT_CLUSTER), "the cluster name the test constructor sets");
        assertEquals("default", attrs.get(TraceAttributes.FILL_SUMMARY_CLASS));
        assertEquals(1L, attrs.get(TraceAttributes.FILL_BACKENDS).longValue());

        recorder.close();
    }

    @Test
    void the_cluster_fill_span_is_attributable_even_when_no_backend_can_serve_it() throws Exception {
        // The attributes are set before any work, so the failure case is not an anonymous span.
        SpanRecorder recorder = SpanRecorder.underParentSpan("searcher.ensureFilled");

        ClusterSearcher searcher = new ClusterSearcher(schemaInfo(), Map.of(), null);

        recorder.record(() -> searcher.fill(new Result(new Query("?query=test")), "default",
                                            new Execution(Execution.Context.createContextStub())));

        var attrs = recorder.spanNamed("cluster.fill").getAttributes();
        assertEquals("testScenario", attrs.get(TraceAttributes.CONTENT_CLUSTER));
        assertEquals(0L, attrs.get(TraceAttributes.FILL_BACKENDS).longValue());

        recorder.close();
    }

    private static SchemaInfo schemaInfo(String... names) {
        Cluster.Builder cluster = new Cluster.Builder("testScenario");   // matches the test constructor's cluster name
        for (String name : names) cluster.addSchema(name);
        return new SchemaInfo(Stream.of(names).map(name -> new Schema.Builder(name).build()).toList(),
                              List.of(cluster.build()));
    }

    /** A backend whose doPartialFill runs a real {@link FillInvoker}, so the L6 dispatch.fill span is created. */
    private static final class InvokerRunningBackend extends VespaBackend {

        InvokerRunningBackend() { super(new ClusterParams("container.0")); }

        @Override protected Result doSearch2(String schema, Query query) {
            throw new UnsupportedOperationException("this backend only fills");
        }

        @Override protected void doPartialFill(Result result, String summaryClass) {
            new FillInvoker() {
                @Override protected void sendFillRequest(Result r, String sc) { }
                @Override protected void getFillResults(Result r, String sc) { }
                @Override protected void release() { }
            }.fill(result, summaryClass);
        }
    }

    /**
     * Stands in for a real backend: records which thread it ran on and creates a span where L5 would create
     * {@code dispatch.search}. {@code search} is overridden rather than {@code doSearch2} because
     * {@code perSchemaSearch:259} calls the public method.
     */
    private static final class SpanCreatingBackend extends VespaBackend {

        final Set<String> threads = ConcurrentHashMap.newKeySet();

        SpanCreatingBackend() { super(new ClusterParams("container.0")); }

        @Override
        public Result search(String schema, Query query) {
            threads.add(Thread.currentThread().getName());
            OtelTracing.instrument("backend " + schema, () -> { });
            return new Result(query);
        }

        @Override protected Result doSearch2(String schema, Query query) {
            throw new UnsupportedOperationException("search() is overridden");
        }

        @Override protected void doPartialFill(Result result, String summaryClass) { }
    }

    /**
     * Stands in for a real backend on the FILL path: creates a span where L6 will create {@code dispatch.fill}.
     * {@code fill} is overridden rather than {@code doPartialFill} so the test needs no fillable-hit fixture -
     * {@code VespaBackend.fill:205} would partition the hits first and reach nothing on an empty result.
     */
    private static final class FillSpanBackend extends VespaBackend {

        final AtomicInteger fills = new AtomicInteger();

        FillSpanBackend() { super(new ClusterParams("container.0")); }

        @Override
        public void fill(Result result, String summaryClass) {
            fills.incrementAndGet();
            OtelTracing.instrument("backend fill", () -> { });
        }

        @Override protected Result doSearch2(String schema, Query query) {
            throw new UnsupportedOperationException("this backend only fills");
        }

        @Override protected void doPartialFill(Result result, String summaryClass) {
            throw new UnsupportedOperationException("fill() is overridden");
        }
    }
}
