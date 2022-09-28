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
    OR("||", (x, y) -> x.or(y)),
    AND("&&", (x, y) -> x.and(y)),
    GREATEREQUAL(">=", (x, y) -> x.greaterEqual(y)),
    GREATER(">", (x, y) -> x.greater(y)),
    LESSEQUAL("<=", (x, y) -> x.lessEqual(y)),
    LESS("<", (x, y) -> x.less(y)),
    APPROX("~=", (x, y) -> x.approx(y)),
    NOTEQUAL("!=", (x, y) -> x.notEqual(y)),
    EQUAL("==", (x, y) -> x.equal(y)),
    PLUS("+", (x, y) -> x.add(y)),
    MINUS("-", (x, y) -> x.subtract(y)),
    MULTIPLY("*", (x, y) -> x.multiply(y)),
    DIVIDE("/", (x, y) -> x.divide(y)),
    MODULO("%", (x, y) -> x.modulo(y)),
    POWER("^", true, (x, y) -> x.power(y));

    /** A list of all the operators in this in order of increasing precedence */
    public static final List<Operator> operatorsByPrecedence = Arrays.stream(Operator.values()).toList();

    private final String image;
    private final boolean bindsRight; // TODO: Implement
    private final BiFunction<Value, Value, Value> function;

    Operator(String image, BiFunction<Value, Value, Value> function) {
        this(image, false, function);
    }

    Operator(String image, boolean bindsRight, BiFunction<Value, Value, Value> function) {
        this.image = image;
        this.bindsRight = bindsRight;
        this.function = function;
    }

    /** Returns true if this operator has precedence over the given operator */
    public boolean hasPrecedenceOver(Operator op) {
        return operatorsByPrecedence.indexOf(this) > operatorsByPrecedence.indexOf(op);
    }

    public final Value evaluate(Value x, Value y) {
        return function.apply(x, y);
    }

    @Override
    public String toString() {
        return image;
    }

}
