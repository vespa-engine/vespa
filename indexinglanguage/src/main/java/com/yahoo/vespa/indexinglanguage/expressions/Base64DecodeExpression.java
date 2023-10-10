// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.LongFieldValue;

import java.util.Base64;

/**
 * @author Simon Thoresen Hult
 */
public final class Base64DecodeExpression extends Expression {

    public Base64DecodeExpression() {
        super(DataType.STRING);
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        String input = String.valueOf(context.getValue());
        if (input.isEmpty()) {
            context.setValue(new LongFieldValue(Long.MIN_VALUE));
            return;
        }
        if (input.length() > 12) {
            throw new NumberFormatException("Base64 value '" + input + "' is out of range");
        }
        byte[] decoded = Base64.getDecoder().decode(input);
        if (decoded == null || decoded.length == 0) {
            throw new NumberFormatException("Illegal base64 value '" + input + "'");
        }
        long output = 0;
        for (int i = decoded.length; --i >= 0;) {
            output = (output << 8) + (((int)decoded[i]) & 0xff);
        }
        context.setValue(new LongFieldValue(output));
    }

    @Override
    protected void doVerify(VerificationContext context) {
        context.setValueType(createdOutputType());
    }

    @Override
    public DataType createdOutputType() {
        return DataType.LONG;
    }

    @Override
    public String toString() {
        return "base64decode";
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Base64DecodeExpression;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
