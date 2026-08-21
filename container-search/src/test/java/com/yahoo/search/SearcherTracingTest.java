// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search;

import ai.vespa.telemetry.api.trace.TraceAttributes;
import com.yahoo.component.ComponentId;
import com.yahoo.component.chain.Chain;
import com.yahoo.search.result.Hit;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.test.SpanRecorder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies the two L3 per-SEARCHER spans: {@code searcher.search} from {@link Searcher#process} and
 * {@code searcher.ensureFilled} from {@link Searcher#ensureFilled}.
 *
 * <p>Both names are FIXED. A chain nests one span per searcher for each operation, so the searcher identity lives
 * entirely in the {@code vespa.searcher.id} and {@code vespa.searcher.class} attributes - without them the nested
 * spans would be indistinguishable. Several tests below therefore look spans up by attribute rather than by name,
 * which is the property the fixed naming depends on.</p>
 *
 * @author onur
 */
class SearcherTracingTest {

    @Test
    void searcher_spans_nest_as_the_chain_recurses() {
        SpanRecorder recorder = SpanRecorder.underParentSpan("chain");

        Chain<Searcher> chain = new Chain<>(new ComponentId("test"),
                new PassThroughSearcher("A"), new PassThroughSearcher("B"), new SourceSearcher("C"));
        Execution execution = new Execution(chain, Execution.Context.createContextStub());

        recorder.record(() -> {
            execution.search(new Query("?query=test"));
        });

        assertEquals(3, recorder.countSpans("searcher.search"), "one span per searcher, all with the same name");
        SpanData a = searcherSpan(recorder, "A");
        SpanData b = searcherSpan(recorder, "B");
        SpanData c = searcherSpan(recorder, "C");
        assertEquals(SpanKind.INTERNAL, a.getKind());
        assertEquals("ai.vespa", a.getInstrumentationScopeInfo().getName());
        assertEquals(recorder.parentSpan().getSpanContext().getSpanId(), a.getParentSpanId(), "outermost searcher nests under the chain span");
        assertEquals(a.getSpanId(), b.getParentSpanId(), "searcher B nests under searcher A");
        assertEquals(b.getSpanId(), c.getParentSpanId(), "searcher C nests under searcher B");

        recorder.close();
    }

    @Test
    void searcher_spans_carry_the_component_id_and_implementation_class_as_attributes() {
        SpanRecorder recorder = SpanRecorder.underParentSpan("chain");

        Chain<Searcher> chain = new Chain<>(new ComponentId("test"), new SourceSearcher("A"));
        Execution execution = new Execution(chain, Execution.Context.createContextStub());

        recorder.record(() -> {
            execution.search(new Query("?query=test"));
        });

        SpanData a = recorder.spanNamed("searcher.search");
        assertEquals("A", a.getAttributes().get(TraceAttributes.SEARCHER_ID));
        assertEquals(SourceSearcher.class.getName(), a.getAttributes().get(TraceAttributes.SEARCHER_CLASS),
                     "the implementation class, not the component id, so third-party searchers are distinguishable");

        recorder.close();
    }

    @Test
    void a_throwing_searcher_records_the_exception_on_its_span() {
        SpanRecorder recorder = SpanRecorder.underParentSpan("chain");

        Chain<Searcher> chain = new Chain<>(new ComponentId("test"), new ThrowingSearcher("X"));
        Execution execution = new Execution(chain, Execution.Context.createContextStub());

        recorder.record(() -> {
            assertThrows(RuntimeException.class, () -> execution.search(new Query("?query=test")));
        });

        SpanData x = recorder.spanNamed("searcher.search");
        assertEquals(StatusCode.ERROR, x.getStatus().getStatusCode());
        assertEquals(1, x.getEvents().size(), "the thrown exception must be recorded on the searcher span");
        assertEquals("exception", x.getEvents().get(0).getName());

        recorder.close();
    }

    @Test
    void fill_span_is_created_when_hits_need_filling() {
        SpanRecorder recorder = SpanRecorder.underParentSpan("chain");

        Result result = new Result(new Query("?query=test"));
        Hit hit = new Hit("id");
        hit.setFillable();   // fillable but not filled => getFilled() is non-null but empty => needs fill
        result.hits().add(hit);
        FillingSearcher searcher = new FillingSearcher("filler");
        Execution execution = new Execution(new Chain<>(new ComponentId("c"), searcher), Execution.Context.createContextStub());

        recorder.record(() -> {
            searcher.ensureFilled(result, "default", execution);
        });

        SpanData fill = recorder.spanNamed("searcher.ensureFilled");
        assertEquals(SpanKind.INTERNAL, fill.getKind());
        assertEquals("ai.vespa", fill.getInstrumentationScopeInfo().getName());
        assertEquals(recorder.parentSpan().getSpanContext().getSpanId(), fill.getParentSpanId(), "the fill span nests under the current span");
        assertEquals(1, recorder.countSpans("searcher.ensureFilled"), "exactly one fill span");

        recorder.close();
    }

    @Test
    void fill_span_carries_the_summary_class_and_the_number_of_hits_being_filled() {
        SpanRecorder recorder = SpanRecorder.underParentSpan("chain");

        Result result = new Result(new Query("?query=test"));
        for (String id : new String[] { "id1", "id2" }) {   // two, so the count cannot be confused with a constant 1
            Hit hit = new Hit(id);
            hit.setFillable();
            result.hits().add(hit);
        }
        FillingSearcher searcher = new FillingSearcher("filler");
        Execution execution = new Execution(new Chain<>(new ComponentId("c"), searcher), Execution.Context.createContextStub());

        recorder.record(() -> {
            searcher.ensureFilled(result, "default", execution);
        });

        SpanData fill = recorder.spanNamed("searcher.ensureFilled");
        assertEquals("default", fill.getAttributes().get(TraceAttributes.FILL_SUMMARY_CLASS));
        assertEquals(2L, fill.getAttributes().get(TraceAttributes.FILL_HITS).longValue(),
                     "the count is read before fill runs, so it reports the hits the fill covers");

        recorder.close();
    }

    @Test
    void no_fill_span_when_hits_are_already_filled() {
        SpanRecorder recorder = SpanRecorder.underParentSpan("chain");

        Result result = new Result(new Query("?query=test"));
        Hit hit = new Hit("id");
        hit.setFillable();
        hit.setFilled("default");   // already filled for the requested class => short-circuit, no fill span
        result.hits().add(hit);
        FillingSearcher searcher = new FillingSearcher("filler");
        Execution execution = new Execution(new Chain<>(new ComponentId("c"), searcher), Execution.Context.createContextStub());

        recorder.record(() -> {
            searcher.ensureFilled(result, "default", execution);
        });

        assertEquals(0, recorder.countSpans("searcher.ensureFilled"), "already-filled hits must not create a fill span");

        recorder.close();
    }


    @Test
    void fill_spans_carry_the_searcher_identity_so_the_nested_ones_can_be_told_apart() {
        // The name is fixed, so without these attributes a chain of fills produces N identical spans. This is
        // the whole reason the identity attributes were added to the fill span.
        SpanRecorder recorder = SpanRecorder.underParentSpan("chain");

        Result result = new Result(new Query("?query=test"));
        Hit hit = new Hit("id");
        hit.setFillable();
        result.hits().add(hit);
        FillingSearcher searcher = new FillingSearcher("filler");
        Execution execution = new Execution(new Chain<>(new ComponentId("c"), searcher), Execution.Context.createContextStub());

        recorder.record(() -> {
            searcher.ensureFilled(result, "default", execution);
        });

        SpanData fill = recorder.spanNamed("searcher.ensureFilled");
        assertEquals("filler", fill.getAttributes().get(TraceAttributes.SEARCHER_ID));
        assertEquals(FillingSearcher.class.getName(), fill.getAttributes().get(TraceAttributes.SEARCHER_CLASS));

        recorder.close();
    }

    /** The one {@code searcher.search} span belonging to the searcher with this component id. */
    private static SpanData searcherSpan(SpanRecorder recorder, String id) {
        List<SpanData> matching = recorder.spans().stream()
                .filter(s -> s.getName().equals("searcher.search"))
                .filter(s -> id.equals(s.getAttributes().get(TraceAttributes.SEARCHER_ID)))
                .toList();
        assertEquals(1, matching.size(), "expected exactly one searcher.search span with id '" + id + "', got " + recorder.spans());
        return matching.get(0);
    }

    /** Invokes the rest of the chain, so its span nests over the downstream searcher spans. */
    private static final class PassThroughSearcher extends Searcher {
        PassThroughSearcher(String id) { super(new ComponentId(id)); }
        @Override public Result search(Query query, Execution execution) { return execution.search(query); }
    }

    /** Terminal searcher: returns a result without calling down the chain. */
    private static final class SourceSearcher extends Searcher {
        SourceSearcher(String id) { super(new ComponentId(id)); }
        @Override public Result search(Query query, Execution execution) { return new Result(query); }
    }

    private static final class ThrowingSearcher extends Searcher {
        ThrowingSearcher(String id) { super(new ComponentId(id)); }
        @Override public Result search(Query query, Execution execution) { throw new RuntimeException("boom"); }
    }

    /** Terminal searcher whose fill() fills the hits directly (so ensureFilled reaches the fill branch). */
    private static final class FillingSearcher extends Searcher {
        FillingSearcher(String id) { super(new ComponentId(id)); }
        @Override public Result search(Query query, Execution execution) { return new Result(query); }
        @Override public void fill(Result result, String summaryClass, Execution execution) {
            result.hits().asList().forEach(hit -> hit.setFilled(summaryClass));
        }
    }
}
