// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.tensor.functions;

import java.util.function.DoubleUnaryOperator;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class ScalarFunctionsTestCase {

    void expect_oddf(DoubleUnaryOperator foo, double input, double output) {
        double res = foo.applyAsDouble(input);
        assertEquals("apply("+foo+","+input+") -> ", output, res, 0.000000001);
        input *= -1;
        output *= -1;
        res = foo.applyAsDouble(input);
        assertEquals("apply("+foo+","+input+") -> "+res, output, res, 0.000000001);
    }

    @Test
    public void testErrorFunction() {
        var func = ScalarFunctions.erf();
        // from wikipedia:
        expect_oddf(func, 0.0, 0.0);
        expect_oddf(func, 0.02, 0.022564575);
        expect_oddf(func, 0.04, 0.045111106);
        expect_oddf(func, 0.06, 0.067621594);
        expect_oddf(func, 0.08, 0.090078126);
        expect_oddf(func, 0.1, 0.112462916);
        expect_oddf(func, 0.2, 0.222702589);
        expect_oddf(func, 0.3, 0.328626759);
        expect_oddf(func, 0.4, 0.428392355);
        expect_oddf(func, 0.5, 0.520499878);
        expect_oddf(func, 0.6, 0.603856091);
        expect_oddf(func, 0.7, 0.677801194);
        expect_oddf(func, 0.8, 0.742100965);
        expect_oddf(func, 0.9, 0.796908212);
        expect_oddf(func, 1.0, 0.842700793);
        expect_oddf(func, 1.1, 0.88020507);
        expect_oddf(func, 1.2, 0.910313978);
        expect_oddf(func, 1.3, 0.934007945);
        expect_oddf(func, 1.4, 0.95228512);
        expect_oddf(func, 1.5, 0.966105146);
        expect_oddf(func, 1.6, 0.976348383);
        expect_oddf(func, 1.7, 0.983790459);
        expect_oddf(func, 1.8, 0.989090502);
        expect_oddf(func, 1.9, 0.992790429);
        expect_oddf(func, 2.0, 0.995322265);
        expect_oddf(func, 2.1, 0.997020533);
        expect_oddf(func, 2.2, 0.998137154);
        expect_oddf(func, 2.3, 0.998856823);
        expect_oddf(func, 2.4, 0.999311486);
        expect_oddf(func, 2.5, 0.999593048);
        expect_oddf(func, 3.0, 0.99997791);
        expect_oddf(func, 3.5, 0.999999257);
        // from MPFR:
        expect_oddf(func, 4.0, 0.99999998458);
        expect_oddf(func, 4.2412109375, 0.9999999980);
        expect_oddf(func, 4.2734375,    0.99999999849);
        expect_oddf(func, 4.3203125,    0.9999999990);
        expect_oddf(func, 5.0, 0.999999999998);
        expect_oddf(func, 5.921875,  1.0);
    }

}
