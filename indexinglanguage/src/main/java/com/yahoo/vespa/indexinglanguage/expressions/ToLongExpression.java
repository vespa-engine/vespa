// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;
import com.yahoo.document.datatypes.LongFieldValue;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 */
public class ToLongExpression extends Expression {

    @Override
    protected void doExecute(ExecutionContext ctx) {
        ctx.setValue(new LongFieldValue(Long.valueOf(String.valueOf(ctx.getValue()))));
    }

    @Override
    protected void doVerify(VerificationContext ctx) {
        ctx.setValue(createdOutputType());
    }

    @Override
    public DataType requiredInputType() {
        return UnresolvedDataType.INSTANCE;
    }

    @Override
    public DataType createdOutputType() {
        return DataType.LONG;
    }

    @Override
    public String toString() {
        return "to_long";
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ToLongExpression;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
