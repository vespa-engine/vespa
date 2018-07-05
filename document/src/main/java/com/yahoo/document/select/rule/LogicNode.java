// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.select.rule;

import com.yahoo.document.BucketIdFactory;
import com.yahoo.document.select.BucketSet;
import com.yahoo.document.select.Context;
import com.yahoo.document.select.OrderingSpecification;
import com.yahoo.document.select.ResultList;
import com.yahoo.document.select.Visitor;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

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
    private final List<NodeItem> items = new ArrayList<NodeItem>();

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

    // Inherit doc from ExpressionNode.
    public BucketSet getBucketSet(BucketIdFactory factory) {
        Stack<BucketItem> buf = new Stack<>();
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

    public OrderingSpecification getOrdering(int order) {
        Stack<OrderingItem> buf = new Stack<>();
        for (NodeItem item : items) {
            if (!buf.isEmpty()) {
                while (buf.peek().operator > item.operator) {
                    pickOrdering(buf);
                }
            }
            buf.push(new OrderingItem(item.operator, item.node.getOrdering(order)));
        }
        while (buf.size() > 1) {
            pickOrdering(buf);
        }
        return buf.pop().ordering;
    }

    private OrderingSpecification pickOrdering(OrderingSpecification a, OrderingSpecification b, boolean isAnd) {
        if (a.getWidthBits() == b.getWidthBits() && a.getDivisionBits() == b.getDivisionBits() && a.getOrder() == b.getOrder()) {
            if ((a.getOrder() == OrderingSpecification.ASCENDING && isAnd) ||
                (a.getOrder() == OrderingSpecification.DESCENDING && !isAnd)) {
                return new OrderingSpecification(a.getOrder(), Math.max(a.getOrderingStart(), b.getOrderingStart()), b.getWidthBits(), a.getDivisionBits());
            } else {
                return new OrderingSpecification(a.getOrder(), Math.min(a.getOrderingStart(), b.getOrderingStart()), b.getWidthBits(), a.getDivisionBits());
            }
        }
        return null;
    }

    private void pickOrdering(Stack<OrderingItem> buf) {
        OrderingItem rhs = buf.pop();
        OrderingItem lhs = buf.pop();
        switch (rhs.operator) {
        case AND:
            if (lhs.ordering == null) {
                lhs.ordering = rhs.ordering;
            } else if (rhs.ordering == null) {
                // empty
            } else {
                lhs.ordering = pickOrdering(lhs.ordering, rhs.ordering, true);
            }
            break;
        case OR:
            if (lhs.ordering != null && rhs.ordering != null) {
                lhs.ordering = pickOrdering(lhs.ordering, rhs.ordering, false);
            } else {
                lhs.ordering = null;
            }
            break;
        default:
            lhs.ordering = null;
        }
        buf.push(lhs);
    }

    /**
     * Combines the top two items of the given stack using the operator of the second.
     *
     * @param buf The stack of bucket items.
     */
    private void combineBuckets(Stack<BucketItem> buf) {
        BucketItem rhs = buf.pop();
        BucketItem lhs = buf.pop();
        switch (rhs.operator) {
            case AND:
                if (lhs.buckets == null) {
                    lhs.buckets = rhs.buckets;
                } else if (rhs.buckets == null) {
                    // empty
                } else {
                    lhs.buckets = lhs.buckets.intersection(rhs.buckets);
                }
                break;
            case OR:
                if (lhs.buckets == null) {
                    // empty
                } else if (rhs.buckets == null) {
                    lhs.buckets = null;
                } else {
                    lhs.buckets = lhs.buckets.union(rhs.buckets);
                }
                break;
            default:
                throw new IllegalStateException("Arithmetic operator " + rhs.operator + " not supported.");
        }
        buf.push(lhs);
    }

    // Inherit doc from ExpressionNode.
    @Override
    public Object evaluate(Context context) {
        Stack<ValueItem> buf = new Stack<>();
        for (NodeItem item : items) {
            if ( ! buf.isEmpty()) {
                while (buf.peek().operator > item.operator) {
                    combineValues(buf);
                }
            }
            
            buf.push(new ValueItem(item.operator, ResultList.toResultList(item.node.evaluate(context))));
        }
        while (buf.size() > 1) {
            combineValues(buf);
        }
        return buf.pop().value;
    }

    /**
     * Combines the top two items of the given stack using the operator of the second.
     *
     * @param buf The stack of values.
     */
    private void combineValues(Stack<ValueItem> buf) {
        ValueItem rhs = buf.pop();
        ValueItem lhs = buf.pop();

        switch (rhs.operator) {
            case AND:
                buf.push(new ValueItem(lhs.operator, lhs.value.combineAND(rhs.value)));
                break;
            case OR:
                buf.push(new ValueItem(lhs.operator, lhs.value.combineOR(rhs.value)));
                break;
            default:
                throw new IllegalStateException("Arithmetic operator " + rhs.operator + " not supported.");
        }
    }

    public void accept(Visitor visitor) {
        visitor.visit(this);
    }

    // Inherit doc from Object.
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
    public String operatorToString(int operator) {
        switch (operator) {
            case NOP:
                return null;
            case OR:
                return "or";
            case AND:
                return "and";
            default:
                throw new IllegalStateException("Logical operator " + operator + " not supported.");
        }
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
    private final class ValueItem {
        private int operator;
        private ResultList value;

        public ValueItem(int operator, ResultList value) {
            this.operator = operator;
            this.value = value;
        }
    }

    /**
     * Private class to store bucket sets in a stack.
     */
    private final class BucketItem {
        private int operator;
        private BucketSet buckets;

        public BucketItem(int operator, BucketSet buckets) {
            this.operator = operator;
            this.buckets = buckets;
        }
    }

    /**
     * Private class to store ordering expressions in a stack.
     */
    private final class OrderingItem {
        private int operator;
        private OrderingSpecification ordering;

        public OrderingItem(int operator, OrderingSpecification orderSpec) {
            this.operator = operator;
            this.ordering = orderSpec;
        }
    }

    /**
     * Private class to store expression nodes in a stack.
     */
    public final class NodeItem {
        private int operator;
        private ExpressionNode node;

        public NodeItem(int operator, ExpressionNode node) {
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
