// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text;

/**
 * Formats any parenthesis expression.
 * In addition to the obvious this can also operate in "two column mode",
 * wherein each chunk that will be formatted on a separate line may optionally
 * contain a prefix marked by a start and end tab sign which will be printed in a left column of the given fixed size.
 * The prefix itself is not formatted but will be cut if too long.
 *
 * @author bratseth
 */
public class ExpressionFormatter {

    private static final int indentUnit = 2;

    /** The size of the first column, or 0 if none */
    private final int firstColumnLength;

    /**
     * The desired size of the second column (or the entire line if no first column),
     * or 0 to split into multiple lines as much as possible.
     * Setting this collects larger chunks to one line across markup
     * but will not split too long lines that have no markup.
     */
    private final int secondColumnLength;

    private ExpressionFormatter(int firstColumnLength, int secondColumnLength) {
        this.firstColumnLength = firstColumnLength;
        this.secondColumnLength = secondColumnLength;
    }

    public String format(String parenthesisExpression) {
        StringBuilder b = new StringBuilder();
        format(parenthesisExpression, 0, b);
        while (b.length() > 0 && Character.isWhitespace(b.charAt(b.length() - 1)))
            b.setLength(b.length() - 1);
        return b.toString();
    }

    private void format(String expression, int indent, StringBuilder b) {
        if (expression.isEmpty()) return;
        expression = appendFirstColumn(expression, b);

        Markup next = Markup.next(expression);

        appendIndent( ! next.isClose() || next.position() > 0 ? indent : indent - 2, b);

        int endOfBalancedChunk = endOfBalancedChunk(expression, Math.max(0, secondColumnLength - indent));
        if (next.isEmpty()) {
            b.append(expression);
        }
        else if (endOfBalancedChunk > 0) {
            b.append(expression, 0, endOfBalancedChunk + 1).append("\n");
            format(expression.substring(endOfBalancedChunk + 1), indent, b);
        }
        else if (next.isComma()) {
            b.append(expression, 0, next.position() + 1).append("\n");
            format(expression.substring(next.position() + 1), indent, b);
        }
        else {
            if ( next.isClose() && next.position() > 0) { // content before end parenthesis: content, newline, then end parenthesis
                b.append(expression, 0, next.position()).append("\n");
                appendFirstColumn(")", b);
                appendIndent(indent - 2, b);
                b.append(")\n");
            }
            else {
                b.append(expression, 0, next.position() + 1).append("\n");
            }
            format(expression.substring(next.position() + 1), indent + (next.isOpen() ? indentUnit : -indentUnit), b);
        }
    }

    /** Returns the position of the end of a balanced chunk of at most the given size, or 0 if there is no such chunk */
    private int endOfBalancedChunk(String expression, int maxSize) {
        int chunkSize = 0;
        int i = 0;
        int nesting = 0;
        while (i < maxSize && i < expression.length()) {
            if (expression.charAt(i) == '\t') return chunkSize;
            if (expression.charAt(i) == '(') nesting++;
            if (expression.charAt(i) == ')') nesting--;
            if (nesting < 0) return chunkSize;
            if (nesting == 0 && ( expression.charAt(i)==')' || expression.charAt(i)==','))
                chunkSize = i;
            i++;
        }
        return chunkSize;
    }

    private String appendFirstColumn(String expression, StringBuilder b) {
        if (firstColumnLength == 0) return expression;

        while (expression.charAt(0) == ' ')
            expression = expression.substring(1);

        if (expression.charAt(0) == '\t') {
            int tab2 = expression.indexOf('\t', 1);
            if (tab2 >= 0) {
                String firstColumn = expression.substring(1, tab2);
                b.append(asSize(firstColumnLength, firstColumn)).append(" ");
                return expression.substring(tab2 + 1);
            }
        }
        appendIndent(firstColumnLength + 1, b);
        return expression;
    }

    private void appendIndent(int indent, StringBuilder b) {
        b.append(" ".repeat(Math.max(0, indent)));
    }

    private String asSize(int size, String s) {
        if (s.length() > size)
            return s.substring(0, size);
        else
            return s + " ".repeat(size - s.length());
    }

    /** Convenience method creating a formatter and using it to format the given expression */
    public static String on(String parenthesisExpression) {
        return new ExpressionFormatter(0, 80).format(parenthesisExpression);
    }

    public static ExpressionFormatter withLineLength(int maxLineLength) {
        return new ExpressionFormatter(0, maxLineLength);
    }

    public static ExpressionFormatter inTwoColumnMode(int firstColumnSize, int secondColumnSize) {
        return new ExpressionFormatter(firstColumnSize, secondColumnSize);
    }

    /** Contains the next position of each kind of markup, or Integer.MAX_VALUE if not present */
    private static class Markup {

        final int open, close, comma;

        private Markup(int open, int close, int comma) {
            this.open = open;
            this.close = close;
            this.comma = comma;
        }

        int position() {
            return Math.min(Math.min(open, close), comma);
        }

        boolean isOpen() {
            return open < close && open < comma;
        }

        boolean isClose() {
            return close < open && close < comma;
        }

        boolean isComma() {
            return comma < open && comma < close;
        }

        boolean isEmpty() {
            return open == Integer.MAX_VALUE && close == Integer.MAX_VALUE && comma == Integer.MAX_VALUE;
        }

        static Markup next(String expression) {
            int nextOpen = expression.indexOf('(');
            int nextClose = expression.indexOf(')');
            int nextComma = expression.indexOf(',');
            if (nextOpen < 0)
                nextOpen = Integer.MAX_VALUE;
            if (nextClose < 0)
                nextClose = Integer.MAX_VALUE;
            if (nextComma < 0)
                nextComma = Integer.MAX_VALUE;
            return new Markup(nextOpen, nextClose, nextComma);
        }

    }

}
