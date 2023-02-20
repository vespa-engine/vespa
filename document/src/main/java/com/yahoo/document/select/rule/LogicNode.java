// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.select.rule;

import com.yahoo.document.BucketIdFactory;
import com.yahoo.document.select.BucketSet;
import com.yahoo.document.select.Context;
import com.yahoo.document.select.ResultList;
import com.yahoo.document.select.Visitor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * This class defines a logical expression of nodes. This implementation uses a stack to evaluate its content as to
 * avoid deep recursions when building the parse tree.
 *
 * @author Simon Thoresen Hult
 */
public class LogicNode implements ExpressionNode {

    // A no-op value is defined for completeness.
    public static final int NOP = 0;

    // The OR operator has lower precedence than AND.
    public static final int OR = 1;

    // The AND operator has the highest precedence.
    public static final int AND = 2;

    // The items contained in this.
    private final List<NodeItem> items = new ArrayList<>();

    /**
     * Construct an empty logic expression.
     */
    public LogicNode() {
        // empty
    }

    public List<NodeItem> getItems() {
        return items;
    }
    
    /**
     * Adds an (operator, node) pair to this expression.
     *
     * @param operator The operator that combines the previous with the node given.
     * @param node The node to add to this.
     * @return This, to allow chaining.
     */
    public LogicNode add(String operator, ExpressionNode node) {
        items.add(new LogicNode.NodeItem(stringToOperator(operator), node));
        return this;
    }

    @Override
    public BucketSet getBucketSet(BucketIdFactory factory) {
        Deque<BucketItem> buf = new ArrayDeque<>();
        for (NodeItem item : items) {
            if (!buf.isEmpty()) {
                while (buf.peek().operator > item.operator) {
                    combineBuckets(buf);
                }
            }
            buf.push(new BucketItem(item.operator, item.node.getBucketSet(factory)));
        }
        while (buf.size() > 1) {
            combineBuckets(buf);
        }
        return buf.pop().buckets;
    }

    /**
     * Combines the top two items of the given stack using the operator of the second.
     *
     * @param buf The stack of bucket items.
     */
    private void combineBuckets(Deque<BucketItem> buf) {
        BucketItem rhs = buf.pop();
        BucketItem lhs = buf.pop();
        switch (rhs.operator) {
            case AND -> {
                if (lhs.buckets == null) {
                    lhs.buckets = rhs.buckets;
                } else if (rhs.buckets == null) {
                    // empty
                } else {
                    lhs.buckets = lhs.buckets.intersection(rhs.buckets);
                }
            }
            case OR -> {
                if (lhs.buckets == null) {
                    // empty
                } else if (rhs.buckets == null) {
                    lhs.buckets = null;
                } else {
                    lhs.buckets = lhs.buckets.union(rhs.buckets);
                }
            }
            default -> throw new IllegalStateException("Arithmetic operator " + rhs.operator + " not supported.");
        }
        buf.push(lhs);
    }

    @Override
    public Object evaluate(Context context) {
        Deque<ValueItem> buf = new ArrayDeque<>();
        for (NodeItem item : items) {
            if ( buf.size() > 1) {
                while ((buf.peek().getOperator() >= item.operator)) {
                    combineValues(buf);
                }
            }
            buf.push(new LazyValueItem(item, context));
        }
        while (buf.size() > 1) {
            combineValues(buf);
        }
        return buf.pop().getResult();
    }

    /**
     * Combines the top two items of the given stack using the operator of the second.
     *
     * @param buf The stack of values.
     */
    private void combineValues(Deque<ValueItem> buf) {
        ValueItem rhs = buf.pop();
        ValueItem lhs = buf.pop();
        buf.push(new LazyCombinedItem(lhs, rhs));
    }

    @Override
    public void accept(Visitor visitor) {
        visitor.visit(this);
    }

    @Override
    public String toString() {
        StringBuilder ret = new StringBuilder();
        for (LogicNode.NodeItem item : items) {
            if (item.operator != NOP) {
                ret.append(" ").append(operatorToString(item.operator)).append(" ");
            }
            ret.append(item.node);
        }
        return ret.toString();
    }

    /**
     * Converts the given operator index to a string representation.
     *
     * @param operator The operator index to convert.
     * @return The string representation.
     */
    private String operatorToString(int operator) {
        return switch (operator) {
            case NOP -> null;
            case OR -> "or";
            case AND -> "and";
            default -> throw new IllegalStateException("Logical operator " + operator + " not supported.");
        };
    }

    /**
     * Converts the given operator string to a corresponding operator index. This is necessary to perform a stack
     * traversal of logic expression.
     *
     * @param operator The operator to convert.
     * @return The corresponding index.
     */
    private int stringToOperator(String operator) {
        if (operator == null) {
            return NOP;
        } else if (operator.equalsIgnoreCase("or")) {
            return OR;
        } else if (operator.equalsIgnoreCase("and")) {
            return AND;
        } else {
            throw new IllegalStateException("Logical operator '" + operator + "' not supported.");
        }
    }

    /**
     * Private class to store results in a stack.
     */
    private static abstract class ValueItem implements ResultList.LazyResultList {
        private final int operator;
        ValueItem(int operator) {
            this.operator = operator;
        }
        int getOperator() { return operator; }
    }

    private static final class LazyValueItem extends ValueItem {
        private final NodeItem item;
        private final Context context;
        private ResultList lazyResult = null;

        LazyValueItem(NodeItem item, Context context) {
            super(item.operator);
            this.item = item;
            this.context = context;
        }
        @Override
        public ResultList getResult() {
            if (lazyResult == null) {
                lazyResult = ResultList.toResultList(item.node.evaluate(context));
            }
            return lazyResult;
        }
    }

    private static final class LazyCombinedItem extends ValueItem {
        private final ValueItem lhs;
        private final ValueItem rhs;
        private ResultList lazyResult = null;

        LazyCombinedItem(ValueItem lhs, ValueItem rhs) {
            super(lhs.getOperator());
            this.lhs = lhs;
            this.rhs = rhs;
        }
        @Override
        public ResultList getResult() {
            if (lazyResult == null) {
                switch (rhs.getOperator()) {
                    case AND -> lazyResult = lhs.getResult().combineAND(rhs);
                    case OR -> lazyResult = lhs.getResult().combineOR(rhs);
                    default ->
                            throw new IllegalStateException("Logical operator " + rhs.getOperator() + " not supported.");
                }
            }
            return lazyResult;
        }
    }

    /**
     * Private class to store bucket sets in a stack.
     */
    private static final class BucketItem {
        final private int operator;
        private BucketSet buckets;

        BucketItem(int operator, BucketSet buckets) {
            this.operator = operator;
            this.buckets = buckets;
        }
    }

    /**
     * Private class to store expression nodes in a stack.
     */
    public static final class NodeItem {
        final private int operator;
        final private ExpressionNode node;

        NodeItem(int operator, ExpressionNode node) {
            this.operator = operator;
            this.node = node;
        }

        public int getOperator() {
            return operator;
        }

        public ExpressionNode getNode() {
            return node;
        }
    }
}
