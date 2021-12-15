// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.select.convert;

import com.yahoo.document.select.rule.ArithmeticNode;
import com.yahoo.document.select.rule.AttributeNode;
import com.yahoo.document.select.rule.ComparisonNode;

/**
 * Represents a query containing a valid now() expression. The now expression
 * is very strict right now, but can be expanded later.
 *
 * @author Ulf Lilleengen
 */
public class NowQueryExpression {

    private final AttributeNode attribute;
    private final ComparisonNode comparison;
    private final NowQueryNode now;

    NowQueryExpression(AttributeNode attribute, ComparisonNode comparison, ArithmeticNode arithmetic) {
        this.attribute = attribute;
        this.comparison = comparison;
        this.now = (arithmetic != null ? new NowQueryNode(arithmetic) : new NowQueryNode(0));
    }

    public String getDocumentType() {
        return attribute.getValue().toString();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for(AttributeNode.Item item : attribute.getItems()) {
            sb.append(item.getName()).append(".");
        }
        sb.deleteCharAt(sb.length() - 1);
        return sb + ":" + comparison.getOperator() + now;
    }

}
