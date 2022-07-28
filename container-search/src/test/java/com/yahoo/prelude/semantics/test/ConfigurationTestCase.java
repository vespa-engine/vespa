// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.test;

import com.yahoo.component.chain.Chain;
import com.yahoo.config.subscription.ConfigGetter;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.prelude.semantics.RuleBase;
import com.yahoo.prelude.semantics.RuleBaseException;
import com.yahoo.prelude.semantics.SemanticRulesConfig;
import com.yahoo.prelude.semantics.SemanticSearcher;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.test.QueryTestCase;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests creating a set of rule bases (the same set as in inheritingrules) from config
 *
 * @author bratseth
 */
@SuppressWarnings("deprecation")
public class ConfigurationTestCase {

    private static final String root="src/test/java/com/yahoo/prelude/semantics/test/rulebases/";

    private static final SemanticSearcher searcher;
    private static final SemanticRulesConfig semanticRulesConfig;

    static {
        semanticRulesConfig = new ConfigGetter<>(SemanticRulesConfig.class).getConfig("file:" + root + "semantic-rules.cfg");
        searcher = new SemanticSearcher(semanticRulesConfig, new SimpleLinguistics());
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
    void testReadingConfigurationRuleBase() {
        RuleBase parent = searcher.getRuleBase("parent");
        assertNotNull(parent);
        assertEquals("parent", parent.getName());
        assertEquals("semantic-rules.cfg", parent.getSource());
    }

    @Test
    void testParent() {
        assertSemantics("WEAKAND(100) vehiclebrand:audi", "audi cars", "parent");
        assertSemantics("WEAKAND(100) vehiclebrand:alfa", "alfa bus", "parent");
        assertSemantics("AND (WEAKAND(100) vehiclebrand:bmw) expensivetv", "bmw motorcycle", "parent.sr");
        assertSemantics("WEAKAND(100) vw car",        "vw cars", "parent");
        assertSemantics("WEAKAND(100) skoda car",     "skoda cars", "parent.sr");
    }

    @Test
    void testChild1() {
        assertSemantics("WEAKAND(100) vehiclebrand:skoda", "audi cars", "child1.sr");
        assertSemantics("WEAKAND(100) vehiclebrand:alfa",  "alfa bus", "child1");
        assertSemantics("AND (WEAKAND(100) vehiclebrand:bmw) expensivetv", "bmw motorcycle", "child1");
        assertSemantics("WEAKAND(100) vehiclebrand:skoda", "vw cars", "child1");
        assertSemantics("WEAKAND(100) skoda car",      "skoda cars", "child1");
    }

    @Test
    void testChild2() {
        assertSemantics("WEAKAND(100) vehiclebrand:audi", "audi cars", "child2");
        assertSemantics("WEAKAND(100) vehiclebrand:alfa", "alfa bus", "child2.sr");
        assertSemantics("AND (WEAKAND(100) vehiclebrand:bmw) expensivetv", "bmw motorcycle", "child2.sr");
        assertSemantics("WEAKAND(100) vw car", "vw cars", "child2");
        assertSemantics("WEAKAND(100) vehiclebrand:skoda", "skoda cars", "child2");
    }

    @Test
    void testGrandchild() {
        assertSemantics("WEAKAND(100) vehiclebrand:skoda", "audi cars", "grandchild.sr");
        assertSemantics("WEAKAND(100) vehiclebrand:alfa", "alfa bus", "grandchild");
        assertSemantics("AND (WEAKAND(100) vehiclebrand:bmw) expensivetv", "bmw motorcycle", "grandchild");
        assertSemantics("WEAKAND(100) vehiclebrand:skoda", "vw cars", "grandchild");
        assertSemantics("WEAKAND(100) vehiclebrand:skoda", "skoda cars", "grandchild");
    }

    @Test
    void testSearcher() {
        assertSemantics("WEAKAND(100) vehiclebrand:skoda", "vw cars",   "grandchild");
        assertSemantics("WEAKAND(100) vehiclebrand:skoda", "vw cars",   "grandchild.sd");
        try {
            assertSemantics("WEAKAND(100) vw cars",    "vw cars",   "doesntexist");
            fail("No exception on missing rule base");
        }
        catch (RuleBaseException e) {
            // Success
        }
        assertSemantics("WEAKAND(100) vw cars",       "vw cars",   "grandchild.sd&rules.off");
        assertSemanticsRulesOff("WEAKAND(100) vw cars",       "vw cars");

        assertSemantics("WEAKAND(100) vw car",        "vw cars",   "child2");
        assertSemantics("WEAKAND(100) vehiclebrand:skoda", "skoda cars", "child2");

        assertSemantics("WEAKAND(100) vehiclebrand:skoda", "audi cars", "child1");
        assertSemantics("WEAKAND(100) vehiclebrand:skoda", "vw cars",   "child1");
        assertSemantics("WEAKAND(100) skoda car",     "skoda cars", "child1");

        assertSemantics("WEAKAND(100) vw car",        "vw cars",   "parent");
        assertSemantics("WEAKAND(100) skoda car",     "skoda cars", "parent");
    }

    private Result doSearch(Searcher searcher, Query query, int offset, int hits) {
        query.setOffset(offset);
        query.setHits(hits);
        return createExecution(searcher).search(query);
    }

    private Execution createExecution(Searcher searcher) {
        return new Execution(chainedAsSearchChain(searcher), Execution.Context.createContextStub());
    }

    private Chain<Searcher> chainedAsSearchChain(Searcher topOfChain) {
        List<Searcher> searchers = new ArrayList<>();
        searchers.add(topOfChain);
        return new Chain<>(searchers);
    }

}
