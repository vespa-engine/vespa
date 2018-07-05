// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;
import com.yahoo.document.datatypes.LongFieldValue;
import org.apache.commons.codec.binary.Base64;

/**
 * @author Simon Thoresen Hult
 */
public class Base64DecodeExpression extends Expression {

    @Override
    protected void doExecute(ExecutionContext ctx) {
        String input = String.valueOf(ctx.getValue());
        if (input.isEmpty()) {
            ctx.setValue(new LongFieldValue(Long.MIN_VALUE));
            return;
        }
        if (input.length() > 12) {
            throw new NumberFormatException("Base64 value '" + input + "' is out of range.");
        }
        byte[] decoded = Base64.decodeBase64(input);
        if (decoded == null || decoded.length == 0) {
            throw new NumberFormatException("Illegal base64 value '" + input + "'.");
        }
        long output = 0;
        for (int i = decoded.length; --i >= 0;) {
            output = (output << 8) + (((int)decoded[i]) & 0xff);
        }
        ctx.setValue(new LongFieldValue(output));
    }

    @Override
    protected void doVerify(VerificationContext context) {
        context.setValue(createdOutputType());
    }

    @Override
    public DataType requiredInputType() {
        return DataType.STRING;
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
