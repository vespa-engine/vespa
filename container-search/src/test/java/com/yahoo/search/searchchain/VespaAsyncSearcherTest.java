// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.searchchain;

import com.yahoo.component.chain.Chain;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests async execution of search chains.
 *
 * @author Peter Thomas
 * @author bratseth
 */
public class VespaAsyncSearcherTest {

    private ExecutorService executor;

    @BeforeEach
    public void setUp() throws Exception {
        executor = Executors.newFixedThreadPool(16);
    }

    @AfterEach
    public void tearDown() {
        assertEquals(0, executor.shutdownNow().size());
    }

    private static class FirstSearcher extends Searcher {

        @Override
        public Result search(Query query, Execution execution) {
            int count = 10;
            List<FutureResult> futures = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                Query subQuery = query.clone();
                FutureResult future = new AsyncExecution(execution).search(subQuery);
                futures.add(future);
            }
            AsyncExecution.waitForAll(futures, 10 * 60 * 1000);
            Result combinedResult = new Result(query);
            for (FutureResult resultFuture : futures) {
                Result result = resultFuture.get();
                combinedResult.mergeWith(result);
                combinedResult.hits().add(result.hits());
            }
            return combinedResult;
        }

    }

    private static class SecondSearcher extends Searcher {

        @Override
        public Result search(Query query, Execution exctn) {
            return new Result(query);
        }

    }

    @Test
    void testAsyncExecution() {
        Chain<Searcher> chain = new Chain<>(new FirstSearcher(), new SecondSearcher());
        Execution execution = new Execution(chain, Execution.Context.createContextStub());
        Query query = new Query();
        execution.search(query);
    }

}
