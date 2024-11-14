// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.LongFieldValue;
import java.time.Instant;

/**
 * Converts ISO-8601 formatted date string to UNIX Epoch Time in seconds
 *
 * @author bergum
 */
public class ToEpochSecondExpression extends Expression {

    public ToEpochSecondExpression() {
        super(DataType.STRING); //only accept string input
    }

    @Override
    public DataType setInputType(DataType input, VerificationContext context) {
        super.setInputType(input, DataType.STRING, context);
        return DataType.LONG;
    }

    @Override
    public DataType setOutputType(DataType output, VerificationContext context) {
        super.setOutputType(DataType.LONG, output, null, context);
        return DataType.STRING;
    }

    @Override
    protected void doVerify(VerificationContext context) {
        context.setCurrentType(createdOutputType());
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        String inputString = String.valueOf(context.getCurrentValue());
        long epochTime =  Instant.parse(inputString).getEpochSecond();
        context.setCurrentValue(new LongFieldValue(epochTime));
    }

    @Override
    public DataType createdOutputType() { return DataType.LONG; }

    @Override
    public String toString() { return "to_epoch_second"; }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ToEpochSecondExpression;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}
