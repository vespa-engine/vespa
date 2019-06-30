// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text;

/**
 * Pretty prints any parenthesis expression
 *
 * @author bratseth
 */
public class ParenthesisExpressionPrettyPrinter {

    private static final int indentUnit = 2;

    public static String prettyPrint(String parenthesisExpression) {
        StringBuilder b = new StringBuilder();
        prettyPrint(parenthesisExpression, 0, b);
        return b.toString();
    }

    private static void prettyPrint(String expression, int indent, StringBuilder b) {
        int nextStartParenthesis = expression.indexOf("(");
        int nextEndParenthesis = expression.indexOf(")");
        if (nextStartParenthesis < 0)
            nextStartParenthesis = Integer.MAX_VALUE;
        if (nextEndParenthesis < 0)
            nextEndParenthesis = Integer.MAX_VALUE;

        boolean start = nextStartParenthesis < nextEndParenthesis;
        int nextParenthesis = Math.min(nextStartParenthesis, nextEndParenthesis);

        int effectiveIndent = start || nextParenthesis > 0 ? indent : indent - 2;
        b.append(" ".repeat(Math.max(0, effectiveIndent)));
        if (nextParenthesis == Integer.MAX_VALUE) {
            b.append(expression);
        }
        else {
            if (! start && nextParenthesis > 0) {
                b.append(expression, 0, nextParenthesis).append("\n");
                b.append(" ".repeat(Math.max(0, indent - 2))).append(")\n");
            }
            else {
                b.append(expression, 0, nextParenthesis + 1).append("\n");
            }
            prettyPrint(expression.substring(nextParenthesis + 1), indent + (start ? indentUnit : -indentUnit), b);
        }
    }

}
