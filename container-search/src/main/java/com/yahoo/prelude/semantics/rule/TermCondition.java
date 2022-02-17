// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.rule;

import com.yahoo.prelude.semantics.engine.NameSpace;
import com.yahoo.prelude.semantics.engine.RuleBaseLinguistics;
import com.yahoo.prelude.semantics.engine.RuleEvaluation;

/**
 * A term in a rule
 *
 * @author bratseth
 */
public class TermCondition extends Condition {

    private final RuleBaseLinguistics linguistics;
    private final String originalTerm;
    private final String term;

    public TermCondition(String term, RuleBaseLinguistics linguistics) {
        this(null, term, linguistics);
    }

    public TermCondition(String label, String term, RuleBaseLinguistics linguistics) {
        super(label);
        this.linguistics = linguistics;
        this.originalTerm = term;
        this.term = linguistics.process(term);
    }

    protected boolean doesMatch(RuleEvaluation e) {
        // TODO: Move this into the respective namespaces when query becomes one */
        if (getNameSpace() != null) {
            NameSpace nameSpace = e.getEvaluation().getNameSpace(getNameSpace());
            return nameSpace.matches(originalTerm, e); // No processing of terms in namespaces
        }
        else {
            if (e.currentItem() == null) return false;
            if ( ! labelMatches(e)) return false;

            boolean matches = labelMatches(e.currentItem().getItem(), e) &&
                              linguistics.process(e.currentItem().getItem().stringValue()).equals(term);
            if ((matches && !e.isInNegation() || (!matches && e.isInNegation()))) {
                e.addMatch(e.currentItem(), originalTerm);
                e.setValue(term);
                e.next();
            }
            return matches;
        }
    }

    public String term() { return term; }

    @Override
    public String toInnerString() {
        return getLabelString() + term;
    }

}
