// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.searcher.test;

import com.yahoo.component.chain.Chain;
import com.yahoo.prelude.fastsearch.FastHit;
import com.yahoo.prelude.hitfield.JSONString;
import com.yahoo.prelude.searcher.JSONDebugSearcher;
import com.yahoo.processing.execution.Execution.Trace;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.result.Relevance;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchchain.testutil.DocumentSourceSearcher;
import com.yahoo.yolean.trace.TraceNode;
import com.yahoo.yolean.trace.TraceVisitor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Visit the trace and check JSON payload is stored there when requested.
 *
 * @author Steinar Knutsen
 */
public class JSONDebugSearcherTestCase {

    private static final String NODUMPJSON = "?query=1&tracelevel=6";
    private static final String DUMPJSON = "?query=1&dumpjson=jsonfield&tracelevel=6";

    @Test
    void test() {
        Chain<Searcher> searchChain = makeSearchChain("{1: 2}", new JSONDebugSearcher());
        Execution e = new Execution(searchChain, Execution.Context.createContextStub());
        e.search(new Query(NODUMPJSON));
        Trace t = e.trace();
        LookForJson visitor = new LookForJson();
        t.accept(visitor);
        assertFalse(visitor.gotJson);
        e = new Execution(searchChain, Execution.Context.createContextStub());
        e.search(new Query(DUMPJSON));
        t = e.trace();
        t.accept(visitor);
        assertTrue(visitor.gotJson);
    }

    private Chain<Searcher> makeSearchChain(String content, Searcher dumper) {
        DocumentSourceSearcher docsource = new DocumentSourceSearcher();
        addResult(new Query(DUMPJSON), content, docsource);
        addResult(new Query(NODUMPJSON), content, docsource);
        return new Chain<>(dumper, docsource);
    }

    private void addResult(Query q, String content, DocumentSourceSearcher docsource) {
        Result r = new Result(q);
        FastHit hit = new FastHit();
        hit.setId("http://abc.html");
        hit.setRelevance(new Relevance(1));
        hit.setField("jsonfield", new JSONString(content));
        r.hits().add(hit);
        docsource.addResult(q, r);
    }

    private static class LookForJson extends TraceVisitor {

        private static final String JSON_PAYLOAD = "{1: 2}";
        public boolean gotJson = false;

        @Override
        public void visit(TraceNode node) {
            if (node.payload() == null || node.payload().getClass() != String.class) {
                return;
            }
            if (node.payload().toString().equals(JSONDebugSearcher.JSON_FIELD + JSON_PAYLOAD)) {
                gotJson = true;
            }
        }
    }

}
