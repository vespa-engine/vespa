// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.handler;

import ai.vespa.telemetry.api.trace.TraceAttributes;
import com.yahoo.component.chain.Chain;
import com.yahoo.search.Query;
import com.yahoo.search.Searcher;
import com.yahoo.search.test.SpanRecorder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies the L3 CHAIN span created in {@link SearchHandler#searchAndFill}: with a recording-telemetry context
 * current (as L2 makes it on the worker thread), it wraps the search-chain execution in an INTERNAL, default-scope
 * span named from the chain, as a child of the current span, emitted exactly once per query.
 *
 * <p>{@code searchAndFill} is called directly (synchronously) rather than through the async request driver, so the
 * assertion does not depend on worker-pool / query-timeout / response-rendering timing.</p>
 *
 * @author onur
 */
class SearchHandlerTracingTest {

    @Test
    void chain_span_wraps_the_search_as_a_child_of_the_current_span() {
        SpanRecorder recorder = SpanRecorder.underParentSpan("parent");

        try (SearchHandlerTester tester = new SearchHandlerTester()) {
            Chain<Searcher> chain = tester.searchHandler.getSearchChainRegistry().getChain("default");
            recorder.record(() -> tester.searchHandler.searchAndFill(new Query("?query=test"), chain));
        }

        SpanData chainSpan = recorder.spanNamed("chain.search");
        assertEquals("chain.search", chainSpan.getName());
        assertEquals(SpanKind.INTERNAL, chainSpan.getKind());
        assertEquals("ai.vespa", chainSpan.getInstrumentationScopeInfo().getName());
        assertEquals(recorder.parentSpan().getSpanContext().getSpanId(), chainSpan.getParentSpanId(),
                     "the chain span must be a child of the current span");

        recorder.close();
    }

    @Test
    void chain_span_carries_the_chain_id_and_rank_profile_as_attributes() {
        SpanRecorder recorder = SpanRecorder.underParentSpan("parent");

        try (SearchHandlerTester tester = new SearchHandlerTester()) {
            Chain<Searcher> chain = tester.searchHandler.getSearchChainRegistry().getChain("default");
            Query query = new Query("?query=test");
            query.getRanking().setProfile("a_rank_profile");   // set explicitly so it cannot be confused with the chain id
            recorder.record(() -> tester.searchHandler.searchAndFill(query, chain));
        }

        SpanData chainSpan = recorder.spanNamed("chain.search");
        assertEquals("default", chainSpan.getAttributes().get(TraceAttributes.SEARCH_CHAIN));
        assertEquals("a_rank_profile", chainSpan.getAttributes().get(TraceAttributes.QUERY_RANK_PROFILE));

        recorder.close();
    }


}
