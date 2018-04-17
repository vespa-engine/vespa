// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.searchchain.test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.yahoo.component.ComponentId;
import com.yahoo.component.chain.Chain;
import com.yahoo.component.chain.dependencies.After;
import com.yahoo.component.chain.dependencies.Before;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.result.Hit;
import com.yahoo.search.searchchain.Execution;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Tests basic search chain execution functionality
 *
 * @author bratseth
 */
public class ExecutionTestCase {

    @Test
    public void testLinearExecutions()  {
        // Make a chain
        List<Searcher> searchers1=new ArrayList<>();
        searchers1.add(new TestSearcher("searcher1"));
        searchers1.add(new TestSearcher("searcher2"));
        searchers1.add(new TestSearcher("searcher3"));
        searchers1.add(new TestSearcher("searcher4"));
        Chain<Searcher> chain1=new Chain<>(new ComponentId("chain1"), searchers1);
        // Make another chain containing two of the same searcher instances and two new
        List<Searcher> searchers2=new ArrayList<>(searchers1);
        searchers2.set(1,new TestSearcher("searcher5"));
        searchers2.set(3,new TestSearcher("searcher6"));
        Chain<Searcher> chain2=new Chain<>(new ComponentId("chain2"), searchers2);
        // Execute both
        Query query=new Query("test");
        Result result1=new Execution(chain1, Execution.Context.createContextStub()).search(query);
        Result result2=new Execution(chain2, Execution.Context.createContextStub()).search(query);
        // Verify results
        assertEquals(4,result1.getConcreteHitCount());
        assertNotNull(result1.hits().get("searcher1-1"));
        assertNotNull(result1.hits().get("searcher2-1"));
        assertNotNull(result1.hits().get("searcher3-1"));
        assertNotNull(result1.hits().get("searcher4-1"));

        assertEquals(4,result2.getConcreteHitCount());
        assertNotNull(result2.hits().get("searcher1-2"));
        assertNotNull(result2.hits().get("searcher5-1"));
        assertNotNull(result2.hits().get("searcher3-2"));
        assertNotNull(result2.hits().get("searcher6-1"));
    }

    @Test
    public void testNestedExecution() {
        // Make a chain
        List<Searcher> searchers1=new ArrayList<>();
        searchers1.add(new FillableTestSearcher("searcher1"));
        searchers1.add(new WorkflowSearcher());
        searchers1.add(new TestSearcher("searcher2"));
        searchers1.add(new FillingSearcher());
        searchers1.add(new FillableTestSearcherAtTheEnd("searcher3"));
        Chain<Searcher> chain1=new Chain<>(new ComponentId("chain1"), searchers1);
        // Execute it
        Query query=new Query("test");
        Result result1=new Execution(chain1, Execution.Context.createContextStub()).search(query);
        // Verify results
        assertEquals(7,result1.getConcreteHitCount());
        assertNotNull(result1.hits().get("searcher1-1"));
        assertNotNull(result1.hits().get("searcher2-1"));
        assertNotNull(result1.hits().get("searcher3-1"));
        assertNotNull(result1.hits().get("searcher3-1-filled"));
        assertNotNull(result1.hits().get("searcher2-2"));
        assertNotNull(result1.hits().get("searcher3-2"));
        assertNotNull(result1.hits().get("searcher3-2-filled"));
    }

    @Test
    public void testContextCacheSingleLengthSearchChain() {
        IndexFacts[] contextsBefore = new IndexFacts[1];
        IndexFacts[] contextsAfter = new IndexFacts[1];
        List<Searcher> l = new ArrayList<>(1);
        l.add(new ContextCacheSearcher(0, contextsBefore, contextsAfter));
        Chain<Searcher> chain = new Chain<>(l);
        Query query = new Query("?mutatecontext=0");
        new Execution(chain, Execution.Context.createContextStub()).search(query);
        assertEquals(contextsBefore[0], contextsAfter[0]);
        assertSame(contextsBefore[0], contextsAfter[0]);
    }

