// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.select.simple;

import com.yahoo.document.select.rule.ComparisonNode;
import com.yahoo.document.select.rule.ExpressionNode;

/**
 * @author baldersheim
 */
public class SelectionParser extends Parser {

    private ExpressionNode node;

    public ExpressionNode getNode() { return node; }

    public boolean parse(CharSequence s) {
        boolean retval = false;
        IdSpecParser id = new IdSpecParser();
        if (id.parse(s)) {
            OperatorParser op = new OperatorParser();
            if (op.parse(id.getRemaining())) {
                if (id.isUserSpec()) {
                    IntegerParser v = new IntegerParser();
                    if (v.parse(op.getRemaining())) {
                        node = new ComparisonNode(id.getId(), op.getOperator(), v.getValue());
                        retval = true;
                    }
                    setRemaining(v.getRemaining());
                } else {
                    StringParser v = new StringParser();
                    if (v.parse(op.getRemaining())) {
                        node = new ComparisonNode(id.getId(), op.getOperator(), v.getValue());
                        retval = true;
                    }
                    setRemaining(v.getRemaining());
                }
            } else {
                setRemaining(op.getRemaining());
            }
        } else {
            setRemaining(id.getRemaining());
        }

        return retval;
    }

}
