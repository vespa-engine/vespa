// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.update;

import com.yahoo.document.DataType;
import com.yahoo.document.NumericDataType;
import com.yahoo.document.datatypes.DoubleFieldValue;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.NumericFieldValue;
import com.yahoo.document.serialization.DocumentUpdateWriter;

/**
 * <p>Value update representing an arithmetic operation on a numeric data type.</p>
 *
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
public class ArithmeticValueUpdate extends ValueUpdate<DoubleFieldValue> {
    protected Operator operator;
    protected DoubleFieldValue operand;

    public ArithmeticValueUpdate(Operator operator, DoubleFieldValue operand) {
        super(ValueUpdateClassID.ARITHMETIC);
        this.operator = operator;
        this.operand = operand;
    }

    public ArithmeticValueUpdate(Operator operator, Number operand) {
        this(operator, new DoubleFieldValue(operand.doubleValue()));
    }

    /**
     * Returns the operator of this arithmatic value update.
     *
     * @return the operator
     * @see com.yahoo.document.update.ArithmeticValueUpdate.Operator
     */
    public Operator getOperator() {
        return operator;
    }

    /**
     * Returns the operand of this arithmetic value update.
     *
     * @return the operand
     */
    public Number getOperand() {
        return operand.getDouble();
    }

    /** Returns the operand */
    public DoubleFieldValue getValue() { return operand; }

    /** Sets the operand */
    public void setValue(DoubleFieldValue value) { operand=value; }

    @Override
    public FieldValue applyTo(FieldValue oldValue) {
        if (oldValue instanceof NumericFieldValue) {
            Number number = (Number) oldValue.getWrappedValue();
            oldValue.assign(calculate(number));
        } else {
            throw new IllegalStateException("Cannot use arithmetic value update on non-numeric datatype "+oldValue.getClass().getName());
        }
        return oldValue;
    }

    @Override
    protected void checkCompatibility(DataType fieldType) {
        if (!(fieldType instanceof NumericDataType)) {
            throw new UnsupportedOperationException("Expected numeric type, got " + fieldType.getName() + ".");
        }
    }

    private double calculate(Number operand2) {
        switch (operator) {
            case ADD:
                return operand2.doubleValue() + operand.getDouble();
            case DIV:
                return operand2.doubleValue() / operand.getDouble();
            case MUL:
                return operand2.doubleValue() * operand.getDouble();
            case SUB:
                return operand2.doubleValue() - operand.getDouble();
        }
        return 0d;
    }

    @Override
    public void serialize(DocumentUpdateWriter data, DataType superType) {
        data.write(this);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ArithmeticValueUpdate && super.equals(o) &&
                operator == ((ArithmeticValueUpdate) o).operator && operand.equals(((ArithmeticValueUpdate) o).operand);
    }

    @Override
    public int hashCode() {
        return super.hashCode() + operator.hashCode() + operand.hashCode();
    }

    @Override
    public String toString() {
        return super.toString() + " " + operator.name + " " + operand;
    }

    /**
     * Lists valid operations that can be performed by an ArithmeticValueUpdate.
     */
    public enum Operator {
        /**
         * Add the operand to the value.
         */
        ADD(0, "add"),
        /**
         * Divide the value by the operand.
         */
        DIV(1, "divide"),
        /**
         * Multiply the value by the operand.
         */
        MUL(2, "multiply"),
        /**
         * Subtract the operand from the value.
         */
        SUB(3, "subtract");

        /**
         * The numeric ID of the operator, used for serialization.
         */
        public final int id;
        /**
         * The name of the operator, mainly used in toString() methods.
         */
        public final String name;

        Operator(int id, String name) {
            this.id = id;
            this.name = name;
        }

        /**
         * Returns the operator with the specified ID.
         *
         * @param id the ID to search for
         * @return the Operator with the specified ID, or null if it does not exist.
         */
        public static Operator getID(int id) {
            for (Operator operator : Operator.values()) {
                if (operator.id == id) {
                    return operator;
                }
            }
            return null;
        }
    }
}

