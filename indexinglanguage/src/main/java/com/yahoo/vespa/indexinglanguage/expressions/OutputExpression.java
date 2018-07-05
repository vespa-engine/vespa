// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;

/**
 * @author Simon Thoresen Hult
 */
public abstract class OutputExpression extends Expression {

    private final String image;
    private final String fieldName;

    public OutputExpression(String image, String fieldName) {
        this.image = image;
        this.fieldName = fieldName;
    }

    public String getFieldName() {
        return fieldName;
    }

    @Override
    protected void doExecute(ExecutionContext ctx) {
        ctx.setOutputValue(this, fieldName, ctx.getValue());
    }

    @Override
    protected void doVerify(VerificationContext context) {
        context.tryOutputType(this, fieldName, context.getValue());
    }

    @Override
    public DataType requiredInputType() {
        return UnresolvedDataType.INSTANCE;
    }

    @Override
    public DataType createdOutputType() {
        return null;
    }

    @Override
    public String toString() {
        return image + (fieldName != null ? " " + fieldName : "");
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof OutputExpression)) {
            return false;
        }
        OutputExpression rhs = (OutputExpression)obj;
        if (!equals(fieldName, rhs.fieldName)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode() + (fieldName != null ? fieldName.hashCode() : 0);
    }
}
