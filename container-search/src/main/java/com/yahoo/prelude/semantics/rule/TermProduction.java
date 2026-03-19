// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.rule;

import com.yahoo.prelude.query.CompositeItem;
import com.yahoo.prelude.query.EquivItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.PhraseItem;
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

    /**
     * Collapses multiple matched terms from a multi-word condition into a single PhraseItem,
     * preserving the adjacency constraint of the original query terms. For example, when
     * "running shoes" matches a two-word condition, this replaces the individual terms with
     * a single PhraseItem so they can be wrapped in an EQUIV as {@code EQUIV "running shoes" sneakers}.
     *
     * @return the PhraseItem that replaced the matched terms, or null if there was only one match
     */
    protected PhraseItem collapseMultiWordMatches(RuleEvaluation e) {
        int count = e.getNonreferencedMatchCount();
        if (count <= 1) return null;

        Match first = e.getNonreferencedMatch(0);
        CompositeItem parent = first.getParent();
        if (parent == null) return null;

        for (int i = 1; i < count; i++) {
            if (e.getNonreferencedMatch(i).getParent() != parent) return null;
        }

        PhraseItem phrase = new PhraseItem();
        phrase.setIndexName(getLabel());
        for (int i = 0; i < count; i++) {
            phrase.addItem(e.getNonreferencedMatch(i).toItem(getLabel()));
        }

        // Remove extra matched terms (1..N-1) from parent
        for (int i = 1; i < count; i++) {
            parent.removeItem(e.getNonreferencedMatch(i).getItem());
        }

        // Replace first matched term with the phrase
        parent.setItem(first.getPosition(), phrase);

        return phrase;
    }

    /** Adds newItem into an EQUIV at the match position, creating one if needed. */
    protected void addEquivItem(RuleEvaluation e, Item newItem) {
        Match matched = e.getNonreferencedMatch(0);
        CompositeItem matchParent = matched.getParent();
        int matchIndex = matched.getPosition();
        EquivItem ancestor = findAncestorEquiv(matchParent);
        if (ancestor != null) {
            ancestor.addItem(newItem);
        } else if (matchIndex < matchParent.getItemCount()
                && matchParent.getItem(matchIndex) instanceof EquivItem existingEquiv) {
            existingEquiv.addItem(newItem);
        } else if (matchIndex < matchParent.getItemCount()) {
            collapseMultiWordMatches(e);
            EquivItem equiv = new EquivItem(matchParent.getItem(matchIndex));
            equiv.addItem(newItem);
            matchParent.setItem(matchIndex, equiv);
        } else {
            e.addItems(List.of(newItem), getTermType());
        }
    }

    /**
     * Walks up the query tree from item looking for an EquivItem ancestor, up to 4 levels.
     * Cascading rules may match a term nested inside a composite (e.g. WordItem inside
     * PhraseItem inside EquivItem), and we need the enclosing EQUIV to add to it.
     */
    private static EquivItem findAncestorEquiv(Item item) {
        for (int i = 0; i < 4 && item != null; i++, item = item.getParent()) {
            if (item instanceof EquivItem equiv) return equiv;
        }
        return null;
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
