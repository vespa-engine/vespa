// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.FloatFieldValue;

/**
 * @author Simon Thoresen Hult
 */
public final class ToFloatExpression extends Expression {

    @Override
    public DataType setInputType(DataType input, TypeContext context) {
        super.setInputType(input, context);
        return DataType.FLOAT;
    }

    @Override
    public DataType setOutputType(DataType output, TypeContext context) {
        super.setOutputType(DataType.FLOAT, output, null, context);
        return getInputType(context);
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        context.setCurrentValue(new FloatFieldValue(Float.valueOf(String.valueOf(context.getCurrentValue()))));
    }

    @Override
    public String toString() { return "to_float"; }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ToFloatExpression;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}
