// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.test;

import com.yahoo.search.Query;
import com.yahoo.prelude.semantics.RuleBase;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests rule bases using automatas for matching
 *
 * @author bratseth
 */
public class AutomataTestCase extends RuleBaseAbstractTestCase {

    private final String root="src/test/java/com/yahoo/prelude/semantics/test/rulebases/";

    public AutomataTestCase() {
        super("automatarules.sr", "semantics.fsa");
    }

    @Test
    void testAutomataRuleBase() {
        RuleBase ruleBase = searcher.getDefaultRuleBase();
        assertEquals(RuleBase.class, ruleBase.getClass());
        assertTrue(ruleBase.getSource().endsWith(root + "automatarules.sr"));
        assertEquals(root + "semantics.fsa", ruleBase.getAutomataFile());

        Query query = new Query("?query=sony+digital+camera");
        ruleBase.analyze(query, 0);
        assertEquals("RANK (WEAKAND(100) sony digital camera) dsp1:sony dsp5:digicamera", query.getModel().getQueryTree().getRoot().toString());

        query = new Query("?query=sony+digital+camera&rules.reload");
        ruleBase = searcher.getDefaultRuleBase();
        assertTrue(ruleBase.getSource().endsWith(root + "automatarules.sr"));
        assertEquals(root + "semantics.fsa", ruleBase.getAutomataFile());
        ruleBase.analyze(query, 0);
        assertEquals("RANK (WEAKAND(100) sony digital camera) dsp1:sony dsp5:digicamera", query.getModel().getQueryTree().getRoot().toString());
    }

    @Test
    void testAutomataSingleQuery() {
        assertSemantics("RANK sony dsp1:sony", "sony");
    }

    @Test
    void testAutomataFilterIsIgnored() {
        assertSemantics("RANK sony |something dsp1:sony", "sony&filter=something");
        assertSemantics("RANK something |sony", "something&filter=sony");
    }

    @Test
    void testAutomataPluralMatches() {
        assertSemantics("RANK sonys dsp1:sony", "sonys");

        assertSemantics("RANK (AND car cleaner) dsp1:\"car cleaners\" dsp5:\"car cleaners\"", "car cleaner");

        assertSemantics("RANK (AND sony digitals cameras) dsp1:sony dsp5:digicamera", "sony digitals cameras");
    }

    @Test
    void testMatchingMultipleAutomataConditionsSingleWord() {
        assertSemantics("RANK carpenter dsp1:carpenter dsp5:carpenter", "carpenter");
    }

    @Test
    void testMatchingMultipleAutomataConditionsPhrase() {
        assertSemantics("RANK (AND car cleaners) dsp1:\"car cleaners\" dsp5:\"car cleaners\"", "car cleaners");
    }

    // TODO: Make this work again
    @Test
    @Disabled
    void testReplaceOnNoMatch() {
        assertSemantics("nomatch:sonny", "sonny&donomatch");
        assertSemantics("RANK sony dsp1:sony", "sony&donomatch");
        assertSemantics("RANK sonys dsp1:sony", "sonys&donomatch");
        assertSemantics("AND nomatch:sonny nomatch:boy", "sonny boy&donomatch");
        assertSemantics("RANK (AND sony nomatch:boy) dsp1:sony", "sony boy&donomatch");
    }

}
