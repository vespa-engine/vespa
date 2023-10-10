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

    public Base64EncodeExpression() {
        super(DataType.LONG);
    }
    @Override
    protected void doExecute(ExecutionContext context) {
        long input = ((LongFieldValue) context.getValue()).getLong();
        byte[] output = new byte[8];
        for (int i = 0; i < output.length; ++i) {
            output[i] = (byte)(input & 0xffL);
            input >>>= 8;
        }
        String encoded = Base64.getEncoder().encodeToString(output);
        context.setValue(new StringFieldValue(encoded));
    }

    @Override
    protected void doVerify(VerificationContext context) {
        context.setValueType(createdOutputType());
    }

    @Override
    public DataType createdOutputType() {
        return DataType.STRING;
    }

    @Override
    public String toString() {
        return "base64encode";
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Base64EncodeExpression)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
