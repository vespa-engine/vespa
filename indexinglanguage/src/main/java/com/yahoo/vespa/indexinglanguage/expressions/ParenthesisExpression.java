// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;
import com.yahoo.document.Field;
import com.yahoo.vespa.indexinglanguage.ExpressionConverter;
import com.yahoo.vespa.objects.ObjectOperation;
import com.yahoo.vespa.objects.ObjectPredicate;

/**
 * @author Simon Thoresen Hult
 */
public class ParenthesisExpression extends CompositeExpression {

    private final Expression innerExpression;

    public ParenthesisExpression(Expression innerExpression) {
        this.innerExpression = innerExpression;
    }

    @Override
    public boolean requiresInput() { return innerExpression.requiresInput(); }

    public Expression getInnerExpression() { return innerExpression; }

    @Override
    public ParenthesisExpression convertChildren(ExpressionConverter converter) {
        return new ParenthesisExpression(converter.convert(innerExpression));
    }

    @Override
    public DataType setInputType(DataType inputType, VerificationContext context) {
        super.setInputType(inputType, context);
        return innerExpression.setInputType(inputType, context);
    }

    @Override
    public DataType setOutputType(DataType outputType, VerificationContext context) {
        super.setOutputType(outputType, context);
        return innerExpression.setOutputType(outputType, context);
    }

    @Override
    public void setStatementOutput(DocumentType documentType, Field field) {
        innerExpression.setStatementOutput(documentType, field);
    }

    @Override
    protected void doVerify(VerificationContext context) {
        innerExpression.verify(context);
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        innerExpression.execute(context);
    }

    @Override
    public DataType createdOutputType() {
        return innerExpression.createdOutputType();
    }

    @Override
    public String toString() {
        return "(" + innerExpression + ")";
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ParenthesisExpression rhs)) return false;
        if (!innerExpression.equals(rhs.innerExpression)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode() + innerExpression.hashCode();
    }

    @Override
    public void selectMembers(ObjectPredicate predicate, ObjectOperation operation) {
        select(innerExpression, predicate, operation);
    }

}
