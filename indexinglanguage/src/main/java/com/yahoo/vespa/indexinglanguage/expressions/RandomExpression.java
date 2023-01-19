// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
        super(null);
        this.max = max;
    }

    public Integer getMaxValue() {
        return max;
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        int max;
        max = Objects.requireNonNullElseGet(this.max, () -> Integer.parseInt(String.valueOf(context.getValue())));
        context.setValue(new IntegerFieldValue(ThreadLocalRandom.current().nextInt(max)));
    }

    @Override
    protected void doVerify(VerificationContext context) {
        context.setValueType(createdOutputType());
    }

    @Override
    public DataType createdOutputType() {
        return DataType.INT;
    }

    @Override
    public String toString() {
        return "random" + (max != null ? " " + max : "");
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof RandomExpression rhs)) return false;
        if (!equals(max, rhs.max)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
