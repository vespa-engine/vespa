// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.rule;

import java.util.Set;

import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.TermType;
import com.yahoo.prelude.semantics.engine.EvaluationException;
import com.yahoo.prelude.semantics.engine.Match;
import com.yahoo.prelude.semantics.engine.ReferencedMatches;
import com.yahoo.prelude.semantics.engine.RuleEvaluation;
import com.yahoo.protect.Validator;

/**
 * A term produced by a production rule which takes it's actual term value
 * from one or more terms matched in the condition
 *
 * @author bratseth
 */
public class ReferenceTermProduction extends TermProduction {

    private String reference;

    /**
     * Creates a new produced reference term
     *
     * @param reference the label of the condition this should take it's value from
     */
    public ReferenceTermProduction(String reference) {
        super();
        setReference(reference);
    }

    /**
     * Creates a new produced reference term
     *
     * @param reference the label of the condition this should take it's value from
     * @param termType the type of the term to produce
     */
    public ReferenceTermProduction(String reference, TermType termType) {
        super(termType);
        setReference(reference);
    }

    /**
     * Creates a new produced reference term
     *
     * @param label the label of the produced term
     * @param reference the label of the condition this should take it's value from
     */
    public ReferenceTermProduction(String label, String reference) {
        super(label);
        setReference(reference);
    }

    /**
     * Creates a new produced reference term
     *
     * @param label the label of the produced term
     * @param reference the label of the condition this should take it's value from
     * @param termType the type of term to produce
     */
    public ReferenceTermProduction(String label, String reference, TermType termType) {
        super(label, termType);
        setReference(reference);
    }

    /** The label of the condition this should take its value from, never null */
    public void setReference(String reference) {
        Validator.ensureNotNull("reference name of a produced reference term",reference);
        this.reference = reference;
    }

    /** Returns the label of the condition this should take its value from, never null */
    public String getReference() { return reference; }

    void addMatchReferences(Set<String> matchReferences) {
        matchReferences.add(reference);
    }

    public void produce(RuleEvaluation e, int offset) {
        ReferencedMatches referencedMatches = e.getReferencedMatches(reference);
        if (referencedMatches == null)
            throw new EvaluationException("Referred match '" + reference + "' not found");
        if (replacing) {
            replaceMatches(e, referencedMatches);
        }
        else {
            addMatches(e, referencedMatches);
        }
    }

    public void replaceMatches(RuleEvaluation e, ReferencedMatches referencedMatches) {
        Item referencedItem = referencedMatches.toItem(getLabel());
        if (referencedItem == null) return;
        e.removeMatches(referencedMatches);
        insertMatch(e, referencedMatches.matchIterator().next(), referencedItem, 0);
    }

    private void addMatches(RuleEvaluation e, ReferencedMatches referencedMatches) {
        Item referencedItem = referencedMatches.toItem(getLabel());
        if (referencedItem == null) return;
        e.addItem(referencedItem, getTermType());
    }

    public String toInnerTermString() {
        return getLabelString() + "[" + reference + "]";
    }

}
