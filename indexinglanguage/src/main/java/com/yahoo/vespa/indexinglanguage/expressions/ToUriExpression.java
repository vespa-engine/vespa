// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.datatypes.UriFieldValue;

/**
 * @author bratseth
 */
public final class ToUriExpression extends Expression {

    @Override
    public DataType setInputType(DataType input, VerificationContext context) {
        super.setInputType(input, context);
        return DataType.URI;
    }

    @Override
    public DataType setOutputType(DataType output, VerificationContext context) {
        super.setOutputType(DataType.URI, output, null, context);
        return getInputType(context);
    }

    @Override
    protected void doVerify(VerificationContext context) {
        context.setCurrentType(createdOutputType());
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        context.setCurrentValue(new UriFieldValue(String.valueOf(context.getCurrentValue())));
    }

    @Override
    public DataType createdOutputType() { return DataType.URI; }

    @Override
    public String toString() { return "to_uri"; }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ToUriExpression;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}
