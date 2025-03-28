// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.LongFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.text.StringUtilities;

import java.util.Objects;

/**
 * @author Simon Thoresen Hult
 */
public final class ConstantExpression extends Expression {

    private final FieldValue value;

    public ConstantExpression(FieldValue value) {
        this.value = Objects.requireNonNull(value);
    }

    @Override
    public boolean requiresInput() { return false; }

    public FieldValue getValue() { return value; }

    @Override
    public DataType setInputType(DataType inputType, TypeContext context) {
        super.setInputType(inputType, context);
        return value.getDataType();
    }

    @Override
    public DataType setOutputType(DataType outputType, TypeContext context) {
        if (outputType != null && ! value.getDataType().isAssignableTo(outputType))
            throw new VerificationException(this, "Produces type " + value.getDataType().getName() + ", but type " +
                                            outputType.getName() + " is required");
        super.setOutputType(outputType, context);
        return AnyDataType.instance;
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        context.setCurrentValue(value);
    }

    @Override
    public String toString() {
        if (value instanceof StringFieldValue) {
            return "\"" + StringUtilities.escape(value.toString(), '"') + "\"";
        }
        if (value instanceof LongFieldValue) {
            return value + "L";
        }
        return value.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ConstantExpression rhs)) return false;
        if (!value.equals(rhs.value)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode() + value.hashCode();
    }

}
