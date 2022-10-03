// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.expressiontransforms;

import com.yahoo.searchlib.rankingexpression.evaluation.BooleanValue;
import com.yahoo.searchlib.rankingexpression.rule.OperationNode;
import com.yahoo.searchlib.rankingexpression.rule.Operator;
import com.yahoo.searchlib.rankingexpression.rule.CompositeNode;
import com.yahoo.searchlib.rankingexpression.rule.ConstantNode;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.IfNode;
import com.yahoo.searchlib.rankingexpression.transform.ExpressionTransformer;
import com.yahoo.searchlib.rankingexpression.transform.TransformContext;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;

/**
 * Transforms
 *        a &amp;&amp; b into if(a, b, false)
 * and
 *        a || b into if(a, true, b)
 * to avoid computing b if a is false and true respectively.
 *
 * This may increase performance since boolean expressions are not short-circuited.
 *
 * @author bratseth
 */
public class BooleanExpressionTransformer extends ExpressionTransformer<TransformContext> {

    @Override
    public ExpressionNode transform(ExpressionNode node, TransformContext context) {
        if (node instanceof CompositeNode composite)
            node = transformChildren(composite, context);

        if (node instanceof OperationNode arithmetic)
            node = transformBooleanArithmetics(arithmetic, context);

        return node;
    }

    private ExpressionNode transformBooleanArithmetics(OperationNode node, TransformContext context) {
        Iterator<ExpressionNode> child = node.children().iterator();

        // Transform in precedence order:
        Deque<ChildNode> stack = new ArrayDeque<>();
        stack.push(new ChildNode(null, child.next()));
        for (Iterator<Operator> it = node.operators().iterator(); it.hasNext() && child.hasNext();) {
            Operator op = it.next();
            if ( ! stack.isEmpty()) {
                while (stack.size() > 1 && ! op.hasPrecedenceOver(stack.peek().op)) {
                    popStack(stack, context);
                }
            }
            stack.push(new ChildNode(op, child.next()));
        }
        while (stack.size() > 1)
            popStack(stack, context);
        return stack.getFirst().child;
    }

    private void popStack(Deque<ChildNode> stack, TransformContext context) {
        ChildNode rhs = stack.pop();
        ChildNode lhs = stack.peek();

        // isDefinitelyPrimitive is expensive so only invoke it when necessary
        ExpressionNode combination;
        if (rhs.op == Operator.and && isDefinitelyPrimitive(lhs.child, context) && isDefinitelyPrimitive(rhs.child, context))
            combination = andByIfNode(lhs.child, rhs.child);
        else if (rhs.op == Operator.or && isDefinitelyPrimitive(lhs.child, context) && isDefinitelyPrimitive(rhs.child, context))
            combination = orByIfNode(lhs.child, rhs.child);
        else {
            combination = resolve(lhs, rhs);
            lhs.artificial = true;
        }
        lhs.child = combination;
    }

    private boolean isDefinitelyPrimitive(ExpressionNode node, TransformContext context) {
        try {
            return node.type(context.types()).rank() == 0;
        }
        catch (IllegalArgumentException e) {
            // Types can only be reliably resolved top down, which has not done here.
            // E.g
            // function(nameArg) {
            //    attribute(nameArg)
            // }
            // is supported.
            // So, we return false when something cannot be resolved.
            return false;
        }
    }

    private static OperationNode resolve(ChildNode left, ChildNode right) {
        if (! (left.child instanceof OperationNode) && ! (right.child instanceof OperationNode))
            return new OperationNode(left.child, right.op, right.child);

        // Collapse inserted ArithmeticNodes
        List<Operator> joinedOps = new ArrayList<>();
        joinOps(left, joinedOps);
        joinedOps.add(right.op);
        joinOps(right, joinedOps);
        List<ExpressionNode> joinedChildren = new ArrayList<>();
        joinChildren(left, joinedChildren);
        joinChildren(right, joinedChildren);
        return new OperationNode(joinedChildren, joinedOps);
    }

    private static void joinOps(ChildNode node, List<Operator> joinedOps) {
        if (node.artificial && node.child instanceof OperationNode operationNode)
            joinedOps.addAll(operationNode.operators());
    }
    private static void joinChildren(ChildNode node, List<ExpressionNode> joinedChildren) {
        if (node.artificial && node.child instanceof OperationNode operationNode)
            joinedChildren.addAll(operationNode.children());
        else
            joinedChildren.add(node.child);
    }

    private IfNode andByIfNode(ExpressionNode a, ExpressionNode b) {
        return new IfNode(a, b, new ConstantNode(new BooleanValue(false)));
    }

    private IfNode orByIfNode(ExpressionNode a, ExpressionNode b) {
        return new IfNode(a, new ConstantNode(new BooleanValue(true)), b);
    }

    /** A child with the operator to be applied to it when combining it with the previous child. */
    private static class ChildNode {

        final Operator op;
        ExpressionNode child;
        boolean artificial;

        public ChildNode(Operator op, ExpressionNode child) {
            this.op = op;
            this.child = child;
        }

        @Override
        public String toString() {
            return child.toString();
        }

    }

}
