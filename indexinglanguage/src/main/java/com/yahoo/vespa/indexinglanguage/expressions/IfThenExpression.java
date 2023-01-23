// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;
import com.yahoo.document.Field;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.NumericFieldValue;
import com.yahoo.vespa.objects.ObjectOperation;
import com.yahoo.vespa.objects.ObjectPredicate;

import java.math.BigDecimal;

/**
 * @author Simon Thoresen Hult
 */
public final class IfThenExpression extends CompositeExpression {

    public enum Comparator {
        EQ("=="),
        NE("!="),
        LT("<"),
        LE("<="),
        GT(">"),
        GE(">=");

        private final String img;

        private Comparator(String img) {
            this.img = img;
        }

        @Override
        public String toString() {
            return img;
        }

    }

    private final Expression lhs;
    private final Comparator cmp;
    private final Expression rhs;
    private final Expression ifTrue;
    private final Expression ifFalse;

    public IfThenExpression(Expression lhs, Comparator cmp, Expression rhs, Expression ifTrue) {
        this(lhs, cmp, rhs, ifTrue, null);
    }

    public IfThenExpression(Expression lhs, Comparator cmp, Expression rhs, Expression ifTrue, Expression ifFalse) {
        super(resolveInputType(lhs, rhs, ifTrue, ifFalse));
        this.lhs = lhs;
        this.cmp = cmp;
        this.rhs = rhs;
        this.ifTrue = ifTrue;
        this.ifFalse = ifFalse;
    }

    @Override
    public void setStatementOutput(DocumentType documentType, Field field) {
        lhs.setStatementOutput(documentType, field);
        rhs.setStatementOutput(documentType, field);
        ifTrue.setStatementOutput(documentType, field);
        ifFalse.setStatementOutput(documentType, field);
    }

    public Expression getLeftHandSide() {
        return lhs;
    }

    public Comparator getComparator() {
        return cmp;
    }

    public Expression getRightHandSide() {
        return rhs;
    }

    public Expression getIfTrueExpression() {
        return ifTrue;
    }

    public Expression getIfFalseExpression() {
        return ifFalse;
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        FieldValue input = context.getValue();
        FieldValue lhsVal = context.setValue(input).execute(lhs).getValue();
        if (lhsVal == null) {
            context.setValue(null);
            return;
        }
        FieldValue rhsVal = context.setValue(input).execute(rhs).getValue();
        if (rhsVal == null) {
            context.setValue(null);
            return;
        }
        context.setValue(input);
        if (isTrue(lhsVal, cmp, rhsVal)) {
            ifTrue.execute(context);
        } else if (ifFalse != null) {
            ifFalse.execute(context);
        }
    }

    @Override
    protected void doVerify(VerificationContext context) {
        DataType input = context.getValueType();
        context.setValueType(input).execute(lhs);
        context.setValueType(input).execute(rhs);
        context.setValueType(input).execute(ifTrue);
        context.setValueType(input).execute(ifFalse);
        context.setValueType(input);
    }

    @Override
    public void selectMembers(ObjectPredicate predicate, ObjectOperation operation) {
        select(lhs, predicate, operation);
        select(rhs, predicate, operation);
        select(ifTrue, predicate, operation);
        select(ifFalse, predicate, operation);
    }

    private static DataType resolveInputType(Expression lhs, Expression rhs, Expression ifTrue, Expression ifFalse) {
        DataType input = null;
        input = resolveRequiredInputType(input, lhs.requiredInputType());
        input = resolveRequiredInputType(input, rhs.requiredInputType());
        input = resolveRequiredInputType(input, ifTrue.requiredInputType());
        if (ifFalse != null) {
            input = resolveRequiredInputType(input, ifFalse.requiredInputType());
        }
        return input;
    }

    @Override
    public DataType createdOutputType() {
        return null;
    }

    @Override
    public String toString() {
        StringBuilder ret = new StringBuilder();
        ret.append("if (").append(lhs).append(" ").append(cmp).append(" ").append(rhs).append(") ");
        ret.append(toScriptBlock(ifTrue));
        if (ifFalse != null) {
            ret.append(" else ").append(toScriptBlock(ifFalse));
        }
        return ret.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof IfThenExpression exp)) {
            return false;
        }
        if (!lhs.equals(exp.lhs)) {
            return false;
        }
        if (!cmp.equals(exp.cmp)) {
            return false;
        }
        if (!rhs.equals(exp.rhs)) {
            return false;
        }
        if (!ifTrue.equals(exp.ifTrue)) {
            return false;
        }
        if (!equals(ifFalse, exp.ifFalse)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int ret = getClass().hashCode() + lhs.hashCode() + cmp.hashCode() + rhs.hashCode() + ifTrue.hashCode();
        if (ifFalse != null) {
            ret += ifFalse.hashCode();
        }
        return ret;
    }

    private static DataType resolveRequiredInputType(DataType prev, DataType next) {
        if (next == null) {
            return prev;
        }
        if (prev == null) {
            return next;
        }
        if (!prev.equals(next)) {
            throw new VerificationException(IfThenExpression.class, "Operands require conflicting input types, " +
                                                                    prev.getName() + " vs " + next.getName() + ".");
        }
        return prev;
    }

    private static boolean isTrue(FieldValue lhs, Comparator cmp, FieldValue rhs) {
        int res;
        if (lhs instanceof NumericFieldValue && rhs instanceof NumericFieldValue) {
            BigDecimal lhsVal = ArithmeticExpression.asBigDecimal((NumericFieldValue)lhs);
            BigDecimal rhsVal = ArithmeticExpression.asBigDecimal((NumericFieldValue)rhs);
            res = lhsVal.compareTo(rhsVal);
        } else {
            res = lhs.compareTo(rhs);
        }
        return switch (cmp) {
            case EQ -> res == 0;
            case NE -> res != 0;
            case GT -> res > 0;
            case GE -> res >= 0;
            case LT -> res < 0;
            case LE -> res <= 0;
        };
    }

}
