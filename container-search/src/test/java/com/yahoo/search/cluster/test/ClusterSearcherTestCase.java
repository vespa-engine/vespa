// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.cluster.test;

import java.util.ArrayList;
import java.util.List;

import com.yahoo.component.ComponentId;
import com.yahoo.prelude.Ping;
import com.yahoo.prelude.Pong;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.cluster.ClusterSearcher;
import com.yahoo.search.cluster.Hasher;
import com.yahoo.search.cluster.PingableSearcher;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.result.Hit;
import com.yahoo.search.searchchain.Execution;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ClusterSearcherTestCase {

    class TestingBackendSearcher extends PingableSearcher {

        Hit hit;

        public TestingBackendSearcher(Hit hit) {
            this.hit = hit;
        }

        @Override
        public Result search(Query query,Execution execution) {
            Result result = execution.search(query);
            result.hits().add(hit);
            return result;
        }
    }

    class BlockingBackendSearcher extends TestingBackendSearcher {

        private boolean blocking = false;

        public BlockingBackendSearcher(Hit hit) {
            super(hit);
        }

         public Result search(Query query,Execution execution) {
             Result result = super.search(query,execution);
             if (isBlocking()) {
                 result.hits().addError(ErrorMessage.createUnspecifiedError("Dummy error"));
             }
             return result;
         }

        @Override
        public Pong ping(Ping ping, Execution execution) {
            Pong pong = new Pong();
            if (isBlocking()) {
                pong.addError(ErrorMessage.createTimeout("Dummy timeout"));
            }
            return new Pong();
        }

        public boolean isBlocking() {
            return blocking;
        }

        public void setBlocking(boolean blocking) {
            this.blocking = blocking;
        }
    }

    class SimpleQuery extends Query {
        int hashValue;
        public SimpleQuery(int hashValue) {
            this.hashValue = hashValue;
        }

        @Override
        public int hashCode() {
            return hashValue;
        }
    }

    class SimpleHasher<T> extends Hasher<T> {


        class SimpleNodeList extends NodeList<T> {
            public SimpleNodeList() {
                super(null);
            }

            public T select(int code, int trynum) {
                return objects.get(code + trynum % objects.size());
            }

            public int getNodeCount() {
                return objects.size();
            }
        }

        List<T> objects = new ArrayList<>();

        @Override
        public synchronized void remove(T node) {
            objects.remove(node);
        }

        @Override
        public synchronized void add(T node) {
            objects.add(node);
        }

        @Override
        public NodeList<T> getNodes() {
            return new SimpleNodeList();

        }
    }

    /** A cluster searcher which clusters over a set of alternative searchers (search chains would be more realistic) */
    static class SearcherClusterSearcher extends ClusterSearcher<Searcher> {

        public SearcherClusterSearcher(ComponentId id,List<Searcher> searchers,Hasher<Searcher> hasher) {
            super(id,searchers,hasher,false);
        }

        @Override
        public Result search(Query query,Execution execution,Searcher searcher) {
            return searcher.search(query,execution);
        }

        @Override
        public void fill(Result result,String summaryName,Execution execution,Searcher searcher) {
            searcher.fill(result,summaryName,execution);
        }

        @Override
        public Pong ping(Ping ping,Searcher searcher) {
            return new Execution(searcher, Execution.Context.createContextStub()).ping(ping);
        }

    }

    @Test
    public void testSimple() {
        Hit blockingHit = new Hit("blocking");
        Hit nonblockingHit = new Hit("nonblocking");
        BlockingBackendSearcher blockingSearcher  = new BlockingBackendSearcher(blockingHit);
        List<Searcher> searchers=new ArrayList<>();
        searchers.add(blockingSearcher);
        searchers.add(new TestingBackendSearcher(nonblockingHit));
        ClusterSearcher<?> provider = new SearcherClusterSearcher(new ComponentId("simple"),searchers,new SimpleHasher<>());

        Result blockingResult = new Execution(provider, Execution.Context.createContextStub()).search(new SimpleQuery(0));
        assertEquals(blockingHit,blockingResult.hits().get(0));
        Result nonblockingResult = new Execution(provider, Execution.Context.createContextStub()).search(new SimpleQuery(1));
        assertEquals(nonblockingHit,nonblockingResult.hits().get(0));

        blockingSearcher.setBlocking(true);

        blockingResult = new Execution(provider, Execution.Context.createContextStub()).search(new SimpleQuery(0));
        assertEquals(blockingResult.hits().get(0),nonblockingHit);
        nonblockingResult = new Execution(provider, Execution.Context.createContextStub()).search(new SimpleQuery(1));
        assertEquals(nonblockingResult.hits().get(0),nonblockingHit);
    }

}
