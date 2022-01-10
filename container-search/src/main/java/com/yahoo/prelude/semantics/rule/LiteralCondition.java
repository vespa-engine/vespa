// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.rule;

import com.yahoo.prelude.semantics.engine.RuleEvaluation;

/**
 * A condition which is always true, and which has its own value as return value
 *
 * @author bratseth
 */
public class LiteralCondition extends Condition {

    private String value;

    public LiteralCondition(String value) {
        this.value=value;
    }

    protected boolean doesMatch(RuleEvaluation e) {
        e.setValue(value);
        return true;
    }

    public void setValue(String value) { this.value=value; }

    public String getValue() { return value; }

    public String toInnerString() { return "'" + value + "'"; }

}
