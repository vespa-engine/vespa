// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.searchchain.test;

import com.yahoo.component.ComponentId;
import com.yahoo.component.chain.Chain;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.result.Hit;
import com.yahoo.search.searchchain.AsyncExecution;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchchain.FutureResult;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Test for aynchrounous execution
 *
 * @author Arne Bergene Fossaa
 */
public class AsyncExecutionTestCase {

    public class WaitingSearcher extends Searcher {

        int waittime;
        private WaitingSearcher(String id,int waittime) {
            super(new ComponentId(id));
            this.waittime = waittime;
        }

        @Override
        public Result search(Query query,Execution execution) {
            Result result=execution.search(query);
            if(waittime != 0)
                try {
                    Thread.sleep(waittime);
                } catch (InterruptedException e) {
            }
            return result;
        }
    }

    public class SimpleSearcher extends Searcher {

        public Result search(Query query,Execution execution) {
            return execution.search(query);
        }

    }

    // This should take ~50+ ms
    @Test
    public void testAsync() {
        List<Searcher> searchList = new ArrayList<>();
        searchList.add(new WaitingSearcher("one",60000));
        searchList.add(new WaitingSearcher("two",0));
        Chain<Searcher> searchChain = new Chain<>(new ComponentId("chain"), searchList);

        AsyncExecution asyncExecution = new AsyncExecution(searchChain, Execution.Context.createContextStub());
        FutureResult future = asyncExecution.search(new Query("?hits=0"));
        Result result = future.get(0, TimeUnit.MILLISECONDS);

        assertTrue(result.hits().getError() != null);
    }

    @Test
    public void testWaitForAll() {
        Chain<Searcher> slowChain = new Chain<>(
                new ComponentId("slow"),
                Arrays.asList(new Searcher[]{new WaitingSearcher("slow",30000)}
                )
        );

        Chain<Searcher> fastChain = new Chain<>(
                new ComponentId("fast"),
                Arrays.asList(new Searcher[]{new SimpleSearcher()})
                );

        FutureResult slowFuture = new AsyncExecution(slowChain, Execution.Context.createContextStub()).search(new Query("?hits=0"));
        FutureResult fastFuture = new AsyncExecution(fastChain, Execution.Context.createContextStub()).search(new Query("?hits=0"));
        fastFuture.get();
        FutureResult reslist[] = new FutureResult[]{slowFuture,fastFuture};
        List<Result> results = AsyncExecution.waitForAll(Arrays.asList(reslist),0);

        //assertTrue(slowFuture.isCancelled());
        assertTrue(fastFuture.isDone() && !fastFuture.isCancelled());

        assertNotNull(results.get(0).hits().getErrorHit());
        assertNull(results.get(1).hits().getErrorHit());
    }

    @Test
    public void testSync() {
        Query query=new Query("?query=test");
        Searcher searcher=new ResultProducingSearcher();
        Result result=new Execution(searcher, Execution.Context.createContextStub()).search(query);

        assertEquals(1,result.hits().size());
        assertEquals("hello",result.hits().get(0).getField("test"));
    }

    @Test
    public void testSyncThroughSync() {
        Query query=new Query("?query=test");
        Searcher searcher=new ResultProducingSearcher();
        Result result=new Execution(new Execution(searcher, Execution.Context.createContextStub())).search(query);

        assertEquals(1,result.hits().size());
        assertEquals("hello",result.hits().get(0).getField("test"));
    }

    @Test
    public void testAsyncThroughSync() {
        Query query=new Query("?query=test");
        Searcher searcher=new ResultProducingSearcher();
        FutureResult futureResult=new AsyncExecution(new Execution(searcher, Execution.Context.createContextStub())).search(query);

        List<FutureResult> futureResultList=new ArrayList<>();
        futureResultList.add(futureResult);
        AsyncExecution.waitForAll(futureResultList,1000);
        Result result=futureResult.get();

        assertEquals(1,result.hits().size());
        assertEquals("hello",result.hits().get(0).getField("test"));
    }

    private static class ResultProducingSearcher extends Searcher {

        @Override
        public Result search(Query query,Execution execution) {
            Result result=new Result(query);
            Hit hit=new Hit("test");
            hit.setField("test","hello");
            result.hits().add(hit);
            return result;
        }

    }

    public void testAsyncExecutionTimeout() {
        Chain<Searcher> chain = new Chain<>(new Searcher() {
            @Override
            public Result search(Query query, Execution execution) {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                return new Result(query);
            }
        });
        Execution execution = new Execution(chain, Execution.Context.createContextStub());
        AsyncExecution async = new AsyncExecution(execution);
        FutureResult future = async.searchAndFill(new Query());
        future.get(1, TimeUnit.MILLISECONDS);
    }

}
