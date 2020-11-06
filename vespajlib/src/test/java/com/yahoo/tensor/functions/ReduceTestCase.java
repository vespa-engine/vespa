// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.functions;

import com.yahoo.tensor.Tensor;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author bratseth
 */
public class ReduceTestCase {

    private static final double delta = 0.00000001;

    @Test
    public void testReduce() {
        assertNan(Tensor.from("tensor(x{})", "{}").median());
        assertEquals(1.0, Tensor.from("tensor(x[1])", "[1]").median().asDouble(), delta);
        assertEquals(1.5, Tensor.from("tensor(x[2])", "[1, 2]").median().asDouble(), delta);
        assertEquals(3.0, Tensor.from("tensor(x[7])", "[3, 1, 1, 1, 4, 4, 4]").median().asDouble(), delta);
        assertEquals(2.0, Tensor.from("tensor(x[6])", "[3, 1, 1, 1, 4, 4]").median().asDouble(), delta);
        assertEquals(2.0, Tensor.from("tensor(x{})", "{{x: foo}: 3, {x:bar}: 1}").median().asDouble(), delta);

        assertNan(Tensor.Builder.of("tensor(x[3])").cell(Double.NaN, 0).cell(1, 1).cell(2, 2).build().median());
        assertNan(Tensor.Builder.of("tensor(x[3])").cell(Double.NaN, 2).cell(1, 1).cell(2, 0).build().median());
        assertNan(Tensor.Builder.of("tensor(x[1])").cell(Double.NaN, 0).build().median());
    }

    private void assertNan(Tensor tensor) {
        assertTrue(tensor + " is NaN", Double.isNaN(tensor.asDouble()));
    }

}
