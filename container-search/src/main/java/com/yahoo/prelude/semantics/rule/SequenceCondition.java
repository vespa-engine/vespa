// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.rule;

import java.util.Iterator;

import com.yahoo.prelude.semantics.engine.Choicepoint;
import com.yahoo.prelude.semantics.engine.RuleEvaluation;

/**
 * A set of conditions which much match the query in sequence
 *
 * @author bratseth
 */
public class SequenceCondition extends CompositeCondition {

    public SequenceCondition() {
    }

    public boolean doesMatch(RuleEvaluation e) {
        Choicepoint choicepoint = e.getChoicepoint(this, true);
        choicepoint.updateState();
        boolean matches = allSubConditionsMatches(e);
        if (!matches)
            choicepoint.backtrack();
        return matches;
    }

    protected boolean useParentheses() {
        return (getParent() != null
                && ! (getParent() instanceof ChoiceCondition));
    }

    public String toInnerString() {
        return toInnerString(" ");
    }

}
