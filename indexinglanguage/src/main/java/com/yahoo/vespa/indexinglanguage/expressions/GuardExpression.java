// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;
import com.yahoo.document.Field;
import com.yahoo.vespa.indexinglanguage.ExpressionConverter;
import com.yahoo.vespa.indexinglanguage.ExpressionVisitor;
import com.yahoo.vespa.indexinglanguage.UpdateFieldValues;
import com.yahoo.vespa.objects.ObjectOperation;
import com.yahoo.vespa.objects.ObjectPredicate;

/**
 * @author Simon Thoresen Hult
 */
public final class GuardExpression extends CompositeExpression {

    private final Expression innerExpression;
    private final boolean shouldExecute;

    public GuardExpression(Expression innerExpression) {
        this.innerExpression = innerExpression;
        shouldExecute = shouldExecute(innerExpression);
    }

    @Override
    public boolean isMutating() { return innerExpression.isMutating(); }

    @Override
    public boolean requiresInput() { return innerExpression.requiresInput(); }

    public Expression getInnerExpression() { return innerExpression; }

    @Override
    public GuardExpression convertChildren(ExpressionConverter converter) {
        return new GuardExpression(converter.convert(innerExpression));
    }

    @Override
    public DataType setInputType(DataType inputType, TypeContext context) {
        super.setInputType(inputType, context);
        return innerExpression.setInputType(inputType, context);
    }

    @Override
    public DataType setOutputType(DataType outputType, TypeContext context) {
        super.setOutputType(outputType, context);
        return innerExpression.setOutputType(outputType, context);
    }

    @Override
    protected void doResolve(TypeContext context) {
        innerExpression.resolve(context);
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        if (!shouldExecute && context.getFieldValues() instanceof UpdateFieldValues) {
            context.setCurrentValue(null);
        } else {
            innerExpression.execute(context);
        }
    }

    @Override
    public void setStatementOutput(DocumentType documentType, Field field) {
        innerExpression.setStatementOutput(documentType, field);
    }

    @Override
    public String toString() {
        return "guard " + toScriptBlock(innerExpression);
    }

    @Override
    public void selectMembers(ObjectPredicate predicate, ObjectOperation operation) {
         select(innerExpression, predicate, operation);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof GuardExpression rhs)) return false;
        if (!innerExpression.equals(rhs.innerExpression)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode() + innerExpression.hashCode();
    }

    private static boolean shouldExecute(Expression exp) {
        ExecutionGuard guard = new ExecutionGuard();
        guard.visit(exp);
        return guard.shouldExecute;
    }

    private static class ExecutionGuard extends ExpressionVisitor {

        boolean shouldExecute = false;

        @Override
        protected void doVisit(Expression exp) {
            if (exp instanceof InputExpression || exp instanceof SetLanguageExpression) {
                shouldExecute = true;
            }
        }
    }

}
