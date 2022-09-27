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
public enum ArithmeticOperator {

/*
struct Sub          : OperatorHelper<Sub>          { Sub()          : Helper("-", 101, LEFT)  {}};
struct Mul          : OperatorHelper<Mul>          { Mul()          : Helper("*", 102, LEFT)  {}};
struct Div          : OperatorHelper<Div>          { Div()          : Helper("/", 102, LEFT)  {}};
struct Mod          : OperatorHelper<Mod>          { Mod()          : Helper("%", 102, LEFT)  {}};
struct Pow          : OperatorHelper<Pow>          { Pow()          : Helper("^", 103, RIGHT) {}};
struct Equal        : OperatorHelper<Equal>        { Equal()        : Helper("==", 10, LEFT)  {}};
struct NotEqual     : OperatorHelper<NotEqual>     { NotEqual()     : Helper("!=", 10, LEFT)  {}};
struct Approx       : OperatorHelper<Approx>       { Approx()       : Helper("~=", 10, LEFT)  {}};
struct Less         : OperatorHelper<Less>         { Less()         : Helper("<",  10, LEFT)  {}};
struct LessEqual    : OperatorHelper<LessEqual>    { LessEqual()    : Helper("<=", 10, LEFT)  {}};
struct Greater      : OperatorHelper<Greater>      { Greater()      : Helper(">",  10, LEFT)  {}};
struct GreaterEqual : OperatorHelper<GreaterEqual> { GreaterEqual() : Helper(">=", 10, LEFT)  {}};
struct And          : OperatorHelper<And>          { And()          : Helper("&&",  2, LEFT)  {}};
struct Or           : OperatorHelper<Or>           { Or()           : Helper("||",  1, LEFT)  {}};
 */

    // In order from lowest to highest precedence
    OR("||", (x, y) -> x.or(y)),
    AND("&&", (x, y) -> x.and(y)),
    PLUS("+", (x, y) -> x.add(y)),
    MINUS("-", (x, y) -> x.subtract(y)),
    MULTIPLY("*", (x, y) -> x.multiply(y)),
    DIVIDE("/", (x, y) -> x.divide(y)),
    MODULO("%", (x, y) -> x.modulo(y)),
    POWER("^", true, (x, y) -> x.power(y));

    /** A list of all the operators in this in order of decreasing precedence */
    public static final List<ArithmeticOperator> operatorsByPrecedence = Arrays.stream(ArithmeticOperator.values()).toList();

    private final String image;
    private final boolean bindsRight; // TODO: Implement
    private final BiFunction<Value, Value, Value> function;

    ArithmeticOperator(String image, BiFunction<Value, Value, Value> function) {
        this(image, false, function);
    }

    ArithmeticOperator(String image, boolean bindsRight, BiFunction<Value, Value, Value> function) {
        this.image = image;
        this.bindsRight = bindsRight;
        this.function = function;
    }

    /** Returns true if this operator has precedence over the given operator */
    public boolean hasPrecedenceOver(ArithmeticOperator op) {
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
