// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

import com.yahoo.document.select.Visitor;
import com.yahoo.document.select.rule.*;

public abstract class DocumentTypeVisitor implements Visitor {
    @Override
    public void visit(ArithmeticNode arithmeticNode) {
        for (ArithmeticNode.NodeItem item : arithmeticNode.getItems()) {
            item.getNode().accept(this);
        }
    }

    @Override
    public void visit(AttributeNode attributeNode) {
        attributeNode.getValue().accept(this);
    }

    @Override
    public void visit(ComparisonNode comparisonNode) {
        comparisonNode.getLHS().accept(this);
        comparisonNode.getRHS().accept(this);
    }

    @Override
    public void visit(EmbracedNode embracedNode) {
        embracedNode.getNode().accept(this);
    }

    @Override
    public void visit(IdNode idNode) {
    }

    @Override
    public void visit(LiteralNode literalNode) {
    }

    @Override
    public void visit(LogicNode logicNode) {
        for (LogicNode.NodeItem item : logicNode.getItems()) {
            item.getNode().accept(this);
        }
    }

    @Override
    public void visit(NegationNode negationNode) {
        negationNode.getNode().accept(this);
    }

    @Override
    public void visit(NowNode nowNode) {
    }

    @Override
    public void visit(SearchColumnNode searchColumnNode) {
    }

    @Override
    public void visit(VariableNode variableNode) {
    }
}
