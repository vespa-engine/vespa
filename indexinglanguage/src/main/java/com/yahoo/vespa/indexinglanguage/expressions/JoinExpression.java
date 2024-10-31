// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.ArrayDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.Array;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.text.StringUtilities;

import java.util.Iterator;

/**
 * @author Simon Thoresen Hult
 */
public final class JoinExpression extends Expression {

    private final String delimiter;

    public JoinExpression(String delimiter) {
        super(UnresolvedDataType.INSTANCE);
        this.delimiter = delimiter;
    }

    public String getDelimiter() { return delimiter; }

    @Override
    public DataType setInputType(DataType inputType, VerificationContext context) {
        super.setInputType(inputType, context);
        if ( ! (inputType instanceof ArrayDataType))
            throw new VerificationException(this, "Expected Array input, got type " + inputType.getName());
        return DataType.STRING;
    }

    @Override
    public DataType setOutputType(DataType outputType, VerificationContext context) {
        super.setOutputType(DataType.STRING, outputType, null,context);
        return null; // Cannot deduce since any array type is accepted
    }

    @Override
    protected void doVerify(VerificationContext context) {
        DataType input = context.getCurrentType();
        if (!(input instanceof ArrayDataType)) {
            throw new VerificationException(this, "Expected Array input, got type " + input.getName());
        }
        context.setCurrentType(createdOutputType());
    }

    @SuppressWarnings({ "unchecked" })
    @Override
    protected void doExecute(ExecutionContext context) {
        FieldValue input = context.getCurrentValue();
        if (!(input instanceof Array))
            throw new IllegalArgumentException("Expected Array input, got " + input.getDataType().getName());
        StringBuilder output = new StringBuilder();
        for (Iterator<FieldValue> it = ((Array)input).fieldValueIterator(); it.hasNext(); ) {
            output.append(it.next());
            if (it.hasNext()) {
                output.append(delimiter);
            }
        }
        context.setCurrentValue(new StringFieldValue(output.toString()));
    }

    @Override
    public DataType createdOutputType() { return DataType.STRING; }

    @Override
    public String toString() {
        return "join \"" + StringUtilities.escape(delimiter, '"') + "\"";
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof JoinExpression rhs)) return false;
        if (!delimiter.equals(rhs.delimiter)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode() + delimiter.hashCode();
    }
}
