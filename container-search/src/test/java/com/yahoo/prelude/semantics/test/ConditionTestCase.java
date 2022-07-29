// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.test;

import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.prelude.semantics.engine.RuleBaseLinguistics;
import com.yahoo.search.Query;
import com.yahoo.prelude.semantics.RuleBase;
import com.yahoo.prelude.semantics.engine.Evaluation;
import com.yahoo.prelude.semantics.rule.ChoiceCondition;
import com.yahoo.prelude.semantics.rule.ConditionReference;
import com.yahoo.prelude.semantics.rule.NamedCondition;
import com.yahoo.prelude.semantics.rule.ProductionList;
import com.yahoo.prelude.semantics.rule.ProductionRule;
import com.yahoo.prelude.semantics.rule.ReplacingProductionRule;
import com.yahoo.prelude.semantics.rule.SequenceCondition;
import com.yahoo.prelude.semantics.rule.TermCondition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author bratseth
 */
public class ConditionTestCase {

    @Test
    void testTermCondition() {
        var linguistics = new RuleBaseLinguistics(new SimpleLinguistics());
        TermCondition term = new TermCondition("foo", linguistics);
        Query query = new Query("?query=foo");
        assertTrue(term.matches(new Evaluation(query, null).freshRuleEvaluation()));
    }

    @Test
    void testSequenceCondition() {
        var linguistics = new RuleBaseLinguistics(new SimpleLinguistics());
        TermCondition term1 = new TermCondition("foo", linguistics);
        TermCondition term2 = new TermCondition("bar", linguistics);
        SequenceCondition sequence = new SequenceCondition();
        sequence.addCondition(term1);
        sequence.addCondition(term2);
        Query query = new Query("?query=foo+bar");
        assertTrue(sequence.matches(new Evaluation(query, null).freshRuleEvaluation()), query + " matches " + sequence);
        Query query2 = new Query("?query=foo");
        assertFalse(sequence.matches(new Evaluation(query2, null).freshRuleEvaluation()), query2 + " does not match " + sequence);
        Query query3 = new Query("?query=bar");
        assertFalse(sequence.matches(new Evaluation(query3, null).freshRuleEvaluation()), query3 + " does not match " + sequence);
    }

    @Test
    void testChoiceCondition() {
        var linguistics = new RuleBaseLinguistics(new SimpleLinguistics());
        TermCondition term1 = new TermCondition("foo", linguistics);
        TermCondition term2 = new TermCondition("bar", linguistics);
        ChoiceCondition choice = new ChoiceCondition();
        choice.addCondition(term1);
        choice.addCondition(term2);
        Query query1 = new Query("?query=foo+bar");
        assertTrue(choice.matches(new Evaluation(query1, null).freshRuleEvaluation()), query1 + " matches " + choice);
        Query query2 = new Query("?query=foo");
        assertTrue(choice.matches(new Evaluation(query2, null).freshRuleEvaluation()), query2 + " matches " + choice);
        Query query3 = new Query("?query=bar");
        assertTrue(choice.matches(new Evaluation(query3, null).freshRuleEvaluation()), query3 + " matches " + choice);
    }

    @Test
    void testNamedConditionReference() {
        var linguistics = new RuleBaseLinguistics(new SimpleLinguistics());
        TermCondition term = new TermCondition("foo", linguistics);
        NamedCondition named = new NamedCondition("cond", term);
        ConditionReference reference = new ConditionReference("cond");

        // To initialize the condition reference...
        ProductionRule rule = new ReplacingProductionRule();
        rule.setCondition(reference);
        rule.setProduction(new ProductionList());
        RuleBase ruleBase = new RuleBase("test");
        ruleBase.addCondition(named);
        ruleBase.addRule(rule);
        ruleBase.initialize();

        Query query = new Query("?query=foo");
        assertTrue(reference.matches(new Evaluation(query, null).freshRuleEvaluation()), query + "  matches " + reference);
    }

}
