// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.select.rule;

import com.yahoo.document.BucketIdFactory;
import com.yahoo.document.datatypes.NumericFieldValue;
import com.yahoo.document.select.*;

import java.util.List;
import java.util.ArrayList;
import java.util.Stack;

/**
 * @author Simon Thoresen Hult
 */
public class ArithmeticNode implements ExpressionNode {

    public static final int NOP = 0;
    public static final int ADD = 1;
    public static final int SUB = 2;
    public static final int MOD = 3;
    public static final int DIV = 4;
    public static final int MUL = 5;

    private final List<NodeItem> items = new ArrayList<NodeItem>();

    public ArithmeticNode() {
        // empty
    }

    public ArithmeticNode add(String operator, ExpressionNode node) {
        items.add(new NodeItem(stringToOperator(operator), node));
        return this;
    }

    public List<NodeItem> getItems() {
        return items;
    }

    // Inherit doc from ExpressionNode.
    public BucketSet getBucketSet(BucketIdFactory factory) {
        return null;
    }

    // Inherit doc from ExpressionNode.
    public Object evaluate(Context context) {
        StringBuilder ret = null;        
        Stack<ValueItem> buf = new Stack<ValueItem>();
        for (int i = 0; i < items.size(); ++i) {
            NodeItem item = items.get(i);
            Object val = item.node.evaluate(context);

            if (val == null) {
                throw new IllegalArgumentException("Can not perform arithmetic on null value (referencing missing field?)");
            }

            if (val instanceof AttributeNode.VariableValueList) {
                AttributeNode.VariableValueList value = (AttributeNode.VariableValueList)val;
                if (value.size() == 0) {
                    throw new IllegalArgumentException("Can not perform arithmetic on missing field: "
                            + item.node.toString());
                } else if (value.size() != 1) {
                    throw new IllegalStateException("Arithmetic is only valid for single values.");
                } else {
                    val = value.get(0).getValue();
                }
            }

            if (val instanceof NumericFieldValue) {
                val = ((NumericFieldValue)val).getNumber();  
            }

            if (val instanceof String) {
                if (i == 0) {
                    ret = new StringBuilder();
                }
                if (ret != null) {
                    ret.append(val);
                    continue;
                }
            } else if (Number.class.isInstance(val)) {
                if (!buf.isEmpty()) {
                    while (buf.peek().operator > item.operator) {
                        popOffTheTop(buf);
                    }
                }
                buf.push(new ValueItem(item.operator, (Number)val));
                continue;
            }
            throw new IllegalStateException("Term '" + item.node + " with class " + val.getClass() + "' does not evaluate to a number.");
        }
        if (ret != null) {
            return ret.toString();
        }
        while (buf.size() > 1) {
            popOffTheTop(buf);
        }
        return buf.pop().value;
    }

    private void popOffTheTop(Stack<ValueItem> buf) {
        ValueItem rhs = buf.pop();
        ValueItem lhs = buf.pop();
        switch (rhs.operator) {
            case ADD:
                lhs.value = lhs.value.doubleValue() + rhs.value.doubleValue();
                break;
            case SUB:
                lhs.value = lhs.value.doubleValue() - rhs.value.doubleValue();
                break;
            case DIV:
                lhs.value = lhs.value.doubleValue() / rhs.value.doubleValue();
                break;
            case MUL:
                lhs.value = lhs.value.doubleValue() * rhs.value.doubleValue();
                break;
            case MOD:
                lhs.value = lhs.value.longValue() % rhs.value.longValue();
                break;
            default:
                throw new IllegalStateException("Arithmetic operator " + rhs.operator + " not supported.");
        }
        buf.push(lhs);
    }

    @Override
    public String toString() {
        StringBuilder ret = new StringBuilder();
        for (NodeItem item : items) {
            if (item.operator != NOP) {
                ret.append(" ").append(operatorToString(item.operator)).append(" ");
            }
            ret.append(item.node);
        }
        return ret.toString();
    }

    public String operatorToString(int operator) {
        switch (operator) {
            case NOP:
                return null;
            case ADD:
                return "+";
            case SUB:
                return "-";
            case MOD:
                return "%";
            case DIV:
                return "/";
            case MUL:
                return "*";
            default:
                throw new IllegalStateException("Arithmetic operator " + operator + " not supported.");
        }
    }

    private int stringToOperator(String operator) {
        if (operator == null) {
            return NOP;
        } else if (operator.equals("+")) {
            return ADD;
        } else if (operator.equals("-")) {
            return SUB;
        } else if (operator.equals("%")) {
            return MOD;
        } else if (operator.equals("/")) {
            return DIV;
        } else if (operator.equals("*")) {
            return MUL;
        } else {
            throw new IllegalStateException("Arithmetic operator '" + operator + "' not supported.");
        }
    }

    public OrderingSpecification getOrdering(int order) {
        return null;
    }

    public void accept(Visitor visitor) {
        visitor.visit(this);
    }

    private class ValueItem {
        public int operator;
        public Number value;

        public ValueItem(int operator, Number value) {
            this.operator = operator;
            this.value = value;
        }
    }

    public static class NodeItem {
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
