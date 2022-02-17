// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.engine;

import java.util.Iterator;
import java.util.List;

import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.PhraseItem;

/**
 * The matches referenced by a particular context name in a rule evaluation
 *
 * @author bratseth
 */
public class ReferencedMatches {

    private final String contextName;

    private final List<Match> matches = new java.util.ArrayList<>(1);

    public ReferencedMatches(String contextName) {
        this.contextName = contextName;
    }

    public void addMatch(Match match) {
        matches.add(match);
    }

    public String getContextName() { return contextName; }

    public Iterator<Match> matchIterator() {
        return matches.iterator();
    }

    /**
     * Returns the item to insert from these referenced matches, or null if none
     *
     * @param label the label of the matches
     */
    public Item toItem(String label) {
        if (matches.size() == 0) return null;
        if (matches.size() == 1) return matches.get(0).toItem(label);

        PhraseItem phrase = new PhraseItem(); // TODO: Somehow allow AND items instead here
        phrase.setIndexName(label);
        for (Iterator<Match> i = matches.iterator(); i.hasNext(); ) {
            phrase.addItem(i.next().toItem(label));
        }
        return phrase;
    }

}
