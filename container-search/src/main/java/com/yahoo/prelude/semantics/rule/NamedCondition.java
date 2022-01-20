// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.rule;

import com.yahoo.prelude.semantics.engine.RuleEvaluation;

/**
 * A condition given a name which enables it to be referenced from other conditions.
 *
 * @author bratseth
 */
public class NamedCondition {

    private String conditionName;

    private Condition condition;

    public NamedCondition(String name, Condition condition) {
        this.conditionName = name;
        this.condition = condition;
    }

    public String getName() { return conditionName; }

    public void setName(String name) { this.conditionName = name; }

    public Condition getCondition() { return condition; }

    public void setCondition(Condition condition) { this.condition = condition; }

    public boolean matches(RuleEvaluation e) {
        if (e.getTraceLevel() >= 3) {
            e.trace(3,"Evaluating '" + this + "' at " + e.currentItem());
            e.indentTrace();
        }

        boolean matches = condition.matches(e);

        if (e.getTraceLevel() >= 3) {
            e.unindentTrace();
            if (matches)
                e.trace(3,"Matched '" + this + "' at " + e.previousItem());
            else if (e.getTraceLevel() >= 4)
                e.trace(4,"Did not match '" + this + "' at " + e.currentItem());
        }
        return matches;
    }

    /**
     * Returns the canonical string representation of this named condition.
     * This string representation can always be reparsed to produce an
     * identical rule to this one.
     */
    @Override
    public String toString() {
        return "[" + conditionName + "] :- " + condition.toString();
    }

}
