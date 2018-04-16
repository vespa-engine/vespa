// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.test;

import com.yahoo.search.Query;
import com.yahoo.prelude.semantics.RuleBase;
import com.yahoo.prelude.semantics.engine.Evaluation;
import com.yahoo.prelude.semantics.engine.RuleEvaluation;
import com.yahoo.prelude.semantics.rule.ConditionReference;
import com.yahoo.prelude.semantics.rule.NamedCondition;
import com.yahoo.prelude.semantics.rule.ProductionList;
import com.yahoo.prelude.semantics.rule.ProductionRule;
import com.yahoo.prelude.semantics.rule.ReferenceTermProduction;
import com.yahoo.prelude.semantics.rule.ReplacingProductionRule;
import com.yahoo.prelude.semantics.rule.TermCondition;
import com.yahoo.prelude.semantics.rule.TermProduction;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author bratseth
 */
public class ProductionRuleTestCase {

    @Test
    public void testProductionRule() {
        TermCondition term = new TermCondition("sony");
        NamedCondition named = new NamedCondition("brand", term);
        ConditionReference reference = new ConditionReference("brand");

        TermProduction termProduction = new ReferenceTermProduction("brand", "brand");
        ProductionList productionList = new ProductionList();
        productionList.addProduction(termProduction);

        ProductionRule rule = new ReplacingProductionRule();
        rule.setCondition(reference);
        rule.setProduction(productionList);

        // To initialize the condition reference...
        RuleBase ruleBase = new RuleBase();
        ruleBase.setName("test");
        ruleBase.addCondition(named);
        ruleBase.addRule(rule);
        ruleBase.initialize();

        assertTrue("Brand is referenced", rule.matchReferences().contains("brand"));

        Query query = new Query("?query=sony");
        RuleEvaluation e = new Evaluation(query).freshRuleEvaluation();
        assertTrue(rule.matches(e));
        rule.produce(e);
        assertEquals("brand:sony", query.getModel().getQueryTree().getRoot().toString());
    }

}
