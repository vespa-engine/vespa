// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.querytransform.test;

import com.yahoo.search.Query;
import com.yahoo.prelude.query.CompositeItem;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.prelude.querytransform.NonPhrasingSearcher;
import com.yahoo.search.Searcher;
import com.yahoo.search.searchchain.Execution;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tests non-phrasing
 *
 * @author bratseth
 */
public class NonPhrasingSearcherTestCase {

    private Searcher searcher;

    @Test
    public void testSingleWordNonPhrasing() {
        searcher=
            new NonPhrasingSearcher("src/test/java/com/yahoo/prelude/querytransform/test/test-fsa.fsa");

        Query query=new Query("?query=void+aword+kanoo");

        new Execution(searcher, Execution.Context.createContextStub()).search(query);
        assertEquals("AND void kanoo", query.getModel().getQueryTree().getRoot().toString());
    }

    @Test
    public void testMultipleWordNonPhrasing() {
        searcher=
            new NonPhrasingSearcher("src/test/java/com/yahoo/prelude/querytransform/test/test-fsa.fsa");

        Query query=new Query("?query=void+tudor+vidor+kanoo");

        new Execution(searcher, Execution.Context.createContextStub()).search(query);
        CompositeItem item=(CompositeItem) query.getModel().getQueryTree().getRoot();
        assertEquals(2,item.getItemCount());
        assertEquals("void",((WordItem)item.getItem(0)).getWord());
        assertEquals("kanoo",((WordItem)item.getItem(1)).getWord());
    }

    @Test
    public void testNoNonPhrasingIfNoOtherPhrases() {
        searcher=
            new NonPhrasingSearcher("src/test/java/com/yahoo/prelude/querytransform/test/test-fsa.fsa");

        Query query=new Query("?query=tudor+vidor");

        new Execution(searcher, Execution.Context.createContextStub()).search(query);
        CompositeItem item=(CompositeItem) query.getModel().getQueryTree().getRoot();
        assertEquals(2,item.getItemCount());
        assertEquals("tudor",((WordItem)item.getItem(0)).getWord());
        assertEquals("vidor",((WordItem)item.getItem(1)).getWord());
    }

    @Test
    public void testNoNonPhrasingIfSuggestOnly() {
        searcher=
            new NonPhrasingSearcher("src/test/java/com/yahoo/prelude/querytransform/test/test-fsa.fsa");

        Query query=new Query("?query=void+tudor+vidor+kanoo&suggestonly=true");

        new Execution(searcher, Execution.Context.createContextStub()).search(query);
        CompositeItem item=(CompositeItem) query.getModel().getQueryTree().getRoot();
        assertEquals(4,item.getItemCount());
        assertEquals("void",((WordItem)item.getItem(0)).getWord());
        assertEquals("tudor",((WordItem)item.getItem(1)).getWord());
        assertEquals("vidor",((WordItem)item.getItem(2)).getWord());
        assertEquals("kanoo",((WordItem)item.getItem(3)).getWord());
    }

}
