// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;
import com.yahoo.document.Field;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.NumericFieldValue;
import com.yahoo.vespa.indexinglanguage.ExpressionConverter;
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

        Comparator(String img) {
            this.img = img;
        }

        @Override
        public String toString() {
            return img;
        }

    }

    private final Expression left;
    private final Comparator comparator;
    private final Expression right;
    private final Expression ifTrue;
    private final Expression ifFalse;

    public IfThenExpression(Expression lhs, Comparator cmp, Expression right, Expression ifTrue) {
        this(lhs, cmp, right, ifTrue, null);
    }

    public IfThenExpression(Expression lhs, Comparator cmp, Expression right, Expression ifTrue, Expression ifFalse) {
        super(resolveInputType(lhs, right, ifTrue, ifFalse));
        this.left = lhs;
        this.comparator = cmp;
        this.right = right;
        this.ifTrue = ifTrue;
        this.ifFalse = ifFalse;
    }

    @Override
    public IfThenExpression convertChildren(ExpressionConverter converter) {
        return new IfThenExpression(converter.branch().convert(left),
                                    comparator,
                                    converter.branch().convert(right),
                                    converter.branch().convert(ifTrue),
                                    converter.branch().convert(ifFalse));
    }

    @Override
    public void setStatementOutput(DocumentType documentType, Field field) {
        left.setStatementOutput(documentType, field);
        right.setStatementOutput(documentType, field);
        ifTrue.setStatementOutput(documentType, field);
        ifFalse.setStatementOutput(documentType, field);
    }

    public Expression getLeftHandSide() { return left; }

    public Comparator getComparator() { return comparator; }

    public Expression getRightHandSide() { return right; }

    public Expression getIfTrueExpression() { return ifTrue; }

    public Expression getIfFalseExpression() { return ifFalse; }

    @Override
    protected void doExecute(ExecutionContext context) {
        FieldValue input = context.getValue();
        FieldValue leftValue = context.setValue(input).execute(left).getValue();
        if (leftValue == null) {
            context.setValue(null);
            return;
        }
        FieldValue rightValue = context.setValue(input).execute(right).getValue();
        if (rightValue == null) {
            context.setValue(null);
            return;
        }
        context.setValue(input);
        if (isTrue(leftValue, comparator, rightValue)) {
            ifTrue.execute(context);
        } else if (ifFalse != null) {
            ifFalse.execute(context);
        }
    }

    @Override
    protected void doVerify(VerificationContext context) {
        DataType input = context.getValueType();
        context.setValueType(input).execute(left);
        context.setValueType(input).execute(right);
        var trueValue = context.setValueType(input).execute(ifTrue);
        var falseValue = context.setValueType(input).execute(ifFalse);
        var valueType = trueValue.getValueType().isAssignableFrom(falseValue.getValueType()) ?
                        trueValue.getValueType() : falseValue.getValueType();
        context.setValueType(valueType);
    }

    @Override
    public void selectMembers(ObjectPredicate predicate, ObjectOperation operation) {
        select(left, predicate, operation);
        select(right, predicate, operation);
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
        DataType ifTrueType = ifTrue.createdOutputType();
        DataType ifFalseType = ifFalse == null ? null : ifFalse.createdOutputType();
        if (ifTrueType == null || ifFalseType == null) return null;
        if (ifTrueType.isAssignableFrom(ifFalseType))
            return ifTrueType;
        else
            return ifFalseType;
    }

    @Override
    public String toString() {
        StringBuilder ret = new StringBuilder();
        ret.append("if (").append(left).append(" ").append(comparator).append(" ").append(right).append(") ");
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
        if (!left.equals(exp.left)) {
            return false;
        }
        if (!comparator.equals(exp.comparator)) {
            return false;
        }
        if (!right.equals(exp.right)) {
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
        int ret = getClass().hashCode() + left.hashCode() + comparator.hashCode() + right.hashCode() + ifTrue.hashCode();
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
                                                                    prev.getName() + " vs " + next.getName());
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
