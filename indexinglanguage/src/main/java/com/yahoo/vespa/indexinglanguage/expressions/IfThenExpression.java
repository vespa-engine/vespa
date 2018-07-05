// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.NumericFieldValue;
import com.yahoo.vespa.objects.ObjectOperation;
import com.yahoo.vespa.objects.ObjectPredicate;

import java.math.BigDecimal;

/**
 * @author Simon Thoresen Hult
 */
public class IfThenExpression extends CompositeExpression {

    public static enum Comparator {
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
        this.lhs = lhs;
        this.cmp = cmp;
        this.rhs = rhs;
        this.ifTrue = ifTrue;
        this.ifFalse = ifFalse;
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
    protected void doExecute(ExecutionContext ctx) {
        FieldValue input = ctx.getValue();
        FieldValue lhsVal = ctx.setValue(input).execute(lhs).getValue();
        if (lhsVal == null) {
            ctx.setValue(null);
            return;
        }
        FieldValue rhsVal = ctx.setValue(input).execute(rhs).getValue();
        if (rhsVal == null) {
            ctx.setValue(null);
            return;
        }
        ctx.setValue(input);
        if (isTrue(lhsVal, cmp, rhsVal)) {
            ifTrue.execute(ctx);
        } else if (ifFalse != null) {
            ifFalse.execute(ctx);
        }
    }

    @Override
    protected void doVerify(VerificationContext context) {
        DataType input = context.getValue();
        context.setValue(input).execute(lhs);
        context.setValue(input).execute(rhs);
        context.setValue(input).execute(ifTrue);
        context.setValue(input).execute(ifFalse);
        context.setValue(input);
    }

    @Override
    public void selectMembers(ObjectPredicate predicate, ObjectOperation operation) {
        select(lhs, predicate, operation);
        select(rhs, predicate, operation);
        select(ifTrue, predicate, operation);
        select(ifFalse, predicate, operation);
    }

    @Override
    public DataType requiredInputType() {
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
        if (!(obj instanceof IfThenExpression)) {
            return false;
        }
        IfThenExpression exp = (IfThenExpression)obj;
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

    private DataType resolveRequiredInputType(DataType prev, DataType next) {
        if (next == null) {
            return prev;
        }
        if (prev == null) {
            return next;
        }
        if (!prev.equals(next)) {
            throw new VerificationException(this, "Operands require conflicting input types, " +
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
        switch (cmp) {
        case EQ:
            return res == 0;
        case NE:
            return res != 0;
        case GT:
            return res > 0;
        case GE:
            return res >= 0;
        case LT:
            return res < 0;
        case LE:
            return res <= 0;
        default:
            throw new UnsupportedOperationException(cmp.toString());
        }
    }
}