    @Test
    public void testContextCache() {
        IndexFacts[] contextsBefore = new IndexFacts[5];
        IndexFacts[] contextsAfter = new IndexFacts[5];
        List<Searcher> l = new ArrayList<>(5);
        l.add(new ContextCacheSearcher(0, contextsBefore, contextsAfter));
        l.add(new ContextCacheSearcher(1, contextsBefore, contextsAfter));
        l.add(new ContextCacheSearcher(2, contextsBefore, contextsAfter));
        l.add(new ContextCacheSearcher(3, contextsBefore, contextsAfter));
        l.add(new ContextCacheSearcher(4, contextsBefore, contextsAfter));
        Chain<Searcher> chain = new Chain<>(l);
        Query query = new Query("?mutatecontext=2");
        new Execution(chain, Execution.Context.createContextStub()).search(query);

        assertSame(contextsBefore[0], contextsAfter[0]);
        assertSame(contextsBefore[1], contextsAfter[1]);
        assertSame(contextsBefore[2], contextsAfter[2]);
        assertSame(contextsBefore[3], contextsAfter[3]);
        assertSame(contextsBefore[4], contextsAfter[4]);

        assertSame(contextsBefore[0], contextsBefore[1]);
        assertNotSame(contextsBefore[1], contextsBefore[2]);
        assertSame(contextsBefore[2], contextsBefore[3]);
        assertSame(contextsBefore[3], contextsBefore[4]);
    }

    @Test
    public void testContextCacheMoreSearchers() {
        IndexFacts[] contextsBefore = new IndexFacts[7];
        IndexFacts[] contextsAfter = new IndexFacts[7];
        List<Searcher> l = new ArrayList<>(7);
        l.add(new ContextCacheSearcher(0, contextsBefore, contextsAfter));
        l.add(new ContextCacheSearcher(1, contextsBefore, contextsAfter));
        l.add(new ContextCacheSearcher(2, contextsBefore, contextsAfter));
        l.add(new ContextCacheSearcher(3, contextsBefore, contextsAfter));
        l.add(new ContextCacheSearcher(4, contextsBefore, contextsAfter));
        l.add(new ContextCacheSearcher(5, contextsBefore, contextsAfter));
        l.add(new ContextCacheSearcher(6, contextsBefore, contextsAfter));
        Chain<Searcher> chain = new Chain<>(l);
        Query query = new Query("?mutatecontext=2,4");
        new Execution(chain, Execution.Context.createContextStub()).search(query);

        assertSame(contextsBefore[0], contextsAfter[0]);
        assertSame(contextsBefore[1], contextsAfter[1]);
        assertSame(contextsBefore[2], contextsAfter[2]);
        assertSame(contextsBefore[3], contextsAfter[3]);
        assertSame(contextsBefore[4], contextsAfter[4]);
        assertSame(contextsBefore[5], contextsAfter[5]);
        assertSame(contextsBefore[6], contextsAfter[6]);

        assertSame(contextsBefore[0], contextsBefore[1]);
        assertNotSame(contextsBefore[1], contextsBefore[2]);
        assertSame(contextsBefore[2], contextsBefore[3]);
        assertNotSame(contextsBefore[3], contextsBefore[4]);
        assertSame(contextsBefore[4], contextsBefore[5]);
        assertSame(contextsBefore[5], contextsBefore[6]);
    }

    @Test
    public void testBasicFill() {
        Chain<Searcher> chain = new Chain<Searcher>(new FillableResultSearcher());
        Execution execution = new Execution(chain, Execution.Context.createContextStub(null));

        Result result = execution.search(new Query(com.yahoo.search.test.QueryTestCase.httpEncode("?presentation.summary=all")));
        assertNotNull(result.hits().get("a"));
        assertNull(result.hits().get("a").getField("filled"));
        execution.fill(result);
        assertTrue((Boolean) result.hits().get("a").getField("filled"));
    }

