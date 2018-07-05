// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;

/**
 * @author Simon Thoresen Hult
 */
public class SetVarExpression extends Expression {

    private final String varName;

    public SetVarExpression(String varName) {
        this.varName = varName;
    }

    public String getVariableName() {
        return varName;
    }

    @Override
    protected void doExecute(ExecutionContext ctx) {
        ctx.setVariable(varName, ctx.getValue());
    }

    @Override
    protected void doVerify(VerificationContext context) {
        DataType next = context.getValue();
        DataType prev = context.getVariable(varName);
        if (prev != null && !prev.equals(next)) {
            throw new VerificationException(this, "Attempting to assign conflicting types to variable '" + varName +
                                                  "', " + prev.getName() + " vs " + next.getName() + ".");
        }
        context.setVariable(varName, next);
    }

    @Override
    public DataType requiredInputType() {
        return UnresolvedDataType.INSTANCE;
    }

    @Override
    public DataType createdOutputType() {
        return null;
    }

    @Override
    public String toString() {
        return "set_var " + varName;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof SetVarExpression)) {
            return false;
        }
        SetVarExpression rhs = (SetVarExpression)obj;
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
