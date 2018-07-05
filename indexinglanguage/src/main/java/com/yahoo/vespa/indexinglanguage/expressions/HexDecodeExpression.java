// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;
import com.yahoo.document.datatypes.LongFieldValue;

import java.math.BigInteger;

/**
 * @author Simon Thoresen Hult
 */
public class HexDecodeExpression extends Expression {

    private static final BigInteger ULONG_MAX = new BigInteger("18446744073709551616");

    @Override
    protected void doExecute(ExecutionContext ctx) {
        String input = String.valueOf(ctx.getValue());
        if (input.isEmpty()) {
            ctx.setValue(new LongFieldValue(Long.MIN_VALUE));
            return;
        }
        BigInteger output;
        try {
            output = new BigInteger(input, 16);
        } catch (NumberFormatException e) {
            throw new NumberFormatException("Illegal hex value '" + input + "'.");
        }
        if (output.bitLength() > 64) {
            throw new NumberFormatException("Hex value '" + input + "' is out of range.");
        }
        if (output.compareTo(BigInteger.ZERO) == 1 && output.bitLength() == 64) {
            output = output.subtract(ULONG_MAX); // flip to negative
        }
        ctx.setValue(new LongFieldValue(output.longValue()));
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
        return "hexdecode";
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof HexDecodeExpression;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
