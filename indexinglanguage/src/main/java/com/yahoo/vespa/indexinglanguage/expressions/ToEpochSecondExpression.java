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
    protected void doExecute(ExecutionContext context) {
        String inputString = String.valueOf(context.getValue());
        long epochTime =  Instant.parse(inputString).getEpochSecond();
        context.setValue(new LongFieldValue(epochTime));
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
        return "to_epoch_second";
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ToEpochSecondExpression;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}
