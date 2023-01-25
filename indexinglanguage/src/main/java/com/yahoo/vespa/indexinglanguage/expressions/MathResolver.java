// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/**
 * @author Simon Thoresen Hult
 */
public class MathResolver {

    private final List<Item> items = new LinkedList<>();

    public void push(ArithmeticExpression.Operator op, Expression exp) {
        if (items.isEmpty() && op != ArithmeticExpression.Operator.ADD)
            throw new IllegalArgumentException("First item in an arithmetic operation must be an addition, not " + op);
        items.add(new Item(op, exp));
    }

    public Expression resolve() {
        Deque<Item> stack = new ArrayDeque<>();
        stack.push(items.remove(0));
        while (!items.isEmpty()) {
            Item item = items.remove(0);
            while (stack.size() > 1 && stack.peek().op.precedes(item.op)) {
                pop(stack);
            }
            stack.push(item);
        }
        while (stack.size() > 1) {
            pop(stack);
        }
        return stack.pop().exp;
    }

    private void pop(Deque<Item> stack) {
        Item rhs = stack.pop();
        Item lhs = stack.peek();
        lhs.exp = new ArithmeticExpression(lhs.exp, rhs.op, rhs.exp);
    }

    private static class Item {

        final ArithmeticExpression.Operator op;
        Expression exp;

        Item(ArithmeticExpression.Operator op, Expression exp) {
            this.op = Objects.requireNonNull(op);
            this.exp = exp;
        }
    }

}
