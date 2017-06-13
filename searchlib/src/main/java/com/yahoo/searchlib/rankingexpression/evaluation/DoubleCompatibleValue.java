// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.evaluation;

import com.yahoo.searchlib.rankingexpression.rule.Function;
import com.yahoo.searchlib.rankingexpression.rule.TruthOperator;

/**
 * A value which acts as a double in numerical context.
 *
 * @author bratseth
 * @since 5.1.21
 */
public abstract class DoubleCompatibleValue extends Value {

    @Override
    public boolean hasDouble() { return true; }

    @Override
    public Value negate() { return new DoubleValue(-asDouble()); }

    @Override
    public Value add(Value value) {
        return new DoubleValue(asDouble() + value.asDouble());
    }

    @Override
    public Value subtract(Value value) {
        return new DoubleValue(asDouble() - value.asDouble());
    }

    @Override
    public Value multiply(Value value) {
        return new DoubleValue(asDouble() * value.asDouble());
    }

    @Override
    public Value divide(Value value) {
        return new DoubleValue(asDouble() / value.asDouble());
    }

    @Override
    public Value compare(TruthOperator operator, Value value) {
        return new BooleanValue(operator.evaluate(asDouble(), value.asDouble()));
    }

    @Override
    public Value function(Function function, Value value) {
        return new DoubleValue(function.evaluate(asDouble(),value.asDouble()));
    }

}
