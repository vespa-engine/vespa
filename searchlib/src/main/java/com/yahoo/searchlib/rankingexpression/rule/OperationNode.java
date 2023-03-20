// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.rule;

import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.searchlib.rankingexpression.evaluation.Context;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.TypeContext;
import com.yahoo.tensor.functions.Join;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * A sequence of binary operations.
 *
 * @author bratseth
 */
public final class OperationNode extends CompositeNode {

    private static final Logger logger = Logger.getLogger(OperationNode.class.getName());

    private final List<ExpressionNode> children;
    private final List<Operator> operators;

    public OperationNode(List<ExpressionNode> children, List<Operator> operators) {
        this.children = List.copyOf(children);
        this.operators = List.copyOf(operators);
        if (operators.isEmpty()) {
            logger.warning("Strange: no operators for OperationNode");
        }
        int needChildren = operators.size() + 1;
        if (needChildren != children.size()) {
            throw new IllegalArgumentException("Need " + needChildren + " children, but got " + children.size());
        }
    }

    public OperationNode(ExpressionNode leftExpression, Operator operator, ExpressionNode rightExpression) {
        this.children = List.of(leftExpression, rightExpression);
        this.operators = List.of(operator);
    }

    public List<Operator> operators() { return operators; }

    @Override
    public List<ExpressionNode> children() { return children; }

    @Override
    public StringBuilder toString(StringBuilder string, SerializationContext context, Deque<String> path, CompositeNode parent) {
        boolean nonDefaultPrecedence = nonDefaultPrecedence(parent);
        if (nonDefaultPrecedence)
            string.append("(");

        Iterator<ExpressionNode> child = children.iterator();
        child.next().toString(string, context, path, this);
        if (child.hasNext())
            string.append(" ");
        for (Iterator<Operator> op = operators.iterator(); op.hasNext() && child.hasNext();) {
            string.append(op.next().toString()).append(" ");
            child.next().toString(string, context, path, this);
            if (op.hasNext())
                string.append(" ");
        }
        if (nonDefaultPrecedence)
            string.append(")");

        return string;
    }

    /**
     * Returns true if this node has lower precedence than the parent
     * (even though by virtue of being a node it will be calculated before the parent).
     */
    private boolean nonDefaultPrecedence(CompositeNode parent) {
        if ( parent == null) return false;
        if ( ! (parent instanceof OperationNode operationParent)) return false;

        // The last line below can only be correct if both only have one operator.
        // Getting this correct is impossible without more work.
        // So for now we only handle the simple case correctly, and use a safe approach by adding
        // extra parenthesis just in case....
        if ((operationParent.operators.size() != 1) || (operators.size() != 1)) {
            return true;
        }
        return operationParent.operators.get(0).hasPrecedenceOver(this.operators.get(0));
    }

    @Override
    public TensorType type(TypeContext<Reference> context) {
        // Compute type using tensor types as operation operators are supported on tensors
        // and is correct also in the special case of doubles.
        // As all our functions are type-commutative, we don't need to take operator precedence into account
        TensorType type = children.get(0).type(context);
        for (int i = 1; i < children.size(); i++)
            type = Join.outputType(type, children.get(i).type(context));
        return type;
    }

    @Override
    public Value evaluate(Context context) {
        Iterator<ExpressionNode> child = children.iterator();

        // Apply in precedence order:
        Deque<ValueItem> stack = new ArrayDeque<>();
        stack.push(new ValueItem(null, child.next().evaluate(context)));
        for (Iterator<Operator> it = operators.iterator(); it.hasNext() && child.hasNext();) {
            Operator op = it.next();
            if ( ! stack.isEmpty()) {
                while (stack.size() > 1 && ! op.hasPrecedenceOver(stack.peek().op)) {
                    popStack(stack);
                }
            }
            stack.push(new ValueItem(op, child.next().evaluate(context)));
        }
        while (stack.size() > 1) {
            popStack(stack);
        }
        return stack.getFirst().value;
    }

    private void popStack(Deque<ValueItem> stack) {
        ValueItem rhs = stack.pop();
        ValueItem lhs = stack.peek();
        lhs.value = rhs.op.evaluate(lhs.value, rhs.value);
    }

    @Override
    public CompositeNode setChildren(List<ExpressionNode> newChildren) {
        if (children.size() != newChildren.size())
            throw new IllegalArgumentException("Expected " + children.size() + " children but got " + newChildren.size());
        return new OperationNode(newChildren, operators);
    }

    @Override
    public int hashCode() { return Objects.hash(children, operators); }

    public static OperationNode resolve(ExpressionNode left, Operator op, ExpressionNode right) {
        if ( ! (left instanceof OperationNode leftArithmetic)) return new OperationNode(left, op, right);

        List<ExpressionNode> newChildren = new ArrayList<>(leftArithmetic.children());
        newChildren.add(right);

        List<Operator> newOperators = new ArrayList<>(leftArithmetic.operators());
        newOperators.add(op);

        return new OperationNode(newChildren, newOperators);
    }

    private static class ValueItem {

        final Operator op;
        Value value;

        public ValueItem(Operator op, Value value) {
            this.op = op;
            this.value = value;
        }

        @Override
        public String toString() {
            return value.toString();
        }

    }

}

