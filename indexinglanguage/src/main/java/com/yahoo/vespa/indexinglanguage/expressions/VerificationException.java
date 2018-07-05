// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

/**
 * @author Simon Thoresen Hult
 */
public class VerificationException extends RuntimeException {

    private final Expression exp;

    public VerificationException(Expression exp, String msg) {
        super(msg);
        this.exp = exp;
    }

    public Expression getExpression() {
        return exp;
    }

    @Override
    public String toString() {
        return getClass().getName() + ": For expression '" + exp + "': " + getMessage();
    }
}
