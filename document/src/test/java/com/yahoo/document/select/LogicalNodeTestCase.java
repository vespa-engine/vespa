// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.select;

import com.yahoo.document.BucketIdFactory;
import com.yahoo.document.select.rule.ExpressionNode;
import com.yahoo.document.select.rule.LiteralNode;
import com.yahoo.document.select.rule.LogicNode;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class LogicalNodeTestCase {
    private static class TracedNode implements ExpressionNode {
        private final AtomicInteger evalOrder;
        private final ExpressionNode node;
        private int evaluatedAs = -1;

        TracedNode(AtomicInteger evalOrder, ExpressionNode node) {
            this.evalOrder = evalOrder;
            this.node = node;
        }
        @Override
        public Object evaluate(Context doc) {
            evaluatedAs = evalOrder.getAndIncrement();
            return node.evaluate(doc);
        }

        @Override
        public BucketSet getBucketSet(BucketIdFactory factory) {
            return node.getBucketSet(factory);
        }

        @Override
        public void accept(Visitor visitor) {
            node.accept(visitor);
        }
        boolean isEvaluated() { return evaluatedAs >= 0; }
        int getEvalOrder() { return evaluatedAs; }
    }
    private static Result evaluate(ExpressionNode node) {
        return ((ResultList)node.evaluate(new Context(null))).toResult();
    }

    private static TracedNode createTraced(AtomicInteger evalOrder, char node) {
        return new TracedNode(evalOrder, new LiteralNode(node == 'T'));
    }

    private static void addOperator(LogicNode logical, char operator, ExpressionNode node) {
        if (operator == '&') {
            logical.add("and", node);
        } else if (operator == '|') {
            logical.add("or", node);
        } else {
            throw new IllegalArgumentException("Bad operator '" + operator + "'");
        }
    }

    static private void verifyEvaluationOrder(String expr, boolean expectedResult, List<Integer> expectedEvaluationOrder ) {
        assertEquals(1, expr.length()%2);
        assertEquals(expectedEvaluationOrder.size()*2 - 1, expr.length());
        TracedNode [] traced = new TracedNode[expectedEvaluationOrder.size()];
        AtomicInteger evalOrder = new AtomicInteger(0);
        for (int i=0; i < traced.length; i++) {
            traced[i] = createTraced(evalOrder, expr.charAt(i*2));
        }
        LogicNode logical = new LogicNode().add(null, traced[0]);
        for (int i=1; i < traced.length; i++) {
            addOperator(logical, expr.charAt(i*2-1), traced[i]);
        }
        for (TracedNode node : traced) {
            assertFalse(node.isEvaluated());
        }
        assertEquals(Result.toResult(expectedResult), evaluate(logical));
        for (int i = 0; i < traced.length; i++) {
            assertEquals(expectedEvaluationOrder.get(i).intValue(), traced[i].getEvalOrder());
        }
    }
    @Test
    public void testFullyExhaustedAND() {
        verifyEvaluationOrder("T&T", true, List.of(0,1));

    }
    @Test
    public void testShortCircuitAND() {
        verifyEvaluationOrder("F&T", false, List.of(0,-1));
    }

    @Test
    public void testFullyExhaustedOR() {
        verifyEvaluationOrder("F|T", true, List.of(0,1));
    }

    @Test
    public void testShortCircuitOR() {
        verifyEvaluationOrder("T|F", true, List.of(0,-1));
    }

    @Test
    public void testLeft2Right() {
        verifyEvaluationOrder("T&T&T&T&T", true, List.of(0,1,2,3,4));
        verifyEvaluationOrder("T&T&F&T&F", false, List.of(0,1,2,-1,-1));

        verifyEvaluationOrder("F|F|F|F|T", true, List.of(0,1,2,3,4));
        verifyEvaluationOrder("F|F|F|F|F", false, List.of(0,1,2,3,4));
        verifyEvaluationOrder("F|F|T|F|T", true, List.of(0,1,2,-1,-1));
    }

    @Test
    public void testLeft2RightWithPriority() {
        verifyEvaluationOrder("T&F|T", true, List.of(0,1,2));
        verifyEvaluationOrder("F&T|T", true, List.of(0,-1,1));

        verifyEvaluationOrder("T|F&T", true, List.of(0,-1,-1));
        verifyEvaluationOrder("F|F&T", false, List.of(0,1,-1));
        verifyEvaluationOrder("F|T&T", true, List.of(0,1,2));
    }
}
