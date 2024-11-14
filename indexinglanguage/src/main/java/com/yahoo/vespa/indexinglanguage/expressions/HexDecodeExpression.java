// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.LongFieldValue;

import java.math.BigInteger;

/**
 * @author Simon Thoresen Hult
 */
public final class HexDecodeExpression extends Expression {

    private static final BigInteger ULONG_MAX = new BigInteger("18446744073709551616");

    public HexDecodeExpression() {
        super(DataType.STRING);
    }

    @Override
    public DataType setInputType(DataType inputType, VerificationContext context) {
        super.setInputType(inputType, DataType.STRING, context);
        return DataType.LONG;
    }

    @Override
    public DataType setOutputType(DataType outputType, VerificationContext context) {
        super.setOutputType(DataType.LONG, outputType, null, context);
        return DataType.STRING;
    }

    @Override
    protected void doVerify(VerificationContext context) {
        context.setCurrentType(createdOutputType());
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        String input = String.valueOf(context.getCurrentValue());
        if (input.isEmpty()) {
            context.setCurrentValue(new LongFieldValue(Long.MIN_VALUE));
            return;
        }
        BigInteger output;
        try {
            output = new BigInteger(input, 16);
        } catch (NumberFormatException e) {
            throw new NumberFormatException("Illegal hex value '" + input + "'");
        }
        if (output.bitLength() > 64) {
            throw new NumberFormatException("Hex value '" + input + "' is out of range");
        }
        if (output.compareTo(BigInteger.ZERO) > 0 && output.bitLength() == 64) {
            output = output.subtract(ULONG_MAX); // flip to negative
        }
        context.setCurrentValue(new LongFieldValue(output.longValue()));
    }

    @Override
    public DataType createdOutputType() { return DataType.LONG; }

    @Override
    public String toString() { return "hexdecode"; }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof HexDecodeExpression;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}
