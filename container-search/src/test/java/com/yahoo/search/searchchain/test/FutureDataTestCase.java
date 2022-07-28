// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.searchchain.test;

import com.yahoo.component.ComponentId;
import com.yahoo.component.chain.Chain;
import com.yahoo.processing.response.IncomingData;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.federation.FederationSearcher;
import com.yahoo.search.federation.sourceref.SearchChainResolver;
import com.yahoo.search.result.Hit;
import com.yahoo.search.result.HitGroup;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchchain.SearchChainRegistry;
import com.yahoo.search.searchchain.model.federation.FederationOptions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests using the async capabilities of the Processing parent framework of searchers.
 *
 * @author bratseth
 */
public class FutureDataTestCase {

    @Test
    void testAsyncFederation() throws InterruptedException, ExecutionException {
        // Setup environment
        AsyncProviderSearcher asyncProviderSearcher = new AsyncProviderSearcher();
        Searcher syncProviderSearcher = new SyncProviderSearcher();
        Chain<Searcher> asyncSource = new Chain<>(new ComponentId("async"), asyncProviderSearcher);
        Chain<Searcher> syncSource = new Chain<>(new ComponentId("sync"), syncProviderSearcher);
        SearchChainResolver searchChainResolver =
                new SearchChainResolver.Builder().addSearchChain(new ComponentId("sync"), new FederationOptions().setUseByDefault(true)).
                        addSearchChain(new ComponentId("async"), new FederationOptions().setUseByDefault(true)).
                        build();
        Chain<Searcher> main = new Chain<>(new FederationSearcher(new ComponentId("federator"), searchChainResolver));
        SearchChainRegistry searchChainRegistry = new SearchChainRegistry();
        searchChainRegistry.register(main);
        searchChainRegistry.register(syncSource);
        searchChainRegistry.register(asyncSource);

        Query query = new Query();
        query.setTimeout(5000);
        Result result = new Execution(main, Execution.Context.createContextStub(searchChainRegistry)).search(query);
        assertNotNull(result);

        HitGroup syncGroup = (HitGroup) result.hits().get("source:sync");
        assertNotNull(syncGroup);

        HitGroup asyncGroup = (HitGroup) result.hits().get("source:async");
        assertNotNull(asyncGroup);

        assertEquals(3, syncGroup.size(), "Got all sync data");
        assertEquals("sync:0", syncGroup.get(0).getId().toString());
        assertEquals("sync:1", syncGroup.get(1).getId().toString());
        assertEquals("sync:2",  syncGroup.get(2).getId().toString());

        assertEquals(asyncGroup.incoming(), asyncProviderSearcher.incomingData);
        assertEquals(0, asyncGroup.size(), "Got no async data yet");
        asyncProviderSearcher.simulateOneHitIOComplete(new Hit("async:0"));
        assertEquals(0, asyncGroup.size(), "Got no async data yet, as we haven't completed the incoming buffer and there is no data listener");
        asyncProviderSearcher.simulateOneHitIOComplete(new Hit("async:1"));
        asyncProviderSearcher.simulateAllHitsIOComplete();
        assertEquals(0, asyncGroup.size(), "Got no async data yet, as we haven't pulled it");
        asyncGroup.completeFuture().get();
        assertEquals(2, asyncGroup.size(), "Completed, so we have the data");
        assertEquals("async:0", asyncGroup.get(0).getId().toString());
        assertEquals("async:1", asyncGroup.get(1).getId().toString());
    }

    @Test
    void testFutureData() throws InterruptedException, ExecutionException, TimeoutException {
        // Set up
        AsyncProviderSearcher futureDataSource = new AsyncProviderSearcher();
        Chain<Searcher> chain = new Chain<>(Collections.<Searcher>singletonList(futureDataSource));

        // Execute
        Query query = new Query();
        Result result = new Execution(chain, Execution.Context.createContextStub()).search(query);

        // Verify the result prior to completion of delayed data
        assertEquals(0, result.hits().getConcreteSize(), "The result has been returned, but no hits are available yet");

        // pretend we're the IO layer and complete delayed data - this is typically done in a callback from jDisc
        futureDataSource.simulateOneHitIOComplete(new Hit("hit:0"));
        futureDataSource.simulateOneHitIOComplete(new Hit("hit:1"));
        futureDataSource.simulateAllHitsIOComplete();

        assertEquals(0, result.hits().getConcreteSize(), "Async arriving hits are still not visible because we haven't asked for them");

        // Results with future hit groups will be passed to rendering directly and start rendering immediately.
        // For this test we block and wait for the data instead:
        result.hits().completeFuture().get(1000, TimeUnit.MILLISECONDS);
        assertEquals(2, result.hits().getConcreteSize());
    }

    /**
     * A searcher which returns immediately with future data which can then be filled later,
     * simulating an async searcher using a separate thread to fill in result data as it becomes available.
     */
    public static class AsyncProviderSearcher extends Searcher {

        private IncomingData<Hit> incomingData = null;

        @Override
        public Result search(Query query, Execution execution) {
            if (incomingData != null) throw new IllegalArgumentException("This test searcher is one-time use only");

            HitGroup hitGroup = HitGroup.createAsync("Async source");
            this.incomingData = hitGroup.incoming();
            // A real implementation would do query.properties().get("jdisc.request") here
            // to get the jDisc request and use it to spawn a child request to the backend
            // which would eventually add to and complete incomingData
            return new Result(query,hitGroup);
        }

        public void simulateOneHitIOComplete(Hit hit) {
            incomingData.add(hit);
        }

        public void simulateAllHitsIOComplete() {
            incomingData.markComplete();
        }

    }

    public static class SyncProviderSearcher extends Searcher {

        @Override
        public Result search(Query query, Execution execution) {
            Result result = execution.search(query);
            result.hits().add(new Hit("sync:0"));
            result.hits().add(new Hit("sync:1"));
            result.hits().add(new Hit("sync:2"));
            return result;
        }
    }

}
