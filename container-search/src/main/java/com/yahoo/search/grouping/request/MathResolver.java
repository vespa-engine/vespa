// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

/**
 * This is a helper class for resolving arithmetic operations over {@link GroupingExpression} objects. To resolve an
 * operation simply push operator-expression pairs onto it, before calling {@link #resolve()} to retrieve the single
 * corresponding grouping expression object.
 *
 * @author Simon Thoresen Hult
 */
public class MathResolver {

    public enum Type {

        ADD(0, "+"),
        SUB(1, "-"),
        DIV(2, "/"),
        MOD(3, "%"),
        MUL(4, "*");

        private final int pre;
        private final String image;

        Type(int pre, String image) {
            this.pre = pre;
            this.image = image;
        }
    }

    private final List<Item> items = new LinkedList<>();

    /**
     * Pushes the given operator-expression pair onto this math resolver. Once all pairs have been pushed using this
     * method, call {@link #resolve()} to retrieve to combined grouping expression.
     *
     * @param type The operator that appears before the expression being pushed.
     * @param exp  The expression to push.
     */
    public void push(Type type, GroupingExpression exp) {
        if (items.isEmpty() && type != Type.ADD) {
            throw new IllegalArgumentException("First item in an arithmetic operation must be an addition.");
        }
        items.add(new Item(type, exp));
    }

    /**
     * Converts the internal list of operator-expression pairs into a corresponding combined grouping expression. When
     * this method returns there is no residue of the conversion, and this object can be reused.
     *
     * @return The grouping expression corresponding to the pushed arithmetic operations.
     */
    public GroupingExpression resolve() {
        if (items.size() == 1) {
            return items.remove(0).exp; // optimize common case
        }
        Deque<Item> stack = new ArrayDeque<>();
        stack.push(items.remove(0));
        while (!items.isEmpty()) {
            Item item = items.remove(0);
            while (stack.size() > 1 && stack.peek().type.pre >= item.type.pre) {
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
        switch (rhs.type) {
            case ADD -> lhs.exp = new AddFunction(lhs.exp, rhs.exp);
            case DIV -> lhs.exp = new DivFunction(lhs.exp, rhs.exp);
            case MOD -> lhs.exp = new ModFunction(lhs.exp, rhs.exp);
            case MUL -> lhs.exp = new MulFunction(lhs.exp, rhs.exp);
            case SUB -> lhs.exp = new SubFunction(lhs.exp, rhs.exp);
            default -> throw new UnsupportedOperationException("Operator " + rhs.type + " not supported.");
        }
    }

    @Override
    public String toString() {
        StringBuilder ret = new StringBuilder();
        for (int i = 0, len = items.size(); i < len; ++i) {
            Item item = items.get(i);
            if (i != 0) {
                ret.append(" ").append(item.type.image).append(" ");
            }
            ret.append(item.exp.toString());
        }
        return ret.toString();
    }

    private static class Item {
        final Type type;
        GroupingExpression exp;

        Item(Type type, GroupingExpression exp) {
            this.type = type;
            this.exp = exp;
        }
    }
}
