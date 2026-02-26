// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.rule;

import com.yahoo.prelude.query.CompositeItem;
import com.yahoo.prelude.query.TermType;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.prelude.semantics.engine.Match;
import com.yahoo.prelude.semantics.engine.RuleEvaluation;
import com.yahoo.protect.Validator;
import com.yahoo.search.query.QueryTree;

import java.util.List;

/**
 * A literal term produced by a production rule
 *
 * @author bratseth
 */
public class LiteralTermProduction extends TermProduction {

    private String literal;

    /**
     * Creates a new produced literal term
     *
     * @param literal the label of the condition this should take its value from
     */
    public LiteralTermProduction(String literal) {
        super();
        setLiteral(literal);
    }

    /**
     * Creates a new produced literal term
     *
     * @param literal the label of the condition this should take its value from
     * @param termType the type of term to produce
     */
    public LiteralTermProduction(String literal, TermType termType) {
        super(termType);
        setLiteral(literal);
    }

    /**
     * Creates a new produced literal term
     *
     * @param label the label of the produced term
     * @param literal this term word
     * @param termType the type of term to produce
     */
    public LiteralTermProduction(String label, String literal, TermType termType) {
        super(label, termType);
        setLiteral(literal);
    }

    /** The literal term value, never null */
    public void setLiteral(String literal) {
        Validator.ensureNotNull("A produced term", literal);
        this.literal=literal;
    }

    /** Returns the term word produced, never null */
    public String getLiteral() { return literal; }

    public void produce(RuleEvaluation e, int offset) {
        WordItem newItem = new WordItem(literal, getLabel(), true);
        if (replacing) {
            Match matched = e.getNonreferencedMatch(0);
            newItem.setWeight(matched.getItem().getWeight());
            insertMatch(e, matched, List.of(newItem), offset);
        }
        else {
            newItem.setWeight(getWeight());
            if (e.getTraceLevel() >= 6)
                e.trace(6, "Adding '" + newItem + "'");
            if (shouldInsertAtMatch(e)) {
                // Add to the match's parent when it's a nested composite with default type
                Match matched = e.getNonreferencedMatch(0);
                insertMatch(e, matched, List.of(newItem), offset);
            }
            else {
                // Use root-level combining for specific types (RANK, OR, etc.) and non-nested cases
                e.addItems(List.of(newItem), getTermType());
            }
        }
    }

    private boolean shouldInsertAtMatch(RuleEvaluation e) {
        if (e.getNonreferencedMatchCount() == 0) return false;
        return shouldInsertAtMatch(e.getNonreferencedMatch(0));
    }

    public String toInnerTermString() {
        return getLabelString() + literal;
    }

}
