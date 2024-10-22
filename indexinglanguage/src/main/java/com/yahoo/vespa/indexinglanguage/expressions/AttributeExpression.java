// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

/**
 * @author Simon Thoresen Hult
 */
public final class AttributeExpression extends OutputExpression {

    public AttributeExpression(String fieldName) {
        super("attribute", fieldName);
    }

    @Override
    public boolean equals(Object obj) {
        return super.equals(obj) && obj instanceof AttributeExpression;
    }
}
