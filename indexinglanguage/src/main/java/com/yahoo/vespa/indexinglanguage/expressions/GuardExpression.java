// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

    private final Expression exp;
    private final boolean shouldExecute;

    public GuardExpression(Expression exp) {
        super(exp.requiredInputType());
        this.exp = exp;
        shouldExecute = shouldExecute(exp);
    }

    public Expression getInnerExpression() {
        return exp;
    }

    @Override
    public GuardExpression convertChildren(ExpressionConverter converter) {
        return new GuardExpression(converter.convert(exp));
    }

    @Override
    public void setStatementOutput(DocumentType documentType, Field field) {
        exp.setStatementOutput(documentType, field);
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        if (!shouldExecute && context.getAdapter() instanceof UpdateAdapter) {
            context.setValue(null);
        } else {
            exp.execute(context);
        }
    }

    @Override
    protected void doVerify(VerificationContext context) {
        exp.verify(context);
    }

    @Override
    public DataType createdOutputType() {
        return exp.createdOutputType();
    }

    @Override
    public String toString() {
        return "guard " + toScriptBlock(exp);
    }

    @Override
    public void selectMembers(ObjectPredicate predicate, ObjectOperation operation) {
         select(exp, predicate, operation);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof GuardExpression rhs)) return false;
        if (!exp.equals(rhs.exp)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode() + exp.hashCode();
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
