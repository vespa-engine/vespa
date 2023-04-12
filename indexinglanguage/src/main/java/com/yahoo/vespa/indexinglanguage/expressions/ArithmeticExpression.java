// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.NumericDataType;
import com.yahoo.document.datatypes.*;
import com.yahoo.vespa.indexinglanguage.ExpressionConverter;
import com.yahoo.vespa.objects.ObjectOperation;
import com.yahoo.vespa.objects.ObjectPredicate;

import java.math.BigDecimal;
import java.math.MathContext;

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

    private final Expression lhs;
    private final Operator op;
    private final Expression rhs;

    public ArithmeticExpression(Expression lhs, Operator op, Expression rhs) {
        super(requiredInputType(lhs, rhs));
        lhs.getClass(); // throws NullPointerException
        op.getClass();
        rhs.getClass();
        this.lhs = lhs;
        this.op = op;
        this.rhs = rhs;
    }

    @Override
    public ArithmeticExpression convertChildren(ExpressionConverter converter) {
        // TODO: branch()?
        return new ArithmeticExpression(converter.convert(lhs), op, converter.convert(rhs));
    }

    public Expression getLeftHandSide() {
        return lhs;
    }

    public Operator getOperator() {
        return op;
    }

    public Expression getRightHandSide() {
        return rhs;
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        FieldValue input = context.getValue();
        context.setValue(evaluate(context.setValue(input).execute(lhs).getValue(),
                                  context.setValue(input).execute(rhs).getValue()));
    }

    @Override
    protected void doVerify(VerificationContext context) {
        DataType input = context.getValueType();
        context.setValueType(evaluate(context.setValueType(input).execute(lhs).getValueType(),
                                      context.setValueType(input).execute(rhs).getValueType()));
    }

    private static DataType requiredInputType(Expression lhs, Expression rhs) {
        DataType lhsType = lhs.requiredInputType();
        DataType rhsType = rhs.requiredInputType();
        if (lhsType == null) {
            return rhsType;
        }
        if (rhsType == null) {
            return lhsType;
        }
        if (!lhsType.equals(rhsType)) {
            throw new VerificationException(ArithmeticExpression.class, "Operands require conflicting input types, " +
                                                  lhsType.getName() + " vs " + rhsType.getName() + ".");
        }
        return lhsType;
    }

    @Override
    public DataType createdOutputType() {
        return UnresolvedDataType.INSTANCE;
    }

    @Override
    public String toString() {
        return lhs + " " + op + " " + rhs;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ArithmeticExpression)) {
            return false;
        }
        ArithmeticExpression exp = (ArithmeticExpression)obj;
        if (!lhs.equals(exp.lhs)) {
            return false;
        }
        if (!op.equals(exp.op)) {
            return false;
        }
        if (!rhs.equals(exp.rhs)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode() + lhs.hashCode() + op.hashCode() + rhs.hashCode();
    }

    private DataType evaluate(DataType lhs, DataType rhs) {
        if (lhs == null || rhs == null) {
            throw new VerificationException(this, "Attempting to perform arithmetic on a null value.");
        }
        if (!(lhs instanceof NumericDataType) ||
            !(rhs instanceof NumericDataType))
        {
            throw new VerificationException(this, "Attempting to perform unsupported arithmetic: [" +
                                                  lhs.getName() + "] " + op + " [" + rhs.getName() + "]");
        }
        if (lhs == DataType.FLOAT || lhs == DataType.DOUBLE ||
            rhs == DataType.FLOAT || rhs == DataType.DOUBLE)
        {
            if (lhs == DataType.DOUBLE || rhs == DataType.DOUBLE) {
                return DataType.DOUBLE;
            }
            return DataType.FLOAT;
        }
        if (lhs == DataType.LONG || rhs == DataType.LONG) {
            return DataType.LONG;
        }
        return DataType.INT;
    }

    private FieldValue evaluate(FieldValue lhs, FieldValue rhs) {
        if (lhs == null || rhs == null) {
            return null;
        }
        if (!(lhs instanceof NumericFieldValue) ||
            !(rhs instanceof NumericFieldValue))
        {
            throw new IllegalArgumentException("Unsupported operation: [" + lhs.getDataType().getName() + "] " +
                                               op + " [" + rhs.getDataType().getName() + "]");
        }
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
            rhs instanceof FloatFieldValue || rhs instanceof DoubleFieldValue)
        {
            if (lhs instanceof DoubleFieldValue || rhs instanceof DoubleFieldValue) {
                return new DoubleFieldValue(val.doubleValue());
            }
            return new FloatFieldValue(val.floatValue());
        }
        if (lhs instanceof LongFieldValue || rhs instanceof LongFieldValue) {
            return new LongFieldValue(val.longValue());
        }
        return new IntegerFieldValue(val.intValue());
    }

    public static BigDecimal asBigDecimal(NumericFieldValue value) {
        if (value instanceof ByteFieldValue) {
            return BigDecimal.valueOf(((ByteFieldValue)value).getByte());
        } else if (value instanceof DoubleFieldValue) {
            return BigDecimal.valueOf(((DoubleFieldValue)value).getDouble());
        } else if (value instanceof FloatFieldValue) {
            return BigDecimal.valueOf(((FloatFieldValue)value).getFloat());
        } else if (value instanceof IntegerFieldValue) {
            return BigDecimal.valueOf(((IntegerFieldValue)value).getInteger());
        } else if (value instanceof LongFieldValue) {
            return BigDecimal.valueOf(((LongFieldValue)value).getLong());
        }
        throw new IllegalArgumentException("Unsupported numeric field value type '" +
                                           value.getClass().getName() + "'.");
    }

    @Override
    public void selectMembers(ObjectPredicate predicate, ObjectOperation operation) {
        lhs.select(predicate, operation);
        rhs.select(predicate, operation);
    }
}
