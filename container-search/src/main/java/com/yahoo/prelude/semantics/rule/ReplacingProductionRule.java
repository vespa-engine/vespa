// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.rule;

import com.yahoo.prelude.semantics.engine.Match;
import com.yahoo.prelude.semantics.engine.RuleEvaluation;

/**
 * A production rule which <i>replaces</i> matched terms by the production
 *
 * @author bratseth
 */
public class ReplacingProductionRule extends ProductionRule {

    /** Carries out the production of this rule */
    public void produce(RuleEvaluation e) {
        removeNonreferencedMatches(e);
        if (e.getTraceLevel() >= 5) {
            e.trace(5,"Removed terms to get '" + e.getEvaluation().getQuery().getModel().getQueryTree().getRoot() + "', will add terms");
        }
        super.produce(e);
    }

    /** Remove items until there's only one item left */
    private void removeNonreferencedMatches(RuleEvaluation e) {
        int itemCount = e.getEvaluation().getQuerySize();

        // Remove items backwards to ease index handling
        for (int i = e.getNonreferencedMatchCount() - 1; i >= 0; i--) {
            // Ensure we don't produce an empty query
            if (getProduction().getTermCount() == 0 && itemCount == 1)
                break;
            itemCount--;

            Match match = e.getNonreferencedMatch(i);
            match.getItem().getParent().removeItem(match.getPosition());
        }
    }

    protected String getSymbol() { return "->"; }

}
