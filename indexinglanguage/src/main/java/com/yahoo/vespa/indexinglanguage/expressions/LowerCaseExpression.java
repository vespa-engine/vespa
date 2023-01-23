// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.StringFieldValue;

import static com.yahoo.language.LinguisticsCase.toLowerCase;

/**
 * @author Simon Thoresen Hult
 */
public final class LowerCaseExpression extends Expression {

    public LowerCaseExpression() {
        super(DataType.STRING);
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        context.setValue(new StringFieldValue(toLowerCase(String.valueOf(context.getValue()))));
    }

    @Override
    protected void doVerify(VerificationContext context) {
        context.setValueType(createdOutputType());
    }

    @Override
    public DataType createdOutputType() {
        return DataType.STRING;
    }

    @Override
    public String toString() {
        return "lowercase";
    }

    @Override
    public boolean equals(Object obj) {
        return (obj instanceof LowerCaseExpression);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}
