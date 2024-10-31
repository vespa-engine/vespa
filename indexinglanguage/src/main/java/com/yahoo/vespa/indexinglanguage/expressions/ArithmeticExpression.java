// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.NumericDataType;
import com.yahoo.document.datatypes.*;
import com.yahoo.vespa.indexinglanguage.ExpressionConverter;
import com.yahoo.vespa.objects.ObjectOperation;
import com.yahoo.vespa.objects.ObjectPredicate;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Objects;

/**
 * @author Simon Thoresen Hult
 */
public final class ArithmeticExpression extends CompositeExpression {

    public enum Operator {

        ADD(1, "+"),
        SUB(1, "-"),
        MUL(0, "*"),
        DIV(0, "/"),
        MOD(0, "%");

        private final int precedence;
        private final String img;

        Operator(int precedence, String img) {
            this.precedence = precedence;
            this.img = img;
        }

        public boolean precedes(Operator op) {
            return precedence <= op.precedence;
        }

        @Override
        public String toString() {
            return img;
        }
    }

    private final Expression left;
    private final Operator op;
    private final Expression right;

    public ArithmeticExpression(Expression left, Operator op, Expression right) {
        super(requiredInputType(left, right));
        this.left = Objects.requireNonNull(left);
        this.op = Objects.requireNonNull(op);
        this.right = Objects.requireNonNull(right);
    }

    public Expression getLeftHandSide() { return left; }

    public Operator getOperator() { return op; }

    public Expression getRightHandSide() { return right; }

    @Override
    public ArithmeticExpression convertChildren(ExpressionConverter converter) {
        // TODO: branch()?
        return new ArithmeticExpression(converter.convert(left), op, converter.convert(right));
    }

    @Override
    public DataType setInputType(DataType inputType, VerificationContext context) {
        super.setInputType(inputType, context);
        DataType leftOutput = left.setInputType(inputType, context);
        DataType rightOutput = right.setInputType(inputType, context);
        return resultingType(leftOutput, rightOutput);
    }

    @Override
    public DataType setOutputType(DataType outputType, VerificationContext context) {
        super.setOutputType(outputType, context);
        DataType leftInput = left.setOutputType(outputType, context);
        DataType rightInput = right.setOutputType(outputType, context);
        return null; // TODO: Reverse-map when possible
    }

    @Override
    protected void doVerify(VerificationContext context) {
        DataType input = context.getCurrentType();
        context.setCurrentType(resultingType(context.setCurrentType(input).verify(left).getCurrentType(),
                                             context.setCurrentType(input).verify(right).getCurrentType()));
    }

    private DataType resultingType(DataType left, DataType right) {
        if (left == null || right == null)
            return null;
        if (!(left instanceof NumericDataType))
            throw new VerificationException(this, "The first argument must be a number, but has type " + left.getName());
        if (!(right instanceof NumericDataType))
            throw new VerificationException(this, "The second argument must be a number, but has type " + right.getName());

        if (left == DataType.FLOAT || left == DataType.DOUBLE || right == DataType.FLOAT || right == DataType.DOUBLE) {
            if (left == DataType.DOUBLE || right == DataType.DOUBLE)
                return DataType.DOUBLE;
            return DataType.FLOAT;
        }
        if (left == DataType.LONG || right == DataType.LONG)
            return DataType.LONG;
        return DataType.INT;
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        FieldValue input = context.getCurrentValue();
        context.setCurrentValue(evaluate(context.setCurrentValue(input).execute(left).getCurrentValue(),
                                         context.setCurrentValue(input).execute(right).getCurrentValue()));
    }

    private static DataType requiredInputType(Expression left, Expression right) {
        DataType leftType = left.requiredInputType();
        DataType rightType = right.requiredInputType();
        if (leftType == null) return rightType;
        if (rightType == null) return leftType;
        if (!leftType.equals(rightType))
            throw new VerificationException(ArithmeticExpression.class, "Operands require conflicting input types, " +
                                                                        leftType.getName() + " vs " + rightType.getName());
        return leftType;
    }

    @Override
    public DataType createdOutputType() {
        return UnresolvedDataType.INSTANCE;
    }

    @Override
    public String toString() {
        return left + " " + op + " " + right;
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof ArithmeticExpression expression)) return false;

        if (!left.equals(expression.left)) return false;
        if (!op.equals(expression.op)) return false;
        if (!right.equals(expression.right)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode() + left.hashCode() + op.hashCode() + right.hashCode();
    }

    private FieldValue evaluate(FieldValue lhs, FieldValue rhs) {
        if (lhs == null || rhs == null) return null;
        if (!(lhs instanceof NumericFieldValue) || !(rhs instanceof NumericFieldValue))
            throw new IllegalArgumentException("Unsupported operation: [" + lhs.getDataType().getName() + "] " +
                                               op + " [" + rhs.getDataType().getName() + "]");

        BigDecimal lhsVal = asBigDecimal((NumericFieldValue)lhs);
        BigDecimal rhsVal = asBigDecimal((NumericFieldValue)rhs);
        return switch (op) {
            case ADD -> createFieldValue(lhs, rhs, lhsVal.add(rhsVal));
            case SUB -> createFieldValue(lhs, rhs, lhsVal.subtract(rhsVal));
            case MUL -> createFieldValue(lhs, rhs, lhsVal.multiply(rhsVal));
            case DIV -> createFieldValue(lhs, rhs, lhsVal.divide(rhsVal, MathContext.DECIMAL64));
            case MOD -> createFieldValue(lhs, rhs, lhsVal.remainder(rhsVal));
        };
    }

    private FieldValue createFieldValue(FieldValue lhs, FieldValue rhs, BigDecimal val) {
        if (lhs instanceof FloatFieldValue || lhs instanceof DoubleFieldValue ||
            rhs instanceof FloatFieldValue || rhs instanceof DoubleFieldValue) {
            if (lhs instanceof DoubleFieldValue || rhs instanceof DoubleFieldValue)
                return new DoubleFieldValue(val.doubleValue());
            return new FloatFieldValue(val.floatValue());
        }
        if (lhs instanceof LongFieldValue || rhs instanceof LongFieldValue)
            return new LongFieldValue(val.longValue());
        return new IntegerFieldValue(val.intValue());
    }

    public static BigDecimal asBigDecimal(NumericFieldValue value) {
        if (value instanceof ByteFieldValue)
            return BigDecimal.valueOf(((ByteFieldValue)value).getByte());
        else if (value instanceof DoubleFieldValue)
            return BigDecimal.valueOf(((DoubleFieldValue)value).getDouble());
        else if (value instanceof FloatFieldValue)
            return BigDecimal.valueOf(((FloatFieldValue)value).getFloat());
        else if (value instanceof IntegerFieldValue)
            return BigDecimal.valueOf(((IntegerFieldValue)value).getInteger());
        else if (value instanceof LongFieldValue)
            return BigDecimal.valueOf(((LongFieldValue)value).getLong());
        throw new IllegalArgumentException("Unsupported numeric field value type '" +
                                           value.getClass().getName() + "'");
    }

    @Override
    public void selectMembers(ObjectPredicate predicate, ObjectOperation operation) {
        left.select(predicate, operation);
        right.select(predicate, operation);
    }

}
