// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.engine;

import com.yahoo.prelude.semantics.rule.ReplacingProductionRule;
import com.yahoo.search.Query;
import com.yahoo.prelude.query.QueryCanonicalizer;
import com.yahoo.prelude.semantics.RuleBase;
import com.yahoo.prelude.semantics.RuleBaseException;
import com.yahoo.prelude.semantics.rule.ProductionRule;

import java.util.ListIterator;

/**
 * Evaluates the rules of a rule base. This method is thread safe on analyze calls, but
 * not on modification calls.
 *
 * @author bratseth
 */
public class RuleEngine {

    private final RuleBase rules;

    public RuleEngine(RuleBase rules) {
        this.rules=rules;
    }

    /**
     * Evaluates a rule base over a query
     *
     * @param query the query to evaluate
     * @param traceLevel the level of tracing to do
     * @return the error caused by analyzing the query, or null if there was no error
     *         If there is an error, this query is destroyed (unusable)
     */
    public String evaluate(Query query, int traceLevel) {
        // TODO: This is O(query size*rule base size). We'll eventually need to create indices
        //       on rules to look up rule candidates per term to make it O(query size) instead
        //       Probably create indices on the first term like Prolog implementations use to

        boolean matchedAnything = false;
        Evaluation evaluation = new Evaluation(query, rules, traceLevel);
        if (traceLevel >= 2)
            evaluation.trace(2,"Evaluating query '" + evaluation.getQuery().getModel().getQueryTree().getRoot() + "':");
        for (ListIterator<ProductionRule> i = rules.ruleIterator(); i.hasNext(); ) {
            evaluation.reset();
            ProductionRule rule = i.next();
            boolean matched = matchRuleAtAllStartPoints(evaluation,rule);
            matchedAnything |= matched;
        }

        if ( ! matchedAnything) return null;

        String error = QueryCanonicalizer.canonicalize(query);
        query.trace("SemanticSearcher: Rewrote query",true,1);
        return error;
    }

    /** Match a rule at any starting point in the query */
    private boolean matchRuleAtAllStartPoints(Evaluation evaluation, ProductionRule rule) {
        boolean matchedAtLeastOnce = false;
        int iterationCount = 0;

         // Test if it is a removal rule, if so iterate backwards so that precalculated
         // replacement positions do not become invalid as the query shrink
        boolean removalRule = false;
        if (rule instanceof ReplacingProductionRule && rule.getProduction().toString().isEmpty()) { // empty replacement
            removalRule = true;
            evaluation.setToLast();
        }

        int loopLimit = Math.max(15, evaluation.getQuerySize() * 3);

        while (evaluation.currentItem() != null) {
            boolean matched = matchRule(evaluation,rule);
            if (matched) {
                if (removalRule)
                    evaluation.resetToLast();
                else
                    evaluation.reset();
                matchedAtLeastOnce = true;
                if (rule.isLoop()) break;
            }
            else {
                if (removalRule)
                    evaluation.previous();
                else
                    evaluation.next();
            }

            if (matched && iterationCount++ > loopLimit) {
                throw new RuleBaseException("Rule '" + rule + "' has matched '" +
                                            evaluation.getQuery().getModel().getQueryTree().getRoot() +
                                            "' " + loopLimit + " times, aborting");
            }
        }

        return matchedAtLeastOnce;
    }

    /**
     * Matches a rule at the current starting point of the evaluation, and carries
     * out the production if there is a match
     *
     * @return whether this rule matched
     */
    // TODO: Code cleanup
    private boolean matchRule(Evaluation evaluation, ProductionRule rule) {
        RuleEvaluation ruleEvaluation=evaluation.freshRuleEvaluation();

        ruleEvaluation.indentTrace();
        if (ruleEvaluation.getTraceLevel() >= 3) {
            ruleEvaluation.trace(3,"Evaluating rule '" + rule +
                                   "' on '" + ruleEvaluation.getEvaluation().getQuery().getModel().getQueryTree().getRoot() +
                                   "' at '" + ruleEvaluation.currentItem() + "':");
        }

        ruleEvaluation.indentTrace();

        boolean matches = rule.matches(ruleEvaluation);

        boolean matchedBefore = false;
        int currentMatchDigest = ruleEvaluation.calculateMatchDigest(rule);
        if (evaluation.hasMatchDigest(currentMatchDigest))
            matchedBefore = true;

        boolean queryGotShorter = false;
        if (evaluation.getPreviousQuerySize() > evaluation.getQuerySize())
            queryGotShorter = true;

        boolean doProduction =! matchedBefore || queryGotShorter;

        ruleEvaluation.unindentTrace();

        if (ruleEvaluation.getTraceLevel() >= 2) {
            if (matches && doProduction)
                ruleEvaluation.trace(2,"Matched rule '" + rule + "' at " + ruleEvaluation.previousItem());
            else if ( ! matches)
                ruleEvaluation.trace(2,"Did not match rule '" + rule + "' at " + ruleEvaluation.currentItem());
            else
                ruleEvaluation.trace(2,"Ignoring repeated match of '" + rule + "'");
        }

        ruleEvaluation.unindentTrace();

        if (!matches || !doProduction) return false;

        // Do production barrier

        evaluation.addMatchDigest(currentMatchDigest);
        String preQuery = null;
        if (evaluation.getTraceLevel()>=1) {
            preQuery = evaluation.getQuery().getModel().getQueryTree().getRoot().toString();
        }
        rule.produce(ruleEvaluation);
        if (evaluation.getTraceLevel() >= 1) {
            evaluation.trace(1,"Transforming '" + preQuery + "' to '" +
                               evaluation.getQuery().getModel().getQueryTree().getRoot().toString()
                               + "' since '" + rule + "' matched");
        }

        return true;
    }

}
