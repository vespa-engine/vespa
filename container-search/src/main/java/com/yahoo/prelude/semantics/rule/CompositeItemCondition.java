// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.rule;

import com.yahoo.prelude.query.PhraseItem;
import com.yahoo.prelude.semantics.engine.Choicepoint;
import com.yahoo.prelude.semantics.engine.RuleEvaluation;

/**
 * A condition on the presense of a particular kind of composite item (possibly also with a particular content)
 *
 * @author bratseth
 * @since 5.1.15
 */
public class CompositeItemCondition extends CompositeCondition {

    @Override
    protected boolean doesMatch(RuleEvaluation e) {
        Choicepoint choicepoint = e.getChoicepoint(this,true);
        choicepoint.updateState();
        boolean matches = e.currentItem().getItem().getParent() instanceof PhraseItem
                          && allSubConditionsMatches(e);
        if ( ! matches)
            choicepoint.backtrack();
        return matches;

    }

    @Override
    protected String toInnerString() {
        if (getLabel()!=null)
            return getLabel() + ":(" + toInnerStringBody() + ")";
        else if (useParentheses())
            return "(" + toInnerStringBody() + ")";
        else
            return toInnerStringBody();
    }

    private String toInnerStringBody() {
        return "\"" + conditionsToString(" ") + "\"";
    }

}
