// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.rule;

import com.yahoo.searchlib.rankingexpression.evaluation.Value;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;

/**
 * A mathematical operator
 *
 * @author bratseth
 */
public enum Operator {

    // In order from lowest to highest precedence
    or("||", (x, y) -> x.or(y)),
    and("&&", (x, y) -> x.and(y)),
    largerOrEqual(">=", (x, y) -> x.largerOrEqual(y)),
    larger(">", (x, y) -> x.larger(y)),
    smallerOrEqual("<=", (x, y) -> x.smallerOrEqual(y)),
    smaller("<", (x, y) -> x.smaller(y)),
    approxEqual("~=", (x, y) -> x.approxEqual(y)),
    notEqual("!=", (x, y) -> x.notEqual(y)),
    equal("==", (x, y) -> x.equal(y)),
    plus("+", (x, y) -> x.add(y)),
    minus("-", (x, y) -> x.subtract(y)),
    multiply("*", (x, y) -> x.multiply(y)),
    divide("/", (x, y) -> x.divide(y)),
    modulo("%", (x, y) -> x.modulo(y)),
    power("^", true, (x, y) -> x.power(y));

    /** A list of all the operators in this in order of increasing precedence */
    public static final List<Operator> operatorsByPrecedence = Arrays.stream(Operator.values()).toList();

    private final String image;
    private final boolean rightPrecedence;
    private final BiFunction<Value, Value, Value> function;

    Operator(String image, BiFunction<Value, Value, Value> function) {
        this(image, false, function);
    }

    Operator(String image, boolean rightPrecedence, BiFunction<Value, Value, Value> function) {
        this.image = image;
        this.rightPrecedence = rightPrecedence;
        this.function = function;
    }

    /** Returns true if this operator has precedence over the given operator */
    public boolean hasPrecedenceOver(Operator other) {
        if (operatorsByPrecedence.indexOf(this) == operatorsByPrecedence.indexOf(other))
            return rightPrecedence;
        return operatorsByPrecedence.indexOf(this) > operatorsByPrecedence.indexOf(other);
    }

    public final Value evaluate(Value x, Value y) {
        return function.apply(x, y);
    }

    @Override
    public String toString() {
        return image;
    }

}
