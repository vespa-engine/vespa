// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.ArrayDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;
import com.yahoo.document.datatypes.Array;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.text.StringUtilities;

import java.util.Iterator;

/**
 * @author Simon Thoresen Hult
 */
public class JoinExpression extends Expression {

    private final String delimiter;

    public JoinExpression(String delimiter) {
        this.delimiter = delimiter;
    }

    public String getDelimiter() {
        return delimiter;
    }

    @SuppressWarnings({ "unchecked" })
    @Override
    protected void doExecute(ExecutionContext ctx) {
        FieldValue input = ctx.getValue();
        if (!(input instanceof Array)) {
            throw new IllegalArgumentException("Expected Array input, got " + input.getDataType().getName() + ".");
        }
        StringBuilder output = new StringBuilder();
        for (Iterator<FieldValue> it = ((Array)input).fieldValueIterator(); it.hasNext(); ) {
            output.append(String.valueOf(it.next()));
            if (it.hasNext()) {
                output.append(delimiter);
            }
        }
        ctx.setValue(new StringFieldValue(output.toString()));
    }

    @Override
    protected void doVerify(VerificationContext context) {
        DataType input = context.getValue();
        if (!(input instanceof ArrayDataType)) {
            throw new VerificationException(this, "Expected Array input, got " + input.getName() + ".");
        }
        context.setValue(createdOutputType());
    }

    @Override
    public DataType requiredInputType() {
        return UnresolvedDataType.INSTANCE;
    }

    @Override
    public DataType createdOutputType() {
        return DataType.STRING;
    }

    @Override
    public String toString() {
        return "join \"" + StringUtilities.escape(delimiter, '"') + "\"";
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof JoinExpression)) {
            return false;
        }
        JoinExpression rhs = (JoinExpression)obj;
        if (!delimiter.equals(rhs.delimiter)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode() + delimiter.hashCode();
    }
}
