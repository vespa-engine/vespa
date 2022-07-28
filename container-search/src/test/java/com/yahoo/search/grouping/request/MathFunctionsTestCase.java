// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Einar M R Rosenvinge
 */
public class MathFunctionsTestCase {

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

        assertTrue(MathFunctions.newInstance(MathFunctions.Function.EXP, null, null) instanceof MathExpFunction);
        assertTrue(MathFunctions.newInstance(MathFunctions.Function.POW, null, null) instanceof MathPowFunction);
        assertTrue(MathFunctions.newInstance(MathFunctions.Function.LOG, null, null) instanceof MathLogFunction);
        assertTrue(MathFunctions.newInstance(MathFunctions.Function.LOG1P, null, null) instanceof MathLog1pFunction);
        assertTrue(MathFunctions.newInstance(MathFunctions.Function.LOG10, null, null) instanceof MathLog10Function);
        assertTrue(MathFunctions.newInstance(MathFunctions.Function.SIN, null, null) instanceof MathSinFunction);
        assertTrue(MathFunctions.newInstance(MathFunctions.Function.ASIN, null, null) instanceof MathASinFunction);
        assertTrue(MathFunctions.newInstance(MathFunctions.Function.COS, null, null) instanceof MathCosFunction);
        assertTrue(MathFunctions.newInstance(MathFunctions.Function.ACOS, null, null) instanceof MathACosFunction);
        assertTrue(MathFunctions.newInstance(MathFunctions.Function.TAN, null, null) instanceof MathTanFunction);
        assertTrue(MathFunctions.newInstance(MathFunctions.Function.ATAN, null, null) instanceof MathATanFunction);
        assertTrue(MathFunctions.newInstance(MathFunctions.Function.SQRT, null, null) instanceof MathSqrtFunction);
        assertTrue(MathFunctions.newInstance(MathFunctions.Function.SINH, null, null) instanceof MathSinHFunction);
        assertTrue(MathFunctions.newInstance(MathFunctions.Function.ASINH, null, null) instanceof MathASinHFunction);
        assertTrue(MathFunctions.newInstance(MathFunctions.Function.COSH, null, null) instanceof MathCosHFunction);
        assertTrue(MathFunctions.newInstance(MathFunctions.Function.ACOSH, null, null) instanceof MathACosHFunction);
        assertTrue(MathFunctions.newInstance(MathFunctions.Function.TANH, null, null) instanceof MathTanHFunction);
        assertTrue(MathFunctions.newInstance(MathFunctions.Function.ATANH, null, null) instanceof MathATanHFunction);
        assertTrue(MathFunctions.newInstance(MathFunctions.Function.CBRT, null, null) instanceof MathCbrtFunction);
        assertTrue(MathFunctions.newInstance(MathFunctions.Function.HYPOT, null, null) instanceof MathHypotFunction);
        assertTrue(MathFunctions.newInstance(MathFunctions.Function.FLOOR, null, null) instanceof MathFloorFunction);
    }

}
