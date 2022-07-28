// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.test;

import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.prelude.semantics.engine.RuleBaseLinguistics;
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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author bratseth
 */
public class ProductionRuleTestCase {

    @Test
    void testProductionRule() {
        var linguistics = new RuleBaseLinguistics(new SimpleLinguistics());
        TermCondition term = new TermCondition("sony", linguistics);
        NamedCondition named = new NamedCondition("brand", term);
        ConditionReference reference = new ConditionReference("brand");

        TermProduction termProduction = new ReferenceTermProduction("brand", "brand", false);
        ProductionList productionList = new ProductionList();
        productionList.addProduction(termProduction);

        ProductionRule rule = new ReplacingProductionRule();
        rule.setCondition(reference);
        rule.setProduction(productionList);

        // To initialize the condition reference...
        RuleBase ruleBase = new RuleBase("test");
        ruleBase.addCondition(named);
        ruleBase.addRule(rule);
        ruleBase.initialize();

        assertTrue(rule.matchReferences().contains("brand"), "Brand is referenced");

        Query query = new Query("?query=sony");
        RuleEvaluation e = new Evaluation(query, null).freshRuleEvaluation();
        assertTrue(rule.matches(e));
        rule.produce(e);
        assertEquals("WEAKAND(100) brand:sony", query.getModel().getQueryTree().getRoot().toString());
    }

}
