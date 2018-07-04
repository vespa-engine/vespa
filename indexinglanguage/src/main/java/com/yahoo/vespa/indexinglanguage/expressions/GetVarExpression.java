// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;

/**
 * @author Simon Thoresen Hult
 */
public class GetVarExpression extends Expression {

    private final String varName;

    public GetVarExpression(String varName) {
        this.varName = varName;
    }

    public String getVariableName() {
        return varName;
    }

    @Override
    protected void doExecute(ExecutionContext ctx) {
        ctx.setValue(ctx.getVariable(varName));
    }

    @Override
    protected void doVerify(VerificationContext context) {
        DataType input = context.getVariable(varName);
        if (input == null) {
            throw new VerificationException(this, "Variable '" + varName + "' not found.");
        }
        context.setValue(input);
    }

    @Override
    public DataType requiredInputType() {
        return null;
    }

    @Override
    public DataType createdOutputType() {
        return UnresolvedDataType.INSTANCE;
    }

    @Override
    public String toString() {
        return "get_var " + varName;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof GetVarExpression)) {
            return false;
        }
        GetVarExpression rhs = (GetVarExpression)obj;
        if (!varName.equals(rhs.varName)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode() + varName.hashCode();
    }
}
