// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.searchchain;

import com.yahoo.component.chain.Chain;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.result.Hit;
import com.yahoo.search.result.HitGroup;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author bratseth
 */
public class AsyncExecutionOfOneChainTestCase {

    private static final double delta = 0.0000001;

    /** Tests having a result with some slow source data which should pass directly to rendering */
    @Test
    void testParallelExecutionOfOneChain() {
        ExecutorService executor = Executors.newFixedThreadPool(16);
        // Setup
        Chain<Searcher> mainChain = new Chain<>(new ParallelExecutor(executor), new ResultProcessor(), new RegularProvider());

        // Execute
        Result result = new Execution(mainChain, Execution.Context.createContextStub()).search(new Query());

        // Verify
        assertEquals(3 * 2, result.hits().size(), "Received 2 hits from 3 threads");
        assertEquals(1.0, result.hits().get("thread-0:hit-0").getRelevance().getScore(), delta);
        assertEquals(1.0, result.hits().get("thread-1:hit-0").getRelevance().getScore(), delta);
        assertEquals(1.0, result.hits().get("thread-2:hit-0").getRelevance().getScore(), delta);
        assertEquals(0.5, result.hits().get("thread-0:hit-1").getRelevance().getScore(), delta);
        assertEquals(0.5, result.hits().get("thread-1:hit-1").getRelevance().getScore(), delta);
        assertEquals(0.5, result.hits().get("thread-2:hit-1").getRelevance().getScore(), delta);
        assertEquals(0, executor.shutdownNow().size());
    }

    private static class ParallelExecutor extends Searcher {

        /** The number of parallel executions */
        private static final int parallelism = 2;
        private final Executor executor;
        ParallelExecutor(Executor executor) { this.executor = executor; }

        @Override
        public Result search(Query query, Execution execution) {
            List<FutureResult> futureResults = new ArrayList<>(parallelism);
            for (int i = 0; i < parallelism; i++)
                futureResults.add(new AsyncExecution(execution).search(query.clone()));

            Result mainResult = execution.search(query);

            // Add hits from other threads
            AsyncExecution.waitForAll(futureResults,query.getTimeLeft());
            for (FutureResult futureResult : futureResults) {
                Result result = futureResult.get();
                mainResult.mergeWith(result);
                mainResult.hits().addAll(result.hits().asList());
            }
            return mainResult;
        }

    }

    private static class RegularProvider extends Searcher {

        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public Result search(Query query,Execution execution) {
            String thread="thread-" + counter.getAndIncrement();
            Result result=new Result(query,new HitGroup("test"));
            result.hits().add(new Hit(thread + ":hit-0",1.0));
            result.hits().add(new Hit(thread + ":hit-1",0.9));
            return result;
        }

    }

    private static class ResultProcessor extends Searcher {

        @Override
        public Result search(Query query,Execution execution) {
            Result result=execution.search(query);

            int i=1;
            for (Iterator<Hit> hits=result.hits().deepIterator(); hits.hasNext(); )
                hits.next().setRelevance(1d/i++);
            return result;
        }

    }

}
