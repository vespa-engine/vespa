// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.rule;

import com.yahoo.prelude.semantics.engine.RuleEvaluation;

/**
 * A condition which evaluates the <i>last included</i> version of
 * the named condition this is a premise of.
 *
 * @author bratseth
 */
public class SuperCondition extends Condition {

    private Condition condition;

    public void setCondition(Condition condition) {
        this.condition=condition;
    }

    public Condition getCondition() {
        return condition;
    }

    public boolean doesMatch(RuleEvaluation e) {
        return condition.matches(e);
    }

    public String toInnerString() {
        if (condition==null)
            return "@super";
        else
            return condition.toString();
    }


}
