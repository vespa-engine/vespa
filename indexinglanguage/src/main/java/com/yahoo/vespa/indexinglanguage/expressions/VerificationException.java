// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

/**
 * @author Simon Thoresen Hult
 */
public class VerificationException extends RuntimeException {

    private final Class<?> type;
    private final String exp;

    public VerificationException(Expression exp, String msg) {
        super(msg);
        if (exp != null) {
            this.type = exp.getClass();
            this.exp = exp.toString();
        } else {
            this.type = null;
            this.exp = "null";
        }
    }


    public VerificationException(Class<?> exp, String msg) {
        super(msg);
        this.type = exp;
        this.exp = exp.getName();
    }

    public String getExpression() {
        return exp;
    }
    public Class<?> getExpressionType() {
        return type;
    }

    @Override
    public String toString() {
        return getClass().getName() + ": For expression '" + exp + "': " + getMessage();
    }

}
