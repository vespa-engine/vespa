package com.yahoo.document.select;

import com.yahoo.document.BucketIdFactory;
import com.yahoo.document.select.rule.ExpressionNode;
import com.yahoo.document.select.rule.LiteralNode;
import com.yahoo.document.select.rule.LogicNode;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LogicalNodeTestCase {
    private static class TracedNode implements ExpressionNode {

        private final ExpressionNode node;
        private boolean evaluated = false;

        TracedNode(ExpressionNode node) {
            this.node = node;
        }
        @Override
        public Object evaluate(Context doc) {
            evaluated = true;
            return node.evaluate(doc);
        }

        @Override
        public BucketSet getBucketSet(BucketIdFactory factory) {
            return node.getBucketSet(factory);
        }

        @Override
        public OrderingSpecification getOrdering(int order) {
            return node.getOrdering(order);
        }

        @Override
        public void accept(Visitor visitor) {
            node.accept(visitor);
        }
        boolean isEvaluated() { return evaluated; }
    }
    private static Result evaluate(ExpressionNode node) {
        return ((ResultList)node.evaluate(new Context(null))).toResult();
    }
    @Test
    public void testFullyExhaustedAND() {
        TracedNode second = new TracedNode(new LiteralNode(true));
        assertFalse(second.isEvaluated());
        ExpressionNode logical = new LogicNode()
                .add(null, new LiteralNode(true))
                .add("and", second);
        assertEquals(Result.TRUE, evaluate(logical));
        assertTrue(second.isEvaluated());
    }
    @Test
    public void testShortCircuitAND() {
        TracedNode second = new TracedNode(new LiteralNode(true));
        assertFalse(second.isEvaluated());
        ExpressionNode logical = new LogicNode()
                .add(null, new LiteralNode(false))
                .add("and", second);
        assertEquals(Result.FALSE, evaluate(logical));
        assertFalse(second.isEvaluated());
    }

    @Test
    public void testFullyExhaustedOR() {
        TracedNode second = new TracedNode(new LiteralNode(true));
        assertFalse(second.isEvaluated());
        ExpressionNode logical = new LogicNode()
                .add(null, new LiteralNode(false))
                .add("or", second);
        assertEquals(Result.TRUE, evaluate(logical));
        assertTrue(second.isEvaluated());
    }

    @Test
    public void testShortCircuitOR() {
        TracedNode second = new TracedNode(new LiteralNode(false));
        assertFalse(second.isEvaluated());
        ExpressionNode logical = new LogicNode()
                .add(null, new LiteralNode(true))
                .add("or", second);
        assertEquals(Result.TRUE, evaluate(logical));
        assertFalse(second.isEvaluated());
    }
}
