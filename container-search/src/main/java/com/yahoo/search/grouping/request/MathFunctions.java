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
        return switch (type) {
            case EXP -> new MathExpFunction(x);
            case POW -> new MathPowFunction(x, y);
            case LOG -> new MathLogFunction(x);
            case LOG1P -> new MathLog1pFunction(x);
            case LOG10 -> new MathLog10Function(x);
            case SIN -> new MathSinFunction(x);
            case ASIN -> new MathASinFunction(x);
            case COS -> new MathCosFunction(x);
            case ACOS -> new MathACosFunction(x);
            case TAN -> new MathTanFunction(x);
            case ATAN -> new MathATanFunction(x);
            case SQRT -> new MathSqrtFunction(x);
            case SINH -> new MathSinHFunction(x);
            case ASINH -> new MathASinHFunction(x);
            case COSH -> new MathCosHFunction(x);
            case ACOSH -> new MathACosHFunction(x);
            case TANH -> new MathTanHFunction(x);
            case ATANH -> new MathATanHFunction(x);
            case CBRT -> new MathCbrtFunction(x);
            case HYPOT -> new MathHypotFunction(x, y);
            case FLOOR -> new MathFloorFunction(x);
        };
    }
}
