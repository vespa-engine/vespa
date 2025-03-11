// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.IntegerFieldValue;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author Simon Thoresen Hult
 */
public final class RandomExpression extends Expression {

    private final Integer max;

    public RandomExpression() {
        this(null);
    }

    public RandomExpression(Integer max) {
        this.max = max;
    }

    @Override
    public boolean requiresInput() { return false; }

    public Integer getMaxValue() { return max; }

    @Override
    public DataType setInputType(DataType inputType, VerificationContext context) {
        super.setInputType(inputType, context);
        return DataType.INT;
    }

    @Override
    public DataType setOutputType(DataType outputType, VerificationContext context) {
        super.setOutputType(DataType.INT, outputType, null, context);
        return AnyDataType.instance;
    }

    @Override
    protected void doVerify(VerificationContext context) {
        context.setCurrentType(createdOutputType());
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        int max;
        max = Objects.requireNonNullElseGet(this.max, () -> Integer.parseInt(String.valueOf(context.getCurrentValue())));
        context.setCurrentValue(new IntegerFieldValue(ThreadLocalRandom.current().nextInt(max)));
    }

    @Override
    public DataType createdOutputType() { return DataType.INT; }

    @Override
    public String toString() {
        return "random" + (max != null ? " " + max : "");
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof RandomExpression rhs)) return false;
        if (!Objects.equals(max, rhs.max)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}
