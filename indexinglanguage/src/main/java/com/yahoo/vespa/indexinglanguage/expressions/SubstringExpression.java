// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.text.Text;

/**
 * @author Simon Thoresen Hult
 */
public final class SubstringExpression extends Expression {

    private final int from;
    private final int to;

    public SubstringExpression(int from, int to) {
        super(DataType.STRING);
        if (from < 0 || to < 0 || to < from) {
            throw new IndexOutOfBoundsException();
        }
        this.from = from;
        this.to = to;
    }

    public int getFrom() { return from; }

    public int getTo() { return to; }

    @Override
    public DataType setInputType(DataType inputType, VerificationContext context) {
        return super.setInputType(inputType, DataType.STRING, context);
    }

    @Override
    public DataType setOutputType(DataType outputType, VerificationContext context) {
        return super.setOutputType(DataType.STRING, outputType, null, context);
    }

    @Override
    protected void doVerify(VerificationContext context) {
        context.setCurrentType(createdOutputType());
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        String input = String.valueOf(context.getCurrentValue());
        String substring = Text.substringByCodepoints(input, from, to);
        context.setCurrentValue(new StringFieldValue(substring));
    }

    @Override
    public DataType createdOutputType() {
        return DataType.STRING;
    }

    @Override
    public String toString() {
        return "substring " + from + " " + to;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof SubstringExpression rhs)) return false;
        if (from != rhs.from) return false;
        if (to != rhs.to) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode() +
               Integer.valueOf(from).hashCode() +
               Integer.valueOf(to).hashCode();
    }

}
