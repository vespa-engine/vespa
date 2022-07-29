// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.federation;

import com.google.common.collect.ImmutableList;
import com.yahoo.component.ComponentId;
import com.yahoo.component.chain.Chain;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.IndexModel;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.federation.sourceref.SearchChainResolver;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchchain.SearchChainRegistry;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests that sources representing document types which resolve to the same actual source
 * are only included once.
 * 
 * @author bratseth
 */
public class DuplicateSourceTestCase {

    @Test
    void testDuplicateSource() {
        // Set up a single cluster and chain (chain1), containing a MockBackendSearcher and having 2 doc types (doc1, doc2)
        MockBackendSearcher mockBackendSearcher = new MockBackendSearcher();
        SearchChainRegistry searchChains = new SearchChainRegistry();
        searchChains.register(new Chain<>("chain1", mockBackendSearcher));
        Map<String, List<String>> clusters = new HashMap<>();
        clusters.put("chain1", ImmutableList.of("doc1", "doc2"));
        IndexFacts indexFacts = new IndexFacts(new IndexModel(clusters, Collections.emptyList()));
        SearchChainResolver resolver = new SearchChainResolver.Builder()
                .addSearchChain(new ComponentId("chain1"), ImmutableList.of("doc1", "doc2"))
                .build();
        FederationSearcher searcher = new FederationSearcher(new ComponentId("test"), resolver);

        Result result = searcher.search(new Query("?query=test&sources=doc1%2cdoc2"),
                new Execution(Execution.Context.createContextStub(searchChains, indexFacts)));

        assertNull(result.hits().getError());
        assertEquals(1, mockBackendSearcher.getInvocationCount());
    }
    
    private static final class MockBackendSearcher extends Searcher {

        private final AtomicInteger invocationCount = new AtomicInteger(0);
        
        @Override
        public Result search(Query query, Execution execution) {
            invocationCount.incrementAndGet();
            return new Result(query);
        }
        
        public int getInvocationCount() { return invocationCount.get(); }

    }

}
