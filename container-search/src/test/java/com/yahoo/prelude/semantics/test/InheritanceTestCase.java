// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.test;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import com.google.common.util.concurrent.MoreExecutors;
import com.yahoo.component.chain.Chain;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.search.Query;
import com.yahoo.prelude.semantics.RuleBase;
import com.yahoo.prelude.semantics.RuleBaseException;
import com.yahoo.prelude.semantics.SemanticSearcher;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.rendering.RendererRegistry;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.test.QueryTestCase;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author bratseth
 */
@SuppressWarnings("deprecation")
public class InheritanceTestCase {

    private static final String root = "src/test/java/com/yahoo/prelude/semantics/test/rulebases/";

    private static final RuleBase parent, child1, child2, grandchild;
    private static final SemanticSearcher searcher;

    static {
        try {
            parent = RuleBase.createFromFile(root + "inheritingrules/parent.sr", null);
            child1 = RuleBase.createFromFile(root + "inheritingrules/child1.sr", null);
            child2 = RuleBase.createFromFile(root + "inheritingrules/child2.sr", null);
            grandchild = RuleBase.createFromFile(root + "inheritingrules/grandchild.sr", null);
            grandchild.setDefault(true);

            searcher = new SemanticSearcher(parent, child1, child2, grandchild);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected void assertSemantics(String result, String input, RuleBase base) {
        assertSemantics(result, input, base, 0);
    }

    protected void assertSemantics(String result, String input, RuleBase base, int tracelevel) {
        Query query = new Query("?query=" + QueryTestCase.httpEncode(input));
        base.analyze(query, tracelevel);
        assertEquals(result, query.getModel().getQueryTree().getRoot().toString());
    }

    @Test
    public void testInclusion() {
        assertTrue(grandchild.includes("child1"));
        assertTrue(grandchild.includes("child2"));
        assertTrue(grandchild.includes("parent"));
        assertTrue(grandchild.includes("grandfather"));
        assertTrue(grandchild.includes("grandmother"));
        assertFalse(grandchild.includes("nonexisting"));

        assertFalse(parent.includes("child1"));
        assertFalse(parent.includes("child2"));
        assertFalse(parent.includes("parent"));
        assertTrue(parent.includes("grandfather"));
        assertTrue(parent.includes("grandmother"));
    }

    @Test
    public void testInclusionOrderAndContentDump() {
        StringTokenizer lines = new StringTokenizer(grandchild.toContentString(),"\n",false);
        assertEquals("vw -> audi", lines.nextToken());
        assertEquals("cars -> car", lines.nextToken());
        assertEquals("[brand] [vehicle] -> vehiclebrand:[brand]", lines.nextToken());
        assertEquals("vehiclebrand:bmw +> expensivetv", lines.nextToken());
        assertEquals("vehiclebrand:audi -> vehiclebrand:skoda", lines.nextToken());
        assertEquals("vehiclebrand:vw -> vehiclebrand:audi", lines.nextToken());
        assertEquals("causesphrase -> \"a produced phrase\"", lines.nextToken());
        assertEquals("[vehicle] :- car, motorcycle, bus", lines.nextToken());
        assertEquals("[brand] :- alfa, audi, bmw, skoda", lines.nextToken());
    }

    @Test
    public void testParent()  {
        assertSemantics("vehiclebrand:audi", "audi cars", parent);
        assertSemantics("vehiclebrand:alfa", "alfa bus", parent);
        assertSemantics("AND vehiclebrand:bmw expensivetv", "bmw motorcycle", parent);
        assertSemantics("AND vw car",       "vw cars", parent);
        assertSemantics("AND skoda car",    "skoda cars", parent);
    }

    @Test
    public void testChild1() {
        assertSemantics("vehiclebrand:skoda", "audi cars", child1);
        assertSemantics("vehiclebrand:alfa", "alfa bus", child1);
        assertSemantics("AND vehiclebrand:bmw expensivetv", "bmw motorcycle", child1);
        assertSemantics("vehiclebrand:skoda", "vw cars", child1);
        assertSemantics("AND skoda car",      "skoda cars", child1);
    }

    @Test
    public void testChild2() {
        assertSemantics("vehiclebrand:audi","audi cars", child2);
        assertSemantics("vehiclebrand:alfa","alfa bus", child2);
        assertSemantics("AND vehiclebrand:bmw expensivetv","bmw motorcycle", child2);
        assertSemantics("AND vw car","vw cars", child2);
        assertSemantics("vehiclebrand:skoda","skoda cars", child2);
    }

    @Test
    public void testGrandchild() {
        assertSemantics("vehiclebrand:skoda","audi cars", grandchild);
        assertSemantics("vehiclebrand:alfa","alfa bus", grandchild);
        assertSemantics("AND vehiclebrand:bmw expensivetv","bmw motorcycle", grandchild);
        assertSemantics("vehiclebrand:skoda","vw cars", grandchild);
        assertSemantics("vehiclebrand:skoda","skoda cars", grandchild);
    }

    @Test
    public void testRuleBaseNames() {
        assertEquals("parent", parent.getName());
        assertEquals("child1", child1.getName());
        assertEquals("child2", child2.getName());
        assertEquals("grandchild", grandchild.getName());
    }

    @Test
    public void testSearcher() {
        assertSemantics("vehiclebrand:skoda","vw cars",   "");
        assertSemantics("vehiclebrand:skoda","vw cars",   "&rules.rulebase=grandchild");
        assertSemantics("vehiclebrand:skoda","vw cars",   "&rules.rulebase=grandchild.sd");
        try {
            assertSemantics("AND vw cars",       "vw cars",   "&rules.rulebase=doesntexist");
            fail("No exception on missing rule base");
        }
        catch (RuleBaseException e) {
            // Success
        }
        assertSemantics("AND vw cars",       "vw cars",   "&rules.rulebase=grandchild.sd&rules.off");
        assertSemantics("AND vw cars",       "vw cars",   "&rules.off");

        assertSemantics("AND vw car",        "vw cars",   "&rules.rulebase=child2");
        assertSemantics("vehiclebrand:skoda","skoda cars","&rules.rulebase=child2");

        assertSemantics("vehiclebrand:skoda","audi cars", "&rules.rulebase=child1");
        assertSemantics("vehiclebrand:skoda","vw cars",   "&rules.rulebase=child1");
        assertSemantics("AND skoda car",     "skoda cars","&rules.rulebase=child1");

        assertSemantics("AND vw car",        "vw cars",   "&rules.rulebase=parent");
        assertSemantics("AND skoda car",     "skoda cars","&rules.rulebase=parent");
    }

    protected void assertSemantics(String result,String input,String ruleSelection) {
        Query query = new Query("?query=" + QueryTestCase.httpEncode(input) + "&tracelevel=0&tracelevel.rules=0" + ruleSelection);
        doSearch(searcher, query, 0,10);
        assertEquals(result, query.getModel().getQueryTree().getRoot().toString());
    }

    private Result doSearch(Searcher searcher, Query query, int offset, int hits) {
        query.setOffset(offset);
        query.setHits(hits);
        return createExecution(searcher).search(query);
    }

    private Execution createExecution(Searcher searcher) {
        Execution.Context context = new Execution.Context(null, null, null,
                                                          new RendererRegistry(MoreExecutors.directExecutor()), new SimpleLinguistics());
        return new Execution(chainedAsSearchChain(searcher), context);
    }

    private Chain<Searcher> chainedAsSearchChain(Searcher topOfChain) {
        List<Searcher> searchers = new ArrayList<>();
        searchers.add(topOfChain);
        return new Chain<>(searchers);
    }

}
