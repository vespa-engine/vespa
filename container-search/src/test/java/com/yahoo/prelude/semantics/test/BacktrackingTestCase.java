// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.test;

import java.util.ArrayList;
import java.util.List;

import com.google.common.util.concurrent.MoreExecutors;
import com.yahoo.component.chain.Chain;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.search.Query;
import com.yahoo.prelude.semantics.RuleImporter;
import com.yahoo.prelude.semantics.SemanticSearcher;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.rendering.RendererRegistry;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.test.QueryTestCase;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
@SuppressWarnings("deprecation")
public class BacktrackingTestCase {

    private static final String root="src/test/java/com/yahoo/prelude/semantics/test/rulebases/";

    private static final SemanticSearcher searcher;

    static {
        try {
            searcher = new SemanticSearcher(new RuleImporter().importFile(root + "backtrackingrules.sr"));
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected void assertSemantics(String result,String input) {
        assertSemantics(result,input,0);
    }

    protected void assertSemantics(String result,String input,int tracelevel) {
        Query query=new Query("?query=" + QueryTestCase.httpEncode(input) + "&tracelevel=0&tracelevel.rules=" + tracelevel);
        doSearch(searcher, query, 0,10);
        assertEquals(result, query.getModel().getQueryTree().getRoot().toString());
    }

    // Literal terms ---------------

    @Test
    public void testMultilevelBacktrackingLiteralTerms() {
        assertSemantics("replaced","word1 word2 word5 word8");
    }

    @Test
    public void testMultilevelBacktrackingWontReorderOthertermsLiteralTerms() {
        assertSemantics("AND other1 other2 other3 replaced","other1 other2 other3 word1 word2 word5 word8");
    }

    @Test
    public void testMultilevelBacktrackingWithMulticompoundMatchLiteralTerms() {
        assertSemantics("AND other1 other2 other3 replaced","other1 other2 other3 word1 word2 word5-word8");
    }

    @Test
    public void testMultilevelBacktrackingPreservePartialMatchBeforeLiteralTerms() {
        assertSemantics("AND word1 word2 word5 replaced","word1 word2 word5 word1 word2 word5 word8");
    }

    @Test
    public void testMultilevelBacktrackingPreservePartialMatchAfterLiteralTerms() {
        assertSemantics("AND replaced word1 word2 word5","word1 word2 word5 word8 word1 word2 word5 ");
    }

    // reference terms ---------------

    @Test
    public void testMultilevelBacktrackingReferenceTerms() {
        assertSemantics("AND ref:ref1 ref:ref2 ref:ref5 ref:ref8","ref1 ref2 ref5 ref8");
    }

    @Test
    public void testMultilevelBacktrackingPreservePartialMatchBeforeReferenceTerms() {
        assertSemantics("AND ref1 ref2 ref5 ref:ref1 ref:ref2 ref:ref5 ref:ref8",
                        "ref1 ref2 ref5 ref1 ref2 ref5 ref8");
    }

    @Test
    public void testMultilevelBacktrackingPreservePartialMatchAfterReferenceTerms() {
        assertSemantics("AND ref:ref1 ref:ref2 ref:ref5 ref:ref8 ref1 ref2 ref5",
                        "ref1 ref2 ref5 ref8 ref1 ref2 ref5");
    }

    private Result doSearch(Searcher searcher, Query query, int offset, int hits) {
        query.setOffset(offset);
        query.setHits(hits);
        return createExecution(searcher).search(query);
    }

    private Execution createExecution(Searcher searcher) {
        Execution.Context context = new Execution.Context(null, null, null, new RendererRegistry(MoreExecutors.directExecutor()), new SimpleLinguistics());
        return new Execution(chainedAsSearchChain(searcher), context);
    }

    private Chain<Searcher> chainedAsSearchChain(Searcher topOfChain) {
        List<Searcher> searchers = new ArrayList<>();
        searchers.add(topOfChain);
        return new Chain<>(searchers);
    }

}
