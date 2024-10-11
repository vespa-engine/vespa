// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;

/**
 * @author Simon Thoresen Hult
 */
public final class GetVarExpression extends Expression {

    private final String variableName;

    public GetVarExpression(String variableName) {
        super(null);
        this.variableName = variableName;
    }

    public String getVariableName() { return variableName; }

    @Override
    public DataType setInputType(DataType inputType, VerificationContext context) {
        super.setInputType(inputType, context);
        return context.getVariable(variableName);
    }

    @Override
    public DataType setOutputType(DataType outputType, VerificationContext context) {
        var type = context.getVariable(variableName);
        // TODO:
        // if ( ! outputType.isAssignableFrom(type))
        //     throw new IllegalArgumentException(this + " produces , but " + outputType + " is required");
        super.setOutputType(outputType, context);
        return null; // Really ANY
    }

    @Override
    protected void doVerify(VerificationContext context) {
        DataType input = context.getVariable(variableName);
        if (input == null) {
            throw new VerificationException(this, "Variable '" + variableName + "' not found");
        }
        context.setCurrentType(input);
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        context.setCurrentValue(context.getVariable(variableName));
    }

    @Override
    public DataType createdOutputType() {
        return UnresolvedDataType.INSTANCE;
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
