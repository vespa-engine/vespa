// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.evaluation;

import com.google.common.annotations.Beta;
import com.yahoo.searchlib.rankingexpression.rule.Function;
import com.yahoo.searchlib.rankingexpression.rule.TruthOperator;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;
import com.yahoo.tensor.TensorType;

/**
 * A Value containing a tensor.
 * See {@link com.yahoo.tensor.Tensor} for definition of a tensor
 * and the operations supported.
 *
 * @author bratseth
 */
@Beta
public class TensorValue extends Value {

    /** The tensor value of this */
    private final Tensor value;

    public TensorValue(Tensor value) {
        this.value = value;
    }

    @Override
    public TensorType type() { return value.type(); }

    @Override
    public double asDouble() {
        if (hasDouble())
            return value.get(TensorAddress.of());
        throw new UnsupportedOperationException("Requires a double value, but " + this.value + " cannot be " +
                                                "used as a double");
    }

    @Override
    public boolean hasDouble() { return value.type().dimensions().isEmpty() && ! value.isEmpty(); }

    @Override
    public boolean asBoolean() {
        throw new UnsupportedOperationException("A tensor does not have a boolean value");
    }

    @Override
    public Value negate() {
        return new TensorValue(value.map((value) -> -value));
    }

    @Override
    public Value add(Value argument) {
        if (argument instanceof TensorValue)
            return new TensorValue(value.add(((TensorValue)argument).value));
        else
            return new TensorValue(value.map((value) -> value + argument.asDouble()));
    }

    @Override
    public Value subtract(Value argument) {
        if (argument instanceof TensorValue)
            return new TensorValue(value.subtract(((TensorValue) argument).value));
        else
            return new TensorValue(value.map((value) -> value - argument.asDouble()));
    }

    @Override
    public Value multiply(Value argument) {
        if (argument instanceof TensorValue)
            return new TensorValue(value.multiply(((TensorValue) argument).value));
        else
            return new TensorValue(value.map((value) -> value * argument.asDouble()));
    }

    @Override
    public Value divide(Value argument) {
        if (argument instanceof TensorValue)
            return new TensorValue(value.divide(((TensorValue) argument).value));
        else
            return new TensorValue(value.map((value) -> value / argument.asDouble()));
    }

    @Override
    public Value modulo(Value argument) {
        if (argument instanceof TensorValue)
            return new TensorValue(value.fmod(((TensorValue) argument).value));
        else
            return new TensorValue(value.map((value) -> value % argument.asDouble()));
    }

    @Override
    public Value and(Value argument) {
        if (argument instanceof TensorValue)
            return new TensorValue(value.join(((TensorValue)argument).value, (a, b) -> ((a!=0.0) && (b!=0.0)) ? 1.0 : 0.0 ));
        else
            return new TensorValue(value.map((value) -> ((value!=0.0) && argument.asBoolean()) ? 1 : 0));
    }

    @Override
    public Value or(Value argument) {
        if (argument instanceof TensorValue)
            return new TensorValue(value.join(((TensorValue)argument).value, (a, b) -> ((a!=0.0) || (b!=0.0)) ? 1.0 : 0.0 ));
        else
            return new TensorValue(value.map((value) -> ((value!=0.0) || argument.asBoolean()) ? 1 : 0));
    }

    @Override
    public Value not() {
        return new TensorValue(value.map((value) -> (value==0.0) ? 1.0 : 0.0));
    }

    @Override
    public Value power(Value argument) {
        if (argument instanceof TensorValue)
            return new TensorValue(value.pow(((TensorValue)argument).value));
        else
            return new TensorValue(value.map((value) -> Math.pow(value, argument.asDouble())));
    }

    private Tensor asTensor(Value value, String operationName) {
        if ( ! (value instanceof TensorValue))
            throw new UnsupportedOperationException("Could not perform " + operationName +
                                                    ": The second argument must be a tensor but was " + value);
        return ((TensorValue)value).value;
    }

    public Tensor asTensor() { return value; }

    @Override
    public Value compare(TruthOperator operator, Value argument) {
        return new TensorValue(compareTensor(operator, asTensor(argument, operator.toString())));
    }

    private Tensor compareTensor(TruthOperator operator, Tensor argument) {
        switch (operator) {
            case LARGER: return value.larger(argument);
            case LARGEREQUAL: return value.largerOrEqual(argument);
            case SMALLER: return value.smaller(argument);
            case SMALLEREQUAL: return value.smallerOrEqual(argument);
            case EQUAL: return value.equal(argument);
            case NOTEQUAL: return value.notEqual(argument);
            case APPROX_EQUAL: return value.approxEqual(argument);
            default: throw new UnsupportedOperationException("Tensors cannot be compared with " + operator);
        }
    }

    @Override
    public Value function(Function function, Value arg) {
        if (arg instanceof TensorValue)
            return new TensorValue(functionOnTensor(function, asTensor(arg, function.toString())));
        else
            return new TensorValue(value.map((value) -> function.evaluate(value, arg.asDouble())));
    }

    private Tensor functionOnTensor(Function function, Tensor argument) {
        switch (function) {
            case min: return value.min(argument);
            case max: return value.max(argument);
            case atan2: return value.atan2(argument);
            case pow: return value.pow(argument);
            case fmod: return value.fmod(argument);
            case ldexp: return value.ldexp(argument);
            default: throw new UnsupportedOperationException("Cannot combine two tensors using " + function);
        }
    }

    @Override
    public Value asMutable() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
        return value.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TensorValue other = (TensorValue) o;
        return value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

}
