// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.junit.Assert.assertThat;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 * @since 5.1.9
 */
public class MathFunctionsTestCase {
    @Test
    public void testMathFunctions() {
        //if this fails, update the count AND add a test in each of the two blocks below
        assertThat(MathFunctions.Function.values().length, is(21));


        assertThat(MathFunctions.Function.create(0), sameInstance(MathFunctions.Function.EXP));
        assertThat(MathFunctions.Function.create(1), sameInstance(MathFunctions.Function.POW));
        assertThat(MathFunctions.Function.create(2), sameInstance(MathFunctions.Function.LOG));
        assertThat(MathFunctions.Function.create(3), sameInstance(MathFunctions.Function.LOG1P));
        assertThat(MathFunctions.Function.create(4), sameInstance(MathFunctions.Function.LOG10));
        assertThat(MathFunctions.Function.create(5), sameInstance(MathFunctions.Function.SIN));
        assertThat(MathFunctions.Function.create(6), sameInstance(MathFunctions.Function.ASIN));
        assertThat(MathFunctions.Function.create(7), sameInstance(MathFunctions.Function.COS));
        assertThat(MathFunctions.Function.create(8), sameInstance(MathFunctions.Function.ACOS));
        assertThat(MathFunctions.Function.create(9), sameInstance(MathFunctions.Function.TAN));
        assertThat(MathFunctions.Function.create(10), sameInstance(MathFunctions.Function.ATAN));
        assertThat(MathFunctions.Function.create(11), sameInstance(MathFunctions.Function.SQRT));
        assertThat(MathFunctions.Function.create(12), sameInstance(MathFunctions.Function.SINH));
        assertThat(MathFunctions.Function.create(13), sameInstance(MathFunctions.Function.ASINH));
        assertThat(MathFunctions.Function.create(14), sameInstance(MathFunctions.Function.COSH));
        assertThat(MathFunctions.Function.create(15), sameInstance(MathFunctions.Function.ACOSH));
        assertThat(MathFunctions.Function.create(16), sameInstance(MathFunctions.Function.TANH));
        assertThat(MathFunctions.Function.create(17), sameInstance(MathFunctions.Function.ATANH));
        assertThat(MathFunctions.Function.create(18), sameInstance(MathFunctions.Function.CBRT));
        assertThat(MathFunctions.Function.create(19), sameInstance(MathFunctions.Function.HYPOT));
        assertThat(MathFunctions.Function.create(20), sameInstance(MathFunctions.Function.FLOOR));


        assertThat(MathFunctions.newInstance(MathFunctions.Function.EXP, null, null), instanceOf(MathExpFunction.class));
        assertThat(MathFunctions.newInstance(MathFunctions.Function.POW, null, null), instanceOf(MathPowFunction.class));
        assertThat(MathFunctions.newInstance(MathFunctions.Function.LOG, null, null), instanceOf(MathLogFunction.class));
        assertThat(MathFunctions.newInstance(MathFunctions.Function.LOG1P, null, null), instanceOf(MathLog1pFunction.class));
        assertThat(MathFunctions.newInstance(MathFunctions.Function.LOG10, null, null), instanceOf(MathLog10Function.class));
        assertThat(MathFunctions.newInstance(MathFunctions.Function.SIN, null, null), instanceOf(MathSinFunction.class));
        assertThat(MathFunctions.newInstance(MathFunctions.Function.ASIN, null, null), instanceOf(MathASinFunction.class));
        assertThat(MathFunctions.newInstance(MathFunctions.Function.COS, null, null), instanceOf(MathCosFunction.class));
        assertThat(MathFunctions.newInstance(MathFunctions.Function.ACOS, null, null), instanceOf(MathACosFunction.class));
        assertThat(MathFunctions.newInstance(MathFunctions.Function.TAN, null, null), instanceOf(MathTanFunction.class));
        assertThat(MathFunctions.newInstance(MathFunctions.Function.ATAN, null, null), instanceOf(MathATanFunction.class));
        assertThat(MathFunctions.newInstance(MathFunctions.Function.SQRT, null, null), instanceOf(MathSqrtFunction.class));
        assertThat(MathFunctions.newInstance(MathFunctions.Function.SINH, null, null), instanceOf(MathSinHFunction.class));
        assertThat(MathFunctions.newInstance(MathFunctions.Function.ASINH, null, null), instanceOf(MathASinHFunction.class));
        assertThat(MathFunctions.newInstance(MathFunctions.Function.COSH, null, null), instanceOf(MathCosHFunction.class));
        assertThat(MathFunctions.newInstance(MathFunctions.Function.ACOSH, null, null), instanceOf(MathACosHFunction.class));
        assertThat(MathFunctions.newInstance(MathFunctions.Function.TANH, null, null), instanceOf(MathTanHFunction.class));
        assertThat(MathFunctions.newInstance(MathFunctions.Function.ATANH, null, null), instanceOf(MathATanHFunction.class));
        assertThat(MathFunctions.newInstance(MathFunctions.Function.CBRT, null, null), instanceOf(MathCbrtFunction.class));
        assertThat(MathFunctions.newInstance(MathFunctions.Function.HYPOT, null, null), instanceOf(MathHypotFunction.class));
        assertThat(MathFunctions.newInstance(MathFunctions.Function.FLOOR, null, null), instanceOf(MathFloorFunction.class));
    }
}
