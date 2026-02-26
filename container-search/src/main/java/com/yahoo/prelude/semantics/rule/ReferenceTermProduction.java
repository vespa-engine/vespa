// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.rule;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.yahoo.prelude.query.CompositeItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.PhraseItem;
import com.yahoo.prelude.query.TermType;
import com.yahoo.prelude.semantics.engine.EvaluationException;
import com.yahoo.prelude.semantics.engine.Match;
import com.yahoo.prelude.semantics.engine.ReferencedMatches;
import com.yahoo.prelude.semantics.engine.RuleEvaluation;
import com.yahoo.search.query.QueryTree;

/**
 * A term produced by a production rule which takes its actual term value
 * from one or more terms matched in the condition
 *
 * @author bratseth
 */
public class ReferenceTermProduction extends TermProduction {

    private final String reference;
    private final boolean produceAll;

    /**
     * Creates a new produced reference term
     *
     * @param reference the label of the condition this should take its value from
     */
    public ReferenceTermProduction(String reference, boolean produceAll) {
        super();
        this.reference = Objects.requireNonNull(reference, "Reference cannot be null");
        this.produceAll = produceAll;
    }

    /**
     * Creates a new produced reference term
     *
     * @param reference the label of the condition this should take its value from
     * @param termType the type of the term to produce
     */
    public ReferenceTermProduction(String reference, TermType termType, boolean produceAll) {
        super(termType);
        this.reference = Objects.requireNonNull(reference, "Reference cannot be null");
        this.produceAll = produceAll;
    }

    /**
     * Creates a new produced reference term
     *
     * @param label the label of the produced term
     * @param reference the label of the condition this should take its value from
     */
    public ReferenceTermProduction(String label, String reference, boolean produceAll) {
        super(label);
        this.reference = Objects.requireNonNull(reference, "Reference cannot be null");
        this.produceAll = produceAll;
    }

    /**
     * Creates a new produced reference term
     *
     * @param label the label of the produced term
     * @param reference the label of the condition this should take its value from
     * @param termType the type of term to produce
     */
    public ReferenceTermProduction(String label, String reference, TermType termType, boolean produceAll) {
        super(label, termType);
        this.reference = Objects.requireNonNull(reference, "Reference cannot be null");
        this.produceAll = produceAll;
    }

    /** Returns the label of the condition this should take its value from, never null */
    public String getReference() { return reference; }

    public boolean producesAll() { return produceAll; }

    @Override
    void addMatchReferences(Set<String> matchReferences) {
        matchReferences.add(reference);
    }

    public void produce(RuleEvaluation e, int ignored) {
        ReferencedMatches referencedMatches = e.getReferencedMatches(reference);
        if (referencedMatches == null)
            throw new EvaluationException("Referred match '" + reference + "' not found");
        if (replacing) {
            e.removeMatches(referencedMatches);
        }

        var match = referencedMatches.matchIterator().next();
        if (produceAll) {
            // produce all terms in the condition
            NamedCondition namedCondition = e.getEvaluation().ruleBase().getCondition(referencedMatches.getContextName());
            ChoiceCondition choices = (ChoiceCondition)namedCondition.getCondition();
            List<Item> items = new ArrayList<>();
            for (Iterator<Condition> i = choices.conditionIterator(); i.hasNext();) {
                Condition condition = i.next();
                if (condition instanceof TermCondition) {
                    items.add(match.toItem(getLabel(), ((TermCondition)condition).term()));
                }
                else if (condition instanceof SequenceCondition) {
                    PhraseItem phrase = new PhraseItem(getLabel());
                    for (var term : ((SequenceCondition)condition).conditions())
                        phrase.addItem(match.toItem(getLabel(), ((TermCondition)term).term()));
                    items.add(phrase);
                }
                else {
                    // Until we validate this at construction time
                    throw new EvaluationException("Could not produce all terms in " + namedCondition + " as it is " +
                                                  "not a term or sequence condition");
                }
            }
            produce(e, match, items, 0);
        }
        else {
            // produce just the matching term
            produce(e, match, List.of(referencedMatches.toItem(getLabel())), 0);
        }
    }

    private void produce(RuleEvaluation e, Match match, List<Item> items, int offset) {
        if (replacing) {
            insertMatch(e, match, items, offset);
        }
        else if (shouldInsertAtMatch(match)) {
            // Add to the match's parent when it's a nested composite (handles WeakAnd correctly)
            insertMatch(e, match, items, offset);
        }
        else {
            // Use root-level combining for other cases
            e.addItems(items, getTermType());
        }
    }

    @Override
    public String toInnerTermString() {
        return getLabelString() + "[" + reference + "]";
    }

}
