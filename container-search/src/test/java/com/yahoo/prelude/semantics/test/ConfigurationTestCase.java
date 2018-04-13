// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.test;

import com.google.common.util.concurrent.MoreExecutors;
import com.yahoo.component.chain.Chain;
import com.yahoo.config.subscription.ConfigGetter;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.prelude.semantics.SemanticRulesConfig;
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

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

/**
 * Tests creating a set of rule bases (the same set as in inheritingrules) from config
 *
 * @author bratseth
 */
public class ConfigurationTestCase {

    private static final String root="src/test/java/com/yahoo/prelude/semantics/test/rulebases/";

    private static final SemanticSearcher searcher;
    private static final SemanticRulesConfig semanticRulesConfig;

    static {
        semanticRulesConfig = new ConfigGetter<>(SemanticRulesConfig.class).getConfig("file:" + root + "semantic-rules.cfg");
        searcher=new SemanticSearcher(semanticRulesConfig);
    }

    protected void assertSemantics(String result, String input, String baseName) {
        Query query = new Query(QueryTestCase.httpEncode("?query=" + input + "&tracelevel=0&tracelevel.rules=0&rules.rulebase=" + baseName));
        doSearch(searcher, query, 0, 10);
        assertEquals(result, query.getModel().getQueryTree().getRoot().toString());
    }

    protected void assertSemanticsRulesOff(String result, String input) {
        Query query = new Query(QueryTestCase.httpEncode("?query=" + input + "&tracelevel=0&tracelevel.rules=0&rules.off"));
        doSearch(searcher, query, 0, 10);
        assertEquals(result, query.getModel().getQueryTree().getRoot().toString());
    }

    @Test
    public void testReadingConfigurationRuleBase() {
        RuleBase parent=searcher.getRuleBase("parent");
        assertNotNull(parent);
        assertEquals("parent",parent.getName());
        assertEquals("semantic-rules.cfg",parent.getSource());
    }

    @Test
    public void testParent() throws Exception {
        assertSemantics("vehiclebrand:audi","audi cars","parent");
        assertSemantics("vehiclebrand:alfa","alfa bus","parent");
        assertSemantics("AND vehiclebrand:bmw expensivetv","bmw motorcycle","parent.sr");
        assertSemantics("AND vw car",       "vw cars","parent");
        assertSemantics("AND skoda car",    "skoda cars","parent.sr");
    }

    @Test
    public void testChild1() throws Exception {
        assertSemantics("vehiclebrand:skoda","audi cars","child1.sr");
        assertSemantics("vehiclebrand:alfa", "alfa bus","child1");
        assertSemantics("AND vehiclebrand:bmw expensivetv","bmw motorcycle","child1");
        assertSemantics("vehiclebrand:skoda","vw cars","child1");
        assertSemantics("AND skoda car",     "skoda cars","child1");
    }

    @Test
    public void testChild2() throws Exception {
        assertSemantics("vehiclebrand:audi","audi cars","child2");
        assertSemantics("vehiclebrand:alfa","alfa bus","child2.sr");
        assertSemantics("AND vehiclebrand:bmw expensivetv","bmw motorcycle","child2.sr");
        assertSemantics("AND vw car","vw cars","child2");
        assertSemantics("vehiclebrand:skoda","skoda cars","child2");
    }

    @Test
    public void testGrandchild() throws Exception {
        assertSemantics("vehiclebrand:skoda","audi cars","grandchild.sr");
        assertSemantics("vehiclebrand:alfa","alfa bus","grandchild");
        assertSemantics("AND vehiclebrand:bmw expensivetv","bmw motorcycle","grandchild");
        assertSemantics("vehiclebrand:skoda","vw cars","grandchild");
        assertSemantics("vehiclebrand:skoda","skoda cars","grandchild");
    }

    @Test
    public void testSearcher() {
        assertSemantics("vehiclebrand:skoda", "vw cars",   "grandchild");
        assertSemantics("vehiclebrand:skoda", "vw cars",   "grandchild.sd");
        try {
            assertSemantics("AND vw cars",    "vw cars",   "doesntexist");
            fail("No exception on missing rule base");
        }
        catch (RuleBaseException e) {
            // Success
        }
        assertSemantics("AND vw cars",       "vw cars",   "grandchild.sd&rules.off");
        assertSemanticsRulesOff("AND vw cars",       "vw cars");

        assertSemantics("AND vw car",        "vw cars",   "child2");
        assertSemantics("vehiclebrand:skoda","skoda cars","child2");

        assertSemantics("vehiclebrand:skoda","audi cars", "child1");
        assertSemantics("vehiclebrand:skoda","vw cars",   "child1");
        assertSemantics("AND skoda car",     "skoda cars","child1");

        assertSemantics("AND vw car",        "vw cars",   "parent");
        assertSemantics("AND skoda car",     "skoda cars","parent");
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
