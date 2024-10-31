// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

/**
 * @author Simon Thoresen Hult
 */
public class VerificationException extends IllegalArgumentException {

    private final Class<?> type;
    private final String expression;

    public VerificationException(Expression expression, String message) {
        super("Invalid expression '" + expression + "': " + message);
        if (expression != null) {
            this.type = expression.getClass();
            this.expression = expression.toString();
        } else {
            this.type = null;
            this.expression = "null";
        }
    }

    public VerificationException(Class<?> expression, String message) {
        super("Invalid expression of type '" + expression.getSimpleName() + "': " + message);
        this.type = expression;
        this.expression = expression.getName();
    }

    public String getExpression() { return expression; }

    public Class<?> getExpressionType() { return type; }

    @Override
    public String toString() {
        return getClass().getName() + ": " + getMessage();
    }

}
