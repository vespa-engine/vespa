// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.rule;

import java.io.Serializable;

import static java.lang.Math.*;

/**
 * A scalar function
 *
 * @author bratseth
 */
public enum Function implements Serializable {

    cosh      { public double evaluate(double x, double y) { return cosh(x); } },
    sinh      { public double evaluate(double x, double y) { return sinh(x); } },
    tanh      { public double evaluate(double x, double y) { return tanh(x); } },
    cos       { public double evaluate(double x, double y) { return cos(x); } },
    sin       { public double evaluate(double x, double y) { return sin(x); } },
    tan       { public double evaluate(double x, double y) { return tan(x); } },
    acos      { public double evaluate(double x, double y) { return acos(x); } },
    asin      { public double evaluate(double x, double y) { return asin(x); } },
    atan      { public double evaluate(double x, double y) { return atan(x); } },
    exp       { public double evaluate(double x, double y) { return exp(x); } },
    log10     { public double evaluate(double x, double y) { return log10(x); } },
    log       { public double evaluate(double x, double y) { return log(x); } },
    sqrt      { public double evaluate(double x, double y) { return sqrt(x); } },
    ceil      { public double evaluate(double x, double y) { return ceil(x); } },
    fabs      { public double evaluate(double x, double y) { return abs(x); } },
    floor     { public double evaluate(double x, double y) { return floor(x); } },
    isNan     { public double evaluate(double x, double y) { return Double.isNaN(x) ? 1.0 : 0.0; } },
    relu      { public double evaluate(double x, double y) { return max(x,0); } },
    sigmoid   { public double evaluate(double x, double y) { return 1.0 / (1.0 + exp(-1.0 * x)); } },
    atan2(2)  { public double evaluate(double x, double y) { return atan2(x,y); } },
    pow(2)    { public double evaluate(double x, double y) { return pow(x,y); } },
    ldexp(2)  { public double evaluate(double x, double y) { return x*pow(2,y); } },
    fmod(2)   { public double evaluate(double x, double y) { return IEEEremainder(x,y); } },
    min(2)    { public double evaluate(double x, double y) { return min(x,y); } },
    max(2)    { public double evaluate(double x, double y) { return max(x,y); } };

    private final int arity;

    Function() {
        this(1);
    }

    Function(int arity) {
        this.arity = arity;
    }

    /** Perform the function on the input */
    public abstract double evaluate(double x, double y);

    /** Returns the number of arguments this function takes */
    public int arity() { return arity; }

}
