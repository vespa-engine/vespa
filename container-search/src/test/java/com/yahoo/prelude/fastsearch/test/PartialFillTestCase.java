// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch.test;

import com.yahoo.component.chain.Chain;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.prelude.fastsearch.FastHit;
import com.yahoo.prelude.fastsearch.VespaBackEndSearcher;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.rendering.RendererRegistry;
import com.yahoo.search.result.ErrorHit;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.searchchain.Execution;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author havardpe
 */
public class PartialFillTestCase {

    public static class FS4 extends VespaBackEndSearcher {
        public List<Result> history = new ArrayList<>();
        protected Result doSearch2(Query query, Execution execution) {
            return new Result(query);
        }
        protected void doPartialFill(Result result, String summaryClass) {
            history.add(result);
        }
    }

    public static class BadFS4 extends VespaBackEndSearcher {
        protected Result doSearch2(Query query, Execution execution) {
            return new Result(query);
        }
        protected void doPartialFill(Result result, String summaryClass) {
            if (result.hits().getErrorHit() == null) {
                result.hits().addError(ErrorMessage.createUnspecifiedError("error"));
            }
        }
    }

    @Test
    void testPartitioning() {
        FS4 fs4 = new FS4();
        Query a = new Query("/?query=foo");
        Query b = new Query("/?query=bar");
        Query c = new Query("/?query=foo"); // equal to a
        Result r = new Result(new Query("/?query=ignorethis"));
        for (int i = 0; i < 7; i++) {
            FastHit h = new FastHit();
            h.setQuery(a);
            h.setFillable();
            r.hits().add(h);
        }
        for (int i = 0; i < 5; i++) {
            FastHit h = new FastHit();
            h.setQuery(b);
            h.setFillable();
            r.hits().add(h);
        }
        for (int i = 0; i < 3; i++) {
            FastHit h = new FastHit();
            h.setQuery(c);
            h.setFillable();
            r.hits().add(h);
        }
        for (int i = 0; i < 2; i++) {
            FastHit h = new FastHit();
            // no query assigned
            h.setFillable();
            r.hits().add(h);
        }
        for (int i = 0; i < 5; i++) {
            FastHit h = new FastHit();
            // not fillable
            h.setQuery(a);
            r.hits().add(h);
        }
        for (int i = 0; i < 5; i++) {
            FastHit h = new FastHit();
            // already filled
            h.setQuery(a);
            h.setFilled("default");
            r.hits().add(h);
        }
        doFill(fs4, r, "default");
        assertNull(r.hits().getErrorHit());
        assertEquals(4, fs4.history.size());
        assertEquals(a, fs4.history.get(0).getQuery());
        assertEquals(7, fs4.history.get(0).getHitCount());
        assertEquals(b, fs4.history.get(1).getQuery());
        assertEquals(5, fs4.history.get(1).getHitCount());
        assertEquals(c, fs4.history.get(2).getQuery());
        assertEquals(3, fs4.history.get(2).getHitCount());
        assertEquals(r.getQuery(), fs4.history.get(3).getQuery());
        assertEquals(2, fs4.history.get(3).getHitCount());
    }

    @Test
    void testMergeErrors() {
        BadFS4 fs4 = new BadFS4();
        Query a = new Query("/?query=foo");
        Query b = new Query("/?query=bar");
        Result r = new Result(new Query("/?query=ignorethis"));
        {
            FastHit h = new FastHit();
            h.setQuery(a);
            h.setFillable();
            r.hits().add(h);
        }
        {
            FastHit h = new FastHit();
            h.setQuery(b);
            h.setFillable();
            r.hits().add(h);
        }
        doFill(fs4, r, "default");
        ErrorHit eh = r.hits().getErrorHit();
        assertNotNull(eh);
        ErrorMessage exp_sub = ErrorMessage.createUnspecifiedError("error");
        int n = 0;
        for (Iterator<? extends com.yahoo.search.result.ErrorMessage> i = eh.errorIterator(); i.hasNext(); ) {
            com.yahoo.search.result.ErrorMessage error = i.next();
            switch (n) {
                case 0:
                    assertEquals(exp_sub, error);
                    break;
                case 1:
                    assertEquals(exp_sub, error);
                    break;
                default:
                    assertTrue(false);
            }
            n++;
        }
    }

    private Execution createExecution(Searcher searcher) {
        return new Execution(chainedAsSearchChain(searcher), Execution.Context.createContextStub());
    }

    private void doFill(Searcher searcher, Result result, String summaryClass) {
        createExecution(searcher).fill(result, summaryClass);
    }

    private Chain<Searcher> chainedAsSearchChain(Searcher topOfChain) {
        List<Searcher> searchers = new ArrayList<>();
        searchers.add(topOfChain);
        return new Chain<>(searchers);
    }

}
