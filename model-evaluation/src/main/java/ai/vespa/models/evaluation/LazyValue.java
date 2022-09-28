// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.models.evaluation;

import com.yahoo.searchlib.rankingexpression.evaluation.Context;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.searchlib.rankingexpression.rule.Function;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

/**
 * A Value which is computed from an expression when first requested.
 * This is not multithread safe.
 *
 * @author bratseth
 */
class LazyValue extends Value {

    /** The reference to the function computing the value of this */
    private final FunctionReference function;

    /** The context used to compute the function of this */
    private final Context context;

    /** The model this is part of */
    private final Model model;

    private Value computedValue = null;

    public LazyValue(FunctionReference function, Context context, Model model) {
        this.function = function;
        this.context = context;
        this.model = model;
    }

    private Value computedValue() {
        if (computedValue == null)
            computedValue = model.requireReferencedFunction(function).getBody().evaluate(context);
        return computedValue;
    }

    @Override
    public TensorType type() {
        return model.requireReferencedFunction(function).returnType().get();
    }

    @Override
    public double asDouble() {
        return computedValue().asDouble();
    }

    @Override
    public Tensor asTensor() {
        return computedValue().asTensor();
    }

    @Override
    public boolean hasDouble() {
        return type().rank() == 0;
    }

    @Override
    public boolean asBoolean() {
        return computedValue().asBoolean();
    }

    @Override
    public Value negate() {
        return computedValue().negate();
    }

    @Override
    public Value not() {
        return computedValue().not();
    }

    @Override
    public Value or(Value value) {
        return computedValue().or(value);
    }

    @Override
    public Value and(Value value) {
        return computedValue().and(value);
    }

    @Override
    public Value largerOrEqual(Value value) {
        return computedValue().largerOrEqual(value);
    }

    @Override
    public Value larger(Value value) {
        return computedValue().larger(value);
    }

    @Override
    public Value smallerOrEqual(Value value) {
        return computedValue().smallerOrEqual(value);
    }

    @Override
    public Value smaller(Value value) {
        return computedValue().smaller(value);
    }

    @Override
    public Value approxEqual(Value value) {
        return computedValue().approxEqual(value);
    }

    @Override
    public Value notEqual(Value value) {
        return computedValue().notEqual(value);
    }

    @Override
    public Value equal(Value value) {
        return computedValue().equal(value);
    }

    @Override
    public Value add(Value value) {
        return computedValue().add(value);
    }

    @Override
    public Value subtract(Value value) {
        return computedValue().subtract(value);
    }

    @Override
    public Value multiply(Value value) {
        return computedValue().multiply(value);
    }

    @Override
    public Value divide(Value value) {
        return computedValue().divide(value);
    }

    @Override
    public Value modulo(Value value) {
        return computedValue().modulo(value);
    }

    @Override
    public Value power(Value value) {
        return computedValue().power(value);
    }

    @Override
    public Value function(Function function, Value value) {
        return computedValue().function(function, value);
    }

    @Override
    public Value asMutable() {
        return computedValue().asMutable();
    }

    @Override
    public String toString() {
        return "value of " + function;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) return true;
        if (!(other instanceof Value)) return false;
        return computedValue().equals(other);
    }

    @Override
    public int hashCode() {
        return computedValue().hashCode();
    }

    LazyValue copyFor(Context context) {
        return new LazyValue(this.function, context, model);
    }

}
