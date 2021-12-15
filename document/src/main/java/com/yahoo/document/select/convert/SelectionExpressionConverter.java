// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.select.convert;

import com.yahoo.document.select.NowCheckVisitor;
import com.yahoo.document.select.Visitor;
import com.yahoo.document.select.rule.ArithmeticNode;
import com.yahoo.document.select.rule.AttributeNode;
import com.yahoo.document.select.rule.ComparisonNode;
import com.yahoo.document.select.rule.DocumentNode;
import com.yahoo.document.select.rule.DocumentTypeNode;
import com.yahoo.document.select.rule.EmbracedNode;
import com.yahoo.document.select.rule.ExpressionNode;
import com.yahoo.document.select.rule.IdNode;
import com.yahoo.document.select.rule.LiteralNode;
import com.yahoo.document.select.rule.LogicNode;
import com.yahoo.document.select.rule.NegationNode;
import com.yahoo.document.select.rule.NowNode;
import com.yahoo.document.select.rule.VariableNode;

import java.util.HashMap;
import java.util.Map;

/**
 * Class which converts a selection tree into a set of queries per document type.
 * If unsupported operations are or illegal arguments are encountered, an exception is thrown.
 *
 * @author Ulf Lilleengen
 */
public class SelectionExpressionConverter implements Visitor {

    private final Map<String, NowQueryExpression> expressionMap = new HashMap<>();

    private static class BuildState {

        public AttributeNode attribute;
        public ComparisonNode comparison;
        public ArithmeticNode arithmetic;
        public NowNode now;

    }

    private BuildState state;

    private boolean hasNow(ExpressionNode node) {
        NowCheckVisitor visitor = new NowCheckVisitor();
        node.accept(visitor);
        return visitor.requiresConversion();
    }

    public SelectionExpressionConverter() {
        this.state = null;
    }

    public Map<String, String> getQueryMap() {
        Map<String, String> ret = new HashMap<>();
        for (NowQueryExpression expression : expressionMap.values()) {
            ret.put(expression.getDocumentType(), expression.toString());
        }
        return ret;
    }

    public void visit(ArithmeticNode node) {
        if (state == null ) return;
        if (node.getItems().size() > 2) {
            throw new IllegalArgumentException("Too many arithmetic operations");
        }
        for (ArithmeticNode.NodeItem item : node.getItems()) {
            if (item.getOperator() != ArithmeticNode.SUB && item.getOperator() != ArithmeticNode.NOP) {
                throw new IllegalArgumentException("Arithmetic operator '" + node.operatorToString(item.getOperator()) + "' is not supported");
            }
        }
        state.arithmetic = node;

    }

    public void visit(AttributeNode node) {
        if (state == null ) return;
        if (expressionMap.containsKey(node.getValue().toString())) {
            throw new IllegalArgumentException("Specifying multiple document types is not allowed");
        }
        for (AttributeNode.Item item : node.getItems()) {
            if (item.getType() != AttributeNode.Item.ATTRIBUTE) {
                throw new IllegalArgumentException("Only attribute items are supported");
            }
        }
        state.attribute = node;
    }

    public void visit(ComparisonNode node) {
        if (state != null) {
            throw new IllegalArgumentException("Comparison cannot be done within now expression");
        }
        if (!hasNow(node)) {
            return;
        }
        state = new BuildState();
        node.getLHS().accept(this);
        node.getRHS().accept(this);

        if (!">".equals(node.getOperator())) {
            throw new IllegalArgumentException("Comparison operator '" + node.getOperator() + "' is not supported");
        }
        if (!(node.getLHS() instanceof AttributeNode)) {
            throw new IllegalArgumentException("Left hand side of comparison must be a document field");
        }
        state.comparison = node;
        if (state.attribute != null &&
            (state.arithmetic != null || state.now != null)) {
            NowQueryExpression expression = new NowQueryExpression(state.attribute, state.comparison, state.arithmetic);
            expressionMap.put(expression.getDocumentType(), expression);
            state = null;
        }
    }

    public void visit(DocumentNode node) {
        // Silently ignore
    }

    public void visit(DocumentTypeNode node) {
        // Silently ignore
    }

    public void visit(EmbracedNode node) {
        if (state == null ) return;
        throw new UnsupportedOperationException("Grouping is not supported yet.");
    }

    public void visit(IdNode node) {
        if (state == null ) return;
        throw new UnsupportedOperationException("Document id not supported yet.");
    }

    public void visit(LiteralNode node) {
        if (state == null ) return;
        if (!(node.getValue() instanceof Long)) {
            throw new IllegalArgumentException("Literal " + node + " is not supported");
        }
    }

    public void visit(LogicNode node) {
        if (state != null) {
            throw new IllegalArgumentException("Logic expressions not supported in now expressions");
        }
        for (LogicNode.NodeItem item : node.getItems()) {
            item.getNode().accept(this);
        }
    }

    public void visit(NegationNode node) {
        if (state == null ) return;
        throw new UnsupportedOperationException("Negation not supported yet.");
    }

    public void visit(NowNode node) {
        if (state == null ) return;
        state.now = node;
    }

    public void visit(VariableNode node) {
        if (state == null ) return;
        throw new UnsupportedOperationException("Variables not supported yet.");
    }
}
