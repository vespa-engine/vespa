// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.rule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import com.yahoo.prelude.query.PhraseItem;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.prelude.semantics.engine.Match;
import com.yahoo.prelude.semantics.engine.RuleEvaluation;
import com.yahoo.protect.Validator;

/**
 * A literal phrase produced by a production rule
 *
 * @author bratseth
 */
public class LiteralPhraseProduction extends TermProduction {

    private final List<String> terms = new ArrayList<>();

    /** Creates a new produced literal phrase */
    public LiteralPhraseProduction() {
        super();
    }

    /**
     * Creates a new produced literal phrase
     *
     * @param label the label of the produced term
     */
    public LiteralPhraseProduction(String label) {
        super(label);
    }

    /** Adds a term to this phrase */
    public void addTerm(String term) {
        Validator.ensureNotNull("A term in a produced phrase",term);
        terms.add(term);
    }

    /** Returns a read only view of the terms produced by this, never null */
    public List<String> getTerms() { return Collections.unmodifiableList(terms); }

    public void produce(RuleEvaluation e, int offset) {
        PhraseItem newPhrase = new PhraseItem();
        newPhrase.setIndexName(getLabel());
        for (String term : terms)
            newPhrase.addItem(new WordItem(term));

        if (replacing) {
            Match matched = e.getNonreferencedMatch(0);
            insertMatch(e, matched, List.of(newPhrase), offset);
        }
        else {
            newPhrase.setWeight(getWeight());
            if (e.getTraceLevel() >= 6)
                e.trace(6, "Adding '" + newPhrase + "'");
            e.addItems(List.of(newPhrase), getTermType());
        }
    }

    public String toInnerTermString() {
        return getLabelString() + "\"" + getSpaceSeparated(terms) + "\"";
    }

    private String getSpaceSeparated(List<String> terms) {
        StringBuilder builder = new StringBuilder();
        for (Iterator<String> i = terms.iterator(); i.hasNext(); ) {
            builder.append(i.next());
            if (i.hasNext())
                builder.append(" ");
        }
        return builder.toString();
    }

}
