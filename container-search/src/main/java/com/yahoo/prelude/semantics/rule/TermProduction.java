// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.rule;

import com.yahoo.prelude.query.CompositeItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.TermType;
import com.yahoo.prelude.semantics.engine.Match;
import com.yahoo.prelude.semantics.engine.RuleEvaluation;
import com.yahoo.protect.Validator;
import com.yahoo.search.query.QueryTree;

import java.util.List;

/**
 * A new term produced by a production rule
 *
 * @author bratseth
 */
public abstract class TermProduction extends Production {

    /** The label of this term, or null if none */
    private String label;

    /** The type of term to produce */
    private TermType termType;

    /** Creates a produced template term with no label and the default type */
    public TermProduction() {
        this(null, TermType.DEFAULT);
    }

    /** Creates a produced template term with the default term type */
    public TermProduction(String label) {
        this(label, TermType.DEFAULT);
    }

    /** Creates a produced template term with no label */
    public TermProduction(TermType termType) {
        this(null, termType);
    }

    public TermProduction(String label, TermType termType) {
        this.label = label;
        setTermType(termType);
    }

    /** Sets the label of this. Set to null to use the default */
    public String getLabel() { return label; }

    /** Returns the label of this, or null if none (the default) */
    public void setLabel(String label) { this.label = label; }

    /** Returns the type of term to produce, never null. Default is DEFAULT */
    public TermType getTermType() { return termType; }

    /** Sets the term type to produce */
    public void setTermType(TermType termType) {
        Validator.ensureNotNull("Type of produced Term", termType);
        this.termType = termType;
    }

    /**
     * Inserts newItems at the position of this match
     * TODO: Move to ruleevaluation
     */
    protected void insertMatch(RuleEvaluation e, Match matched, List<Item> newItems, int offset) {
        if (getWeight() != 100)
            newItems.forEach(item -> item.setWeight(getWeight()));
        int insertPosition = matched.getPosition() + offset;

        // This check is necessary (?) because earlier items may have been removed
        // after we recorded the match position. It is sort of hackish. A cleaner
        // solution would be to update the match position on changes
        if (insertPosition > matched.getParent().getItemCount()) {
            insertPosition = matched.getParent().getItemCount();
        }

        e.insertItems(newItems, matched.getParent(), insertPosition, getTermType(), replacing);
        if (e.getTraceLevel() >= 6)
            e.trace(6, "Inserted items '" + newItems + "' at position " + insertPosition + " producing " +
                       e.getEvaluation().getQuery().getModel().getQueryTree());
    }

    protected String getLabelString() {
        if (label == null) return "";
        return label + ":";
    }

    /** All instances of this produces a parseable string output */
    public final String toInnerString() {
        if (termType == null)
            return toInnerTermString();
        else
            return termType.toSign() + toInnerTermString();
    }

    protected abstract String toInnerTermString();

    /**
     * Returns true if we should insert at the match position rather than adding to root.
     * This is the case when the match's parent is a nested composite (not directly under QueryTree).
     */
    protected boolean shouldInsertAtMatch(Match match) {
        if (getTermType() != TermType.DEFAULT) return false;
        CompositeItem parent = match.getParent();
        if (parent == null) return false;
        CompositeItem grandparent = parent.getParent();
        return grandparent != null && !(grandparent instanceof QueryTree);
    }

}