    private static class FillableResultSearcher extends Searcher {

        @Override
        public Result search(Query query, Execution execution) {
            Result result = execution.search(query);
            Hit hit = new Hit("a");
            hit.setFillable();
            result.hits().add(hit);
            return result;
        }

        @Override
        public void fill(Result result, String summaryClass, Execution execution) {
            for (Hit hit : result.hits().asList()) {
                if ( ! hit.isFillable()) continue;
                hit.setField("filled",true);
                hit.setFilled("all");
            }
        }
    }

    static class ContextCacheSearcher extends Searcher {
        final int index;
        final IndexFacts[] contextsBefore;
        final IndexFacts[] contextsAfter;

        ContextCacheSearcher(int index, IndexFacts[] contextsBefore, IndexFacts[] contextsAfter) {
            this.index = index;
            this.contextsBefore = contextsBefore;
            this.contextsAfter = contextsAfter;
        }

        @Override
        public Result search(Query query, Execution execution) {
            String s = query.properties().getString("mutatecontext");
            Set<Integer> indexSet = new HashSet<>();
            for (String num : s.split(",")) {
                indexSet.add(Integer.valueOf(num));
            }

            if (indexSet.contains(index)) {
                execution.context().setIndexFacts(new IndexFacts());
            }
            contextsBefore[index] = execution.context().getIndexFacts();
            Result r =  execution.search(query);
            contextsAfter[index] = execution.context().getIndexFacts();
            return r;
        }
    }

    public static class TestSearcher extends Searcher {

        private int counter=1;

        private TestSearcher(String id) {
            super(new ComponentId(id));
        }

        @Override
        public Result search(Query query,Execution execution) {
            Result result=execution.search(query);
            result.hits().add(new Hit(getId().stringValue() + "-" + (counter++)));
            return result;
        }

    }

    public static class ForwardingSearcher extends Searcher {

        @Override
        public Result search(Query query,Execution execution) {
            Chain<Searcher> forwardTo=execution.context().searchChainRegistry().getChain("someChainId");
            return new Execution(forwardTo,execution.context()).search(query);

        }

    }

    public static class FillableTestSearcher extends Searcher {

        private int counter=1;

        private FillableTestSearcher(String id) {
            super(new ComponentId(id));
        }

        @Override
        public Result search(Query query,Execution execution) {
            Result result=execution.search(query);
            Hit hit=new Hit(getId().stringValue() + "-" + counter);
            hit.setFillable();
            result.hits().add(hit);
            return result;
        }

        @Override
        public void fill(Result result,String summaryClass,Execution execution) {
            result.hits().add(new Hit(getId().stringValue() + "-" + (counter++) + "-filled")); // Not something one would normally do in fill
        }

    }

    public static class FillableTestSearcherAtTheEnd extends FillableTestSearcher {

        private FillableTestSearcherAtTheEnd(String id) {
            super(id);
        }
    }

    @Before("com.yahoo.search.searchchain.test.ExecutionTestCase$FillableTestSearcherAtTheEnd")
    @After("com.yahoo.search.searchchain.test.ExecutionTestCase$TestSearcher")
    public static class FillingSearcher extends Searcher {

        @Override
        public Result search(Query query,Execution execution) {
            Result result=execution.search(query);
            execution.fill(result);
            return result;
        }

    }

    @After("com.yahoo.search.searchchain.test.ExecutionTestCase$FillableTestSearcher")
    @Before("com.yahoo.search.searchchain.test.ExecutionTestCase$TestSearcher")
    public static class WorkflowSearcher extends Searcher {

        @Override
        public Result search(Query query,Execution execution) {
            Result result1=execution.search(query);
            Result result2=execution.search(query);
            for (Iterator<Hit> i=result2.hits().iterator(); i.hasNext();)
                result1.hits().add(i.next());
            result1.mergeWith(result2);
            return result1;
        }

    }

}
