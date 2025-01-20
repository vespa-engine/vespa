// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.LongFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;

import java.util.Base64;

/**
 * @author Simon Thoresen Hult
 */
public final class Base64EncodeExpression extends Expression {

    @Override
    public DataType setInputType(DataType inputType, VerificationContext context) {
        super.setInputType(inputType, DataType.LONG, context);
        return DataType.STRING;
    }

    @Override
    public DataType setOutputType(DataType outputType, VerificationContext context) {
        super.setOutputType(DataType.STRING, outputType, null, context);
        return DataType.LONG;
    }

    @Override
    protected void doVerify(VerificationContext context) {
        context.setCurrentType(createdOutputType());
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        long input = ((LongFieldValue) context.getCurrentValue()).getLong();
        byte[] output = new byte[8];
        for (int i = 0; i < output.length; ++i) {
            output[i] = (byte)(input & 0xffL);
            input >>>= 8;
        }
        String encoded = Base64.getEncoder().encodeToString(output);
        context.setCurrentValue(new StringFieldValue(encoded));
    }

    @Override
    public DataType createdOutputType() { return DataType.STRING; }

    @Override
    public String toString() { return "base64encode"; }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Base64EncodeExpression)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}
