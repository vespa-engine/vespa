// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

/**
 * An output expression which has no search side effects.
 *
 * @author steinar
 */
public final class PassthroughExpression extends OutputExpression {

    private static final String PASSTHROUGH = "passthrough";

    public PassthroughExpression(String fieldName) {
        super(PASSTHROUGH, fieldName);
    }

    @Override
    public boolean equals(Object obj) {
        return super.equals(obj) && obj instanceof PassthroughExpression;
    }

}
