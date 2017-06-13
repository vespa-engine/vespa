// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.select;

import com.yahoo.document.select.Visitor;
import com.yahoo.document.select.rule.*;

/**
 * Traverse and check if there exists any now() function in the expression tree.
 *
 * @author <a href="mailto:lulf@yahoo-inc.com">Ulf Lilleengen</a>
 */

public class NowCheckVisitor implements Visitor {
    private int nowNodeCount = 0;

    public boolean requiresConversion() {
        return (nowNodeCount > 0);
    }

    public void visit(ArithmeticNode node) {
        for (ArithmeticNode.NodeItem item : node.getItems()) {
            item.getNode().accept(this);
        }
    }

    public void visit(AttributeNode node) {
        node.getValue().accept(this);
    }

    public void visit(ComparisonNode node) {
        node.getLHS().accept(this);
        node.getRHS().accept(this);
    }

    public void visit(DocumentNode node) {
    }

    public void visit(EmbracedNode node) {
        node.getNode().accept(this);
    }

    public void visit(IdNode node) {
    }

    public void visit(LiteralNode node) {
    }

    public void visit(LogicNode node) {
        for (LogicNode.NodeItem item : node.getItems()) {
            item.getNode().accept(this);
        }
    }

    public void visit(NegationNode node) {
        node.getNode().accept(this);
    }

    public void visit(NowNode node) {
        nowNodeCount++;
    }

    public void visit(SearchColumnNode node) {
    }

    public void visit(VariableNode node) {
    }
}
