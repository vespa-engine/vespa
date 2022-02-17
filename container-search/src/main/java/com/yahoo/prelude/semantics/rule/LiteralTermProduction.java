// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.rule;

import com.yahoo.prelude.query.TermType;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.prelude.semantics.engine.Match;
import com.yahoo.prelude.semantics.engine.RuleEvaluation;
import com.yahoo.protect.Validator;

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
     * @param literal the label of the condition this should take it's value from
     */
    public LiteralTermProduction(String literal) {
        super();
        setLiteral(literal);
    }

    /**
     * Creates a new produced literal term
     *
     * @param literal the label of the condition this should take it's value from
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
        WordItem newItem = new WordItem(literal, getLabel());
        if (replacing) {
            Match matched = e.getNonreferencedMatch(0);
            newItem.setWeight(matched.getItem().getWeight());
            insertMatch(e, matched, List.of(newItem), offset);
        }
        else {
            newItem.setWeight(getWeight());
            if (e.getTraceLevel() >= 6)
                e.trace(6, "Adding '" + newItem + "'");
            e.addItems(List.of(newItem), getTermType());
        }
    }

    public String toInnerTermString() {
        return getLabelString() + literal;
    }

}
