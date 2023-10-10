// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.FieldValue;

/**
 * @author Simon Thoresen Hult
 */
final class SimpleExpression extends Expression {

    private boolean hasExecuteValue = false;
    private boolean hasVerifyValue = false;
    private FieldValue executeValue;
    private DataType verifyValue;
    private DataType createdOutput;

    public SimpleExpression() {
        super(null);
    }
    public SimpleExpression(DataType requiredInput) {
        super(requiredInput);
    }

    public SimpleExpression setExecuteValue(FieldValue executeValue) {
        this.hasExecuteValue = true;
        this.executeValue = executeValue;
        return this;
    }

    public SimpleExpression setVerifyValue(DataType verifyValue) {
        this.hasVerifyValue = true;
        this.verifyValue = verifyValue;
        return this;
    }

    public SimpleExpression setCreatedOutput(DataType createdOutput) {
        this.createdOutput = createdOutput;
        return this;
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        if (hasExecuteValue) {
            context.setValue(executeValue);
        }
    }

    @Override
    protected void doVerify(VerificationContext context) {
        if (hasVerifyValue) {
            context.setValueType(verifyValue);
        }
    }

    @Override
    public DataType createdOutputType() {
        return createdOutput;
    }

    @Override
    public int hashCode() {
        return hashCode(executeValue) + hashCode(verifyValue) + hashCode(requiredInputType()) + hashCode(createdOutput);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof SimpleExpression other)) return false;
        if (hasExecuteValue != other.hasExecuteValue) return false;
        if (!equals(executeValue, other.executeValue)) return false;
        if (hasVerifyValue != other.hasVerifyValue) return false;
        if (!equals(verifyValue, other.verifyValue)) return false;
        if (!equals(requiredInputType(), other.requiredInputType())) return false;
        if (!equals(createdOutput, other.createdOutput)) return false;
        return true;
    }

    public static SimpleExpression newOutput(DataType createdOutput) {
        return new SimpleExpression(null).setCreatedOutput(createdOutput)
                                     .setVerifyValue(createdOutput);
    }

    public static SimpleExpression newRequired(DataType requiredInput) {
        return new SimpleExpression(requiredInput);
    }

    public static SimpleExpression newConversion(DataType requiredInput, DataType createdOutput) {
        return new SimpleExpression(requiredInput)
                                     .setCreatedOutput(createdOutput)
                                     .setExecuteValue(createdOutput.createFieldValue())
                                     .setVerifyValue(createdOutput);
    }

    private static int hashCode(Object obj) {
        return obj != null ? obj.hashCode() : 0;
    }
}
