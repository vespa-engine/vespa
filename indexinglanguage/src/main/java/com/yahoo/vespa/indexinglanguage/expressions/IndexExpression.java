// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

/**
 * @author Simon Thoresen Hult
 */
public final class IndexExpression extends OutputExpression {

    public IndexExpression(String fieldName) {
        super("index", fieldName);
    }

    @Override
    public boolean equals(Object obj) {
        return super.equals(obj) && obj instanceof IndexExpression;
    }

}
