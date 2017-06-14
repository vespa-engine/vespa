// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.rule;

import com.yahoo.searchlib.rankingexpression.evaluation.Value;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A mathematical operator
 *
 * @author bratseth
 */
public enum ArithmeticOperator {

    PLUS(0, "+") { public Value evaluate(Value x, Value y) {
        return x.add(y);
    }},
    MINUS(1, "-") { public Value evaluate(Value x, Value y) {
        return x.subtract(y);
    }},
    MULTIPLY(2, "*") { public Value evaluate(Value x, Value y) {
        return x.multiply(y);
    }},
    DIVIDE(3, "/") { public Value evaluate(Value x, Value y) {
        return x.divide(y);
    }};

    /** A list of all the operators in this in order of decreasing precedence */
    public static final List<ArithmeticOperator> operatorsByPrecedence = operatorsByPrecedence();

    private final int precedence;
    private final String image;

    private ArithmeticOperator(int precedence, String image) {
        this.precedence = precedence;
        this.image = image;
    }

    /** Returns true if this operator has precedence over the given operator */
    public boolean hasPrecedenceOver(ArithmeticOperator op) {
        return precedence > op.precedence;
    }

    public abstract Value evaluate(Value x, Value y);

    @Override
    public String toString() {
        return image;
    }

    private static List<ArithmeticOperator> operatorsByPrecedence() {
        List<ArithmeticOperator> operators = new ArrayList<>();
        operators.add(DIVIDE);
        operators.add(MULTIPLY);
        operators.add(MINUS);
        operators.add(PLUS);
        return Collections.unmodifiableList(operators);
    }

}
