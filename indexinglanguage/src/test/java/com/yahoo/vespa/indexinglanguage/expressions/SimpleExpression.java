// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;
import com.yahoo.document.datatypes.FieldValue;

/**
 * @author Simon Thoresen Hult
 */
class SimpleExpression extends Expression {

    private boolean hasExecuteValue = false;
    private boolean hasVerifyValue = false;
    private FieldValue executeValue;
    private DataType verifyValue;
    private DataType requiredInput;
    private DataType createdOutput;

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

    public SimpleExpression setRequiredInput(DataType requiredInput) {
        this.requiredInput = requiredInput;
        return this;
    }

    public SimpleExpression setCreatedOutput(DataType createdOutput) {
        this.createdOutput = createdOutput;
        return this;
    }

    @Override
    protected void doExecute(ExecutionContext ctx) {
        if (hasExecuteValue) {
            ctx.setValue(executeValue);
        }
    }

    @Override
    protected void doVerify(VerificationContext context) {
        if (hasVerifyValue) {
            context.setValue(verifyValue);
        }
    }

    @Override
    public DataType requiredInputType() {
        return requiredInput;
    }

    @Override
    public DataType createdOutputType() {
        return createdOutput;
    }

    @Override
    public int hashCode() {
        return hashCode(executeValue) + hashCode(verifyValue) + hashCode(requiredInput) + hashCode(createdOutput);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof SimpleExpression)) {
            return false;
        }
        SimpleExpression rhs = (SimpleExpression)obj;
        if (hasExecuteValue != rhs.hasExecuteValue) {
            return false;
        }
        if (!equals(executeValue, rhs.executeValue)) {
            return false;
        }
        if (hasVerifyValue != rhs.hasVerifyValue) {
            return false;
        }
        if (!equals(verifyValue, rhs.verifyValue)) {
            return false;
        }
        if (!equals(requiredInput, rhs.requiredInput)) {
            return false;
        }
        if (!equals(createdOutput, rhs.createdOutput)) {
            return false;
        }
        return true;
    }

    public static SimpleExpression newOutput(DataType createdOutput) {
        return new SimpleExpression().setCreatedOutput(createdOutput)
                                     .setVerifyValue(createdOutput);
    }

    public static SimpleExpression newRequired(DataType requiredInput) {
        return new SimpleExpression().setRequiredInput(requiredInput);
    }

    public static SimpleExpression newConversion(DataType requiredInput, DataType createdOutput) {
        return new SimpleExpression().setRequiredInput(requiredInput)
                                     .setCreatedOutput(createdOutput)
                                     .setExecuteValue(createdOutput.createFieldValue())
                                     .setVerifyValue(createdOutput);
    }

    private static int hashCode(Object obj) {
        return obj != null ? obj.hashCode() : 0;
    }
}
