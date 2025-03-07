// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;

/**
 * @author Simon Thoresen Hult
 */
public final class GetVarExpression extends Expression {

    private final String variableName;

    public GetVarExpression(String variableName) {
        this.variableName = variableName;
    }

    @Override
    public boolean requiresInput() { return false; }

    public String getVariableName() { return variableName; }

    @Override
    public DataType setInputType(DataType inputType, VerificationContext context) {
        super.setInputType(inputType, context);
        DataType output = context.getVariable(variableName);
        if (output == null)
            throw new VerificationException(this, "Variable '" + variableName + "' not found");
        return output;
    }

    @Override
    public DataType setOutputType(DataType outputType, VerificationContext context) {
        super.setOutputType(context.getVariable(variableName), outputType, null, context);
        return AnyDataType.instance;
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        context.setCurrentValue(context.getVariable(variableName));
    }

    @Override
    public String toString() {
        return "get_var " + variableName;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof GetVarExpression rhs)) return false;
        if (!variableName.equals(rhs.variableName)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode() + variableName.hashCode();
    }

}
