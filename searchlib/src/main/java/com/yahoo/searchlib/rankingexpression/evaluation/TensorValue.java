// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.evaluation;

import com.yahoo.api.annotations.Beta;
import com.yahoo.searchlib.rankingexpression.rule.Function;
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
        if (hasDouble())
            return asDouble() != 0.0;
        throw new UnsupportedOperationException("Tensor does not have a value that can be converted to a boolean");
    }

    @Override
    public Value negate() {
        return new TensorValue(value.map((value) -> -value));
    }

    @Override
    public Value not() {
        return new TensorValue(value.map((value) -> (value==0.0) ? 1.0 : 0.0));
    }

    @Override
    public Value or(Value argument) {
        return new TensorValue(value.join(argument.asTensor(), (a, b) -> ((a!=0.0) || (b!=0.0)) ? 1.0 : 0.0 ));
    }

    @Override
    public Value and(Value argument) {
        return new TensorValue(value.join(argument.asTensor(), (a, b) -> ((a!=0.0) && (b!=0.0)) ? 1.0 : 0.0 ));
    }

    @Override
    public Value largerOrEqual(Value argument) {
        return new TensorValue(value.largerOrEqual(argument.asTensor()));
    }

    @Override
    public Value larger(Value argument) {
        return new TensorValue(value.larger(argument.asTensor()));
    }

    @Override
    public Value smallerOrEqual(Value argument) {
        return new TensorValue(value.smallerOrEqual(argument.asTensor()));
    }

    @Override
    public Value smaller(Value argument) {
        return new TensorValue(value.smaller(argument.asTensor()));
    }

    @Override
    public Value approxEqual(Value argument) {
        return new TensorValue(value.approxEqual(argument.asTensor()));
    }

    @Override
    public Value notEqual(Value argument) {
        return new TensorValue(value.notEqual(argument.asTensor()));
    }

    @Override
    public Value equal(Value argument) {
        return new TensorValue(value.equal(argument.asTensor()));
    }

    @Override
    public Value add(Value argument) {
        return new TensorValue(value.add(argument.asTensor()));
    }

    @Override
    public Value subtract(Value argument) {
        return new TensorValue(value.subtract(argument.asTensor()));
    }

    @Override
    public Value multiply(Value argument) {
        return new TensorValue(value.multiply(argument.asTensor()));
    }

    @Override
    public Value divide(Value argument) {
        return new TensorValue(value.divide(argument.asTensor()));
    }

    @Override
    public Value modulo(Value argument) {
        return new TensorValue(value.fmod(argument.asTensor()));
    }

    @Override
    public Value power(Value argument) {
        return new TensorValue(value.pow(argument.asTensor()));
    }

    public Tensor asTensor() { return value; }

    @Override
    public Value function(Function function, Value arg) {
        if (function.arity() != 1)
            return new TensorValue(functionOnTensor(function, arg.asTensor()));
        else
            return new TensorValue(value.map((value) -> function.evaluate(value, 0.0)));
    }

    private Tensor functionOnTensor(Function function, Tensor argument) {
        return switch (function) {
            case min -> value.min(argument);
            case max -> value.max(argument);
            case atan2 -> value.atan2(argument);
            case pow -> value.pow(argument);
            case fmod -> value.fmod(argument);
            case ldexp -> value.ldexp(argument);
            case bit -> value.bit(argument);
            case hamming -> value.hamming(argument);
            default -> value.join(argument, (a, b) -> function.evaluate(a, b));
        };
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
