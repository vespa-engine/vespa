// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.rule;

import com.yahoo.prelude.query.TermItem;
import com.yahoo.prelude.semantics.engine.NameSpace;
import com.yahoo.prelude.semantics.engine.RuleEvaluation;

/**
 * A term in a rule
 *
 * @author bratseth
 */
public class TermCondition extends Condition {

    private String term, termPlusS;

    /** Creates an invalid term */
    public TermCondition() { }

    public TermCondition(String term) {
        this(null,term);
    }

    public TermCondition(String label, String term) {
        super(label);
        this.term = term;
        termPlusS = term + "s";
    }

    public String getTerm() { return term; }

    public void setTerm(String term) {
        this.term = term;
        termPlusS = term + "s";
    }

    protected boolean doesMatch(RuleEvaluation e) {
        // TODO: Move this into the respective namespaces when query becomes one */
        if (getNameSpace() != null) {
            NameSpace nameSpace = e.getEvaluation().getNameSpace(getNameSpace());
            return nameSpace.matches(term, e);
        }
        else {
            if (e.currentItem() == null) return false;
            if ( ! labelMatches(e)) return false;

            String matchedValue = termMatches(e.currentItem().getItem(), e.getEvaluation().getStemming());
            boolean matches = matchedValue!=null && labelMatches(e.currentItem().getItem(), e);
            if ((matches && !e.isInNegation() || (!matches && e.isInNegation()))) {
                e.addMatch(e.currentItem(), matchedValue);
                e.setValue(term);
                e.next();
            }
            return matches;
        }
    }

    /** Returns a non-null replacement term if there is a match, null otherwise */
    private String termMatches(TermItem queryTerm, boolean stemming) {
        String queryTermString = queryTerm.stringValue();

        // The terms are the same
        boolean matches = queryTermString.equals(term);
        if (matches) return term;

        if (stemming)
            if (termMatchesWithStemming(queryTermString)) return term;

        return null;
    }

    private boolean termMatchesWithStemming(String queryTermString) {
        if (queryTermString.length() < 3) return false; // Don't stem very short terms

        // The query term minus s is the same
        boolean matches = queryTermString.equals(termPlusS);
        if (matches) return true;

        // The query term plus s is the same
        matches = term.equals(queryTermString + "s");
        if (matches) return true;

        return false;
    }

    public String toInnerString() {
        return getLabelString() + term;
    }

}
