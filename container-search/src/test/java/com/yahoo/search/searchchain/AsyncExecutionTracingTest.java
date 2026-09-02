// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.searchchain;

import com.yahoo.component.ComponentId;
import com.yahoo.component.chain.Chain;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.processing.rendering.AsynchronousSectionedRenderer;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.rendering.RendererRegistry;
import com.yahoo.search.schema.SchemaInfo;
import com.yahoo.search.test.SpanRecorder;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Verifies L4 fork context propagation at {@link AsyncExecution}: a sub-chain executed on a pool thread must
 * still produce spans, parented under the span that was current on the forking thread.
 *
 * <p>Without the propagation these spans are not merely mis-parented — they are never created. The tracer is
 * resolved from the context ({@link Telemetry#from}), so a fork thread with no context yields the no-op
 * telemetry, a non-recording span, and nothing exported at all.</p>
 *
 * @author onur
 */
class AsyncExecutionTracingTest {

    @Test
    void a_forked_sub_chain_produces_spans_parented_under_the_forking_span() throws Exception {
        SpanRecorder recorder = SpanRecorder.underParentSpan("forking");

        AtomicReference<String> threadThatRanTheSubChain = new AtomicReference<>();
        Chain<Searcher> target = new Chain<>(new ComponentId("target"),
                                             new ThreadRecordingSearcher("A", threadThatRanTheSubChain));
        // createContextStub gives a real Executors.newSingleThreadExecutor(), so this is a genuine thread hand-off
        Execution.Context context = Execution.Context.createContextStub();

        recorder.record(() -> new AsyncExecution(target, context).search(new Query("?query=test"))
                                                                 .get(30, TimeUnit.SECONDS));

        assertNotEquals(Thread.currentThread().getName(), threadThatRanTheSubChain.get(),
                        "the sub-chain must really run on another thread, or this test proves nothing");

        SpanData searcherSpan = recorder.spanNamed("searcher.search");
        assertEquals(recorder.parentSpan().getSpanContext().getSpanId(), searcherSpan.getParentSpanId(),
                     "a span created on the fork thread must be a child of the forking span");
        assertEquals(recorder.parentSpan().getSpanContext().getTraceId(), searcherSpan.getTraceId(),
                     "and must stay in the same trace");

        recorder.close();
    }

    @Test
    void the_rejection_fallback_stays_correctly_parented_because_it_runs_on_the_calling_thread() throws Exception {
        SpanRecorder recorder = SpanRecorder.underParentSpan("forking");

        AtomicReference<String> threadThatRanTheSubChain = new AtomicReference<>();
        Chain<Searcher> target = new Chain<>(new ComponentId("target"),
                                             new ThreadRecordingSearcher("A", threadThatRanTheSubChain));
        Executor alwaysRejects = task -> { throw new RejectedExecutionException("pool full"); };

        recorder.record(() -> new AsyncExecution(target, contextWith(alwaysRejects)).search(new Query("?query=test"))
                                                                                    .get(30, TimeUnit.SECONDS));

        assertEquals(Thread.currentThread().getName(), threadThatRanTheSubChain.get(),
                     "a rejected task runs inline on the calling thread");

        SpanData searcherSpan = recorder.spanNamed("searcher.search");
        assertEquals(recorder.parentSpan().getSpanContext().getSpanId(), searcherSpan.getParentSpanId(),
                     "the inline fallback needs no propagation: the context is already current here");

        recorder.close();
    }

    /** An Execution.Context like createContextStub, but with an executor we control. */
    private static Execution.Context contextWith(Executor executor) {
        return new Execution.Context(new SearchChainRegistry(),
                                     new IndexFacts(),
                                     SchemaInfo.empty(),
                                     null,
                                     new RendererRegistry(Runnable::run),
                                     new SimpleLinguistics(),
                                     executor);
    }



    /** Terminal searcher which records the thread it ran on, so the tests can prove a hand-off did or did not happen. */
    private static final class ThreadRecordingSearcher extends Searcher {

        private final AtomicReference<String> threadName;

        ThreadRecordingSearcher(String id, AtomicReference<String> threadName) {
            super(new ComponentId(id));
            this.threadName = threadName;
        }

        @Override
        public Result search(Query query, Execution execution) {
            threadName.set(Thread.currentThread().getName());
            return new Result(query);
        }
    }
}
