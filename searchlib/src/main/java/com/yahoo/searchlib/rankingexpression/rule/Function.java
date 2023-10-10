// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.rule;

import com.yahoo.tensor.functions.ScalarFunctions;

import java.io.Serializable;

import static java.lang.Math.*;

/**
 * A scalar function
 *
 * @author bratseth
 */
public enum Function implements Serializable {

    abs       { public double evaluate(double x, double y) { return abs(x); } },
    acos      { public double evaluate(double x, double y) { return acos(x); } },
    asin      { public double evaluate(double x, double y) { return asin(x); } },
    atan      { public double evaluate(double x, double y) { return atan(x); } },
    ceil      { public double evaluate(double x, double y) { return ceil(x); } },
    cos       { public double evaluate(double x, double y) { return cos(x); } },
    cosh      { public double evaluate(double x, double y) { return cosh(x); } },
    elu       { public double evaluate(double x, double y) { return x<0 ? exp(x)-1 : x; } },
    exp       { public double evaluate(double x, double y) { return exp(x); } },
    fabs      { public double evaluate(double x, double y) { return abs(x); } },
    floor     { public double evaluate(double x, double y) { return floor(x); } },
    isNan     { public double evaluate(double x, double y) { return Double.isNaN(x) ? 1.0 : 0.0; } },
    log       { public double evaluate(double x, double y) { return log(x); } },
    log10     { public double evaluate(double x, double y) { return log10(x); } },
    relu      { public double evaluate(double x, double y) { return max(x,0); } },
    round     { public double evaluate(double x, double y) { return round(x); } },
    sigmoid   { public double evaluate(double x, double y) { return 1.0 / (1.0 + exp(-1.0 * x)); } },
    sign      { public double evaluate(double x, double y) { return x >= 0 ? 1 : -1; } },
    sin       { public double evaluate(double x, double y) { return sin(x); } },
    sinh      { public double evaluate(double x, double y) { return sinh(x); } },
    square    { public double evaluate(double x, double y) { return x*x; } },
    sqrt      { public double evaluate(double x, double y) { return sqrt(x); } },
    tan       { public double evaluate(double x, double y) { return tan(x); } },
    tanh      { public double evaluate(double x, double y) { return tanh(x); } },
    erf       { public double evaluate(double x, double y) { return ScalarFunctions.Erf.erf(x); } },

    atan2(2)  { public double evaluate(double x, double y) { return atan2(x,y); } },
    fmod(2)   { public double evaluate(double x, double y) { return x % y; } },
    ldexp(2)  { public double evaluate(double x, double y) { return x*pow(2,(int)y); } },
    max(2)    { public double evaluate(double x, double y) { return max(x,y); } },
    min(2)    { public double evaluate(double x, double y) { return min(x,y); } },
    pow(2)    { public double evaluate(double x, double y) { return pow(x,y); } },
    bit(2)    { public double evaluate(double x, double y) { return ((int)y < 8 && (int)y >= 0 && ((int)x & (1 << (int)y)) != 0) ? 1.0 : 0.0; } },
    hamming(2) { public double evaluate(double x, double y) { return ScalarFunctions.Hamming.hamming(x, y); } };

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
