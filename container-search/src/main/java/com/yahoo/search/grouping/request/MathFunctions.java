// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

/**
 * @author baldersheim
 */
public abstract class MathFunctions {

    /**
     * Defines the different types of math functions that are available.
     */
    public enum Function {
        EXP,     //  0
        POW,     //  1
        LOG,     //  2
        LOG1P,   //  3
        LOG10,   //  4
        SIN,     //  5
        ASIN,    //  6
        COS,     //  7
        ACOS,    //  8
        TAN,     //  9
        ATAN,    // 10
        SQRT,    // 11
        SINH,    // 12
        ASINH,   // 13
        COSH,    // 14
        ACOSH,   // 15
        TANH,    // 16
        ATANH,   // 17
        CBRT,    // 18
        HYPOT,   // 19
        FLOOR;   // 20

        static Function create(int tid) {
            for(Function p : values()) {
                if (tid == p.ordinal()) {
                    return p;
                }
            }
            return null;
        }
    }
    public static FunctionNode newInstance(Function type, GroupingExpression x, GroupingExpression y) {
        switch (type) {
            case EXP: return new MathExpFunction(x);
            case POW: return new MathPowFunction(x, y);
            case LOG: return new MathLogFunction(x);
            case LOG1P: return new MathLog1pFunction(x);
            case LOG10: return new MathLog10Function(x);
            case SIN: return new MathSinFunction(x);
            case ASIN: return new MathASinFunction(x);
            case COS: return new MathCosFunction(x);
            case ACOS: return new MathACosFunction(x);
            case TAN: return new MathTanFunction(x);
            case ATAN: return new MathATanFunction(x);
            case SQRT: return new MathSqrtFunction(x);
            case SINH: return new MathSinHFunction(x);
            case ASINH: return new MathASinHFunction(x);
            case COSH: return new MathCosHFunction(x);
            case ACOSH: return new MathACosHFunction(x);
            case TANH: return new MathTanHFunction(x);
            case ATANH: return new MathATanHFunction(x);
            case CBRT: return new MathCbrtFunction(x);
            case HYPOT: return new MathHypotFunction(x, y);
            case FLOOR: return new MathFloorFunction(x);
        }
        return null;
    }
}
