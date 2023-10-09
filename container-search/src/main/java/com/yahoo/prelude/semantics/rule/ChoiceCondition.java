// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.rule;

import java.util.Iterator;

import com.yahoo.prelude.semantics.engine.RuleEvaluation;

/**
 * A condition which consists of a list of alternatives to match at a specific location
 *
 * @author bratseth
 */
public class ChoiceCondition extends CompositeCondition {

    public ChoiceCondition() {
    }

    public boolean doesMatch(RuleEvaluation e) {
        //if (e.currentItem()==null) return false;
        for (Iterator<Condition> i=conditionIterator(); i.hasNext(); ) {
            Condition subCondition= i.next();
            if (subCondition.matches(e))
                return true;
        }

        return false;
    }

    protected String toInnerString() {
         return toInnerString(", ");
     }

}
