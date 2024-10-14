// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;
import com.yahoo.document.Field;
import com.yahoo.vespa.indexinglanguage.ExpressionConverter;
import com.yahoo.vespa.indexinglanguage.ExpressionVisitor;
import com.yahoo.vespa.indexinglanguage.UpdateAdapter;
import com.yahoo.vespa.objects.ObjectOperation;
import com.yahoo.vespa.objects.ObjectPredicate;

/**
 * @author Simon Thoresen Hult
 */
public final class GuardExpression extends CompositeExpression {

    private final Expression expression;
    private final boolean shouldExecute;

    public GuardExpression(Expression expression) {
        super(expression.requiredInputType());
        this.expression = expression;
        shouldExecute = shouldExecute(expression);
    }

    public Expression getInnerExpression() { return expression; }

    @Override
    public GuardExpression convertChildren(ExpressionConverter converter) {
        return new GuardExpression(converter.convert(expression));
    }

    @Override
    public DataType setInputType(DataType inputType, VerificationContext context) {
        super.setInputType(inputType, context);
        return expression.setInputType(inputType, context);
    }

    @Override
    public DataType setOutputType(DataType outputType, VerificationContext context) {
        super.setOutputType(outputType, context);
        return expression.setOutputType(outputType, context);
    }

    @Override
    protected void doVerify(VerificationContext context) {
        expression.verify(context);
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        if (!shouldExecute && context.getFieldValue() instanceof UpdateAdapter) {
            context.setCurrentValue(null);
        } else {
            expression.execute(context);
        }
    }

    @Override
    public void setStatementOutput(DocumentType documentType, Field field) {
        expression.setStatementOutput(documentType, field);
    }

    @Override
    public DataType createdOutputType() {
        return expression.createdOutputType();
    }

    @Override
    public String toString() {
        return "guard " + toScriptBlock(expression);
    }

    @Override
    public void selectMembers(ObjectPredicate predicate, ObjectOperation operation) {
         select(expression, predicate, operation);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof GuardExpression rhs)) return false;
        if (!expression.equals(rhs.expression)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode() + expression.hashCode();
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
