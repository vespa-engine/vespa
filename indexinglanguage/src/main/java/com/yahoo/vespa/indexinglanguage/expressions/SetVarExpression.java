// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;

/**
 * @author Simon Thoresen Hult
 */
public final class SetVarExpression extends Expression {

    private final String varName;

    public SetVarExpression(String varName) {
        super(UnresolvedDataType.INSTANCE);
        this.varName = varName;
    }

    public String getVariableName() {
        return varName;
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        context.setVariable(varName, context.getValue());
    }

    @Override
    protected void doVerify(VerificationContext context) {
        DataType next = context.getValueType();
        DataType prev = context.getVariable(varName);
        if (prev != null && !prev.equals(next)) {
            throw new VerificationException(this, "Attempting to assign conflicting types to variable '" + varName +
                                                  "', " + prev.getName() + " vs " + next.getName() + ".");
        }
        context.setVariable(varName, next);
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
        if (!(obj instanceof SetVarExpression rhs)) return false;
        if (!varName.equals(rhs.varName)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode() + varName.hashCode();
    }

}
