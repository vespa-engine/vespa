// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.rule;

import com.yahoo.prelude.semantics.RuleBase;
import com.yahoo.prelude.semantics.engine.RuleEvaluation;

/**
 * A condition which matches if its contained condition doesn't.
 * NotCondition inverts the term checking but not the label checking.
 * That is, it means "label:!term", it does not mean "!label:term".
 *
 * @author bratseth
 */
public class NotCondition extends Condition {

    private Condition condition;

    public NotCondition(Condition condition) {
        this.condition=condition;
    }

    public Condition getCondtiion() { return condition; }

    public void setCondition(Condition condition) { this.condition=condition; }

    protected boolean doesMatch(RuleEvaluation e) {
        e.setInNegation(!e.isInNegation());
        boolean matches=!condition.matches(e);
        e.setInNegation(!e.isInNegation());
        return matches;
    }

    public String toInnerString() {
        return "!" + condition;
    }

    public void makeReferences(RuleBase ruleBase) {
        condition.makeReferences(ruleBase);
    }

    protected boolean hasOpenChoicepoint(RuleEvaluation evaluation) {
        return condition.hasOpenChoicepoint(evaluation);
    }



}
