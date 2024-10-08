// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.ArrayDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.Array;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.text.StringUtilities;

import java.util.regex.Pattern;

/**
 * @author Simon Thoresen Hult
 */
public final class SplitExpression extends Expression {

    private final Pattern splitPattern;

    public SplitExpression(String splitString) {
        super(DataType.STRING);
        this.splitPattern = Pattern.compile(splitString);
    }

    public Pattern getSplitPattern() { return splitPattern; }

    @Override
    public DataType setInputType(DataType input, VerificationContext context) {
        super.setInputType(input, context);
        if (input != DataType.STRING)
            throw new IllegalArgumentException("split requires a string input, but got " + input);
        return new ArrayDataType(DataType.STRING);
    }

    @Override
    public DataType setNeededOutputType(DataType output, VerificationContext context) {
        super.setNeededOutputType(output, context);
        if ( ! (output instanceof ArrayDataType) && output.getNestedType() == DataType.STRING)
            throw new IllegalArgumentException("split produces a string array, but needs " + output);
        return DataType.STRING;
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        String input = String.valueOf(context.getCurrentValue());
        Array<StringFieldValue> output = new Array<>(DataType.getArray(DataType.STRING));
        if (!input.isEmpty()) {
            String[] splits = splitPattern.split(input);
            for (String split : splits) {
                output.add(new StringFieldValue(split));
            }
        }
        context.setCurrentValue(output);
    }

    @Override
    protected void doVerify(VerificationContext context) {
        context.setCurrentType(createdOutputType());
    }

    @Override
    public DataType createdOutputType() {
        return DataType.getArray(DataType.STRING);
    }

    @Override
    public String toString() {
        return "split \"" + StringUtilities.escape(splitPattern.toString(), '"') + "\"";
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof SplitExpression rhs)) return false;
        if (!splitPattern.toString().equals(rhs.splitPattern.toString())) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode() + splitPattern.toString().hashCode();
    }

}
