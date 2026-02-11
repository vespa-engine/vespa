// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Einar M R Rosenvinge
 */
public class MathFunctionsTestCase {
    private static final DoubleValue ZERO = new DoubleValue(0.0);

    @Test
    void testMathFunctions() {
        //if this fails, update the count AND add a test in each of the two blocks below
        assertEquals(21, MathFunctions.Function.values().length);

        assertSame(MathFunctions.Function.create(0), MathFunctions.Function.EXP);
        assertSame(MathFunctions.Function.create(1), MathFunctions.Function.POW);
        assertSame(MathFunctions.Function.create(2), MathFunctions.Function.LOG);
        assertSame(MathFunctions.Function.create(3), MathFunctions.Function.LOG1P);
        assertSame(MathFunctions.Function.create(4), MathFunctions.Function.LOG10);
        assertSame(MathFunctions.Function.create(5), MathFunctions.Function.SIN);
        assertSame(MathFunctions.Function.create(6), MathFunctions.Function.ASIN);
        assertSame(MathFunctions.Function.create(7), MathFunctions.Function.COS);
        assertSame(MathFunctions.Function.create(8), MathFunctions.Function.ACOS);
        assertSame(MathFunctions.Function.create(9), MathFunctions.Function.TAN);
        assertSame(MathFunctions.Function.create(10), MathFunctions.Function.ATAN);
        assertSame(MathFunctions.Function.create(11), MathFunctions.Function.SQRT);
        assertSame(MathFunctions.Function.create(12), MathFunctions.Function.SINH);
        assertSame(MathFunctions.Function.create(13), MathFunctions.Function.ASINH);
        assertSame(MathFunctions.Function.create(14), MathFunctions.Function.COSH);
        assertSame(MathFunctions.Function.create(15), MathFunctions.Function.ACOSH);
        assertSame(MathFunctions.Function.create(16), MathFunctions.Function.TANH);
        assertSame(MathFunctions.Function.create(17), MathFunctions.Function.ATANH);
        assertSame(MathFunctions.Function.create(18), MathFunctions.Function.CBRT);
        assertSame(MathFunctions.Function.create(19), MathFunctions.Function.HYPOT);
        assertSame(MathFunctions.Function.create(20), MathFunctions.Function.FLOOR);

        assertTrue(MathFunctions.newInstance(MathFunctions.Function.EXP, ZERO, ZERO) instanceof MathExpFunction);
        assertTrue(MathFunctions.newInstance(MathFunctions.Function.POW, ZERO, ZERO) instanceof MathPowFunction);
        assertTrue(MathFunctions.newInstance(MathFunctions.Function.LOG, ZERO, ZERO) instanceof MathLogFunction);
        assertTrue(MathFunctions.newInstance(MathFunctions.Function.LOG1P, ZERO, ZERO) instanceof MathLog1pFunction);
        assertTrue(MathFunctions.newInstance(MathFunctions.Function.LOG10, ZERO, ZERO) instanceof MathLog10Function);
        assertTrue(MathFunctions.newInstance(MathFunctions.Function.SIN, ZERO, ZERO) instanceof MathSinFunction);
        assertTrue(MathFunctions.newInstance(MathFunctions.Function.ASIN, ZERO, ZERO) instanceof MathASinFunction);
        assertTrue(MathFunctions.newInstance(MathFunctions.Function.COS, ZERO, ZERO) instanceof MathCosFunction);
        assertTrue(MathFunctions.newInstance(MathFunctions.Function.ACOS, ZERO, ZERO) instanceof MathACosFunction);
        assertTrue(MathFunctions.newInstance(MathFunctions.Function.TAN, ZERO, ZERO) instanceof MathTanFunction);
        assertTrue(MathFunctions.newInstance(MathFunctions.Function.ATAN, ZERO, ZERO) instanceof MathATanFunction);
        assertTrue(MathFunctions.newInstance(MathFunctions.Function.SQRT, ZERO, ZERO) instanceof MathSqrtFunction);
        assertTrue(MathFunctions.newInstance(MathFunctions.Function.SINH, ZERO, ZERO) instanceof MathSinHFunction);
        assertTrue(MathFunctions.newInstance(MathFunctions.Function.ASINH, ZERO, ZERO) instanceof MathASinHFunction);
        assertTrue(MathFunctions.newInstance(MathFunctions.Function.COSH, ZERO, ZERO) instanceof MathCosHFunction);
        assertTrue(MathFunctions.newInstance(MathFunctions.Function.ACOSH, ZERO, ZERO) instanceof MathACosHFunction);
        assertTrue(MathFunctions.newInstance(MathFunctions.Function.TANH, ZERO, ZERO) instanceof MathTanHFunction);
        assertTrue(MathFunctions.newInstance(MathFunctions.Function.ATANH, ZERO, ZERO) instanceof MathATanHFunction);
        assertTrue(MathFunctions.newInstance(MathFunctions.Function.CBRT, ZERO, ZERO) instanceof MathCbrtFunction);
        assertTrue(MathFunctions.newInstance(MathFunctions.Function.HYPOT, ZERO, ZERO) instanceof MathHypotFunction);
        assertTrue(MathFunctions.newInstance(MathFunctions.Function.FLOOR, ZERO, ZERO) instanceof MathFloorFunction);
    }

}
