// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.rule;

import java.util.Iterator;

import com.yahoo.prelude.semantics.engine.Choicepoint;
import com.yahoo.prelude.semantics.engine.RuleEvaluation;

/**
 * A condition which consists of a list of alternatives to match at any location
 *
 * @author bratseth
 */
public class AndCondition extends CompositeCondition {

    // TODO: Not in use. What was this for? Remove?

    public AndCondition() {
    }

    public boolean doesMatch(RuleEvaluation e) {
        Choicepoint choicepoint=e.getChoicepoint(this,true);
        choicepoint.updateState();
        boolean matches=allSubConditionsMatches(e);
        if (!matches)
            choicepoint.backtrack();
        return matches;
    }

    protected boolean useParentheses() {
        return (getParent()!=null
                && ! (getParent() instanceof ChoiceCondition));
    }

    protected String toInnerString() {
         return toInnerString(" & ");
     }

}
