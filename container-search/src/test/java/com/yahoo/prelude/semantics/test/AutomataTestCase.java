// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.test;

import com.yahoo.search.Query;
import com.yahoo.prelude.semantics.RuleBase;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
    public void testAutomataRuleBase() {
        RuleBase ruleBase=searcher.getDefaultRuleBase();
        assertEquals(RuleBase.class,ruleBase.getClass());
        assertTrue(ruleBase.getSource().endsWith(root + "automatarules.sr"));
        assertEquals(root + "semantics.fsa",ruleBase.getAutomataFile());

        Query query=new Query("?query=sony+digital+camera");
        ruleBase.analyze(query,0);
        assertEquals("RANK (AND sony digital camera) dsp1:sony dsp5:digicamera", query.getModel().getQueryTree().getRoot().toString());

        query=new Query("?query=sony+digital+camera&rules.reload");
        ruleBase=searcher.getDefaultRuleBase();
        assertTrue(ruleBase.getSource().endsWith(root + "automatarules.sr"));
        assertEquals(root + "semantics.fsa",ruleBase.getAutomataFile());
        ruleBase.analyze(query,0);
        assertEquals("RANK (AND sony digital camera) dsp1:sony dsp5:digicamera", query.getModel().getQueryTree().getRoot().toString());
    }

    @Test
    public void testAutomataSingleQuery() {
        assertSemantics("RANK sony dsp1:sony","sony");
    }

    @Test
    public void testAutomataFilterIsIgnored() {
        assertSemantics("RANK sony |something dsp1:sony","sony&filter=something");
        assertSemantics("RANK something |sony","something&filter=sony");
    }

    @Test
    public void testAutomataPluralMatches() {
        assertSemantics("RANK sonys dsp1:sony","sonys");

        assertSemantics("RANK (AND car cleaner) dsp1:\"car cleaners\" dsp5:\"car cleaners\"","car cleaner");

        assertSemantics("RANK (AND sony digitals cameras) dsp1:sony dsp5:digicamera","sony digitals cameras");
    }

    @Test
    public void testMatchingMultipleAutomataConditionsSingleWord() {
        assertSemantics("RANK carpenter dsp1:carpenter dsp5:carpenter","carpenter");
    }

    @Test
    public void testMatchingMultipleAutomataConditionsPhrase() {
        assertSemantics("RANK (AND car cleaners) dsp1:\"car cleaners\" dsp5:\"car cleaners\"","car cleaners");
    }

    @Test
    @Ignore // TODO: Make this work again
    public void testReplaceOnNoMatch() {
        assertSemantics("nomatch:sonny","sonny&donomatch");
        assertSemantics("RANK sony dsp1:sony","sony&donomatch");
        assertSemantics("RANK sonys dsp1:sony","sonys&donomatch");
        assertSemantics("AND nomatch:sonny nomatch:boy","sonny boy&donomatch");
        assertSemantics("RANK (AND sony nomatch:boy) dsp1:sony","sony boy&donomatch");
    }

}
