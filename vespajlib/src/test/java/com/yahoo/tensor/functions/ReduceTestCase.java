// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
        assertEquals(1.0, Tensor.from("tensor(x[1])", "[1]").median().asDouble(), delta);
        assertEquals(1.5, Tensor.from("tensor(x[2])", "[1, 2]").median().asDouble(), delta);
        assertEquals(3.0, Tensor.from("tensor(x[7])", "[3, 1, 1, 1, 4, 4, 4]").median().asDouble(), delta);
        assertEquals(2.0, Tensor.from("tensor(x[6])", "[3, 1, 1, 1, 4, 4]").median().asDouble(), delta);
        assertEquals(2.0, Tensor.from("tensor(x{})", "{{x: foo}: 3, {x:bar}: 1}").median().asDouble(), delta);

        assertNan(Tensor.Builder.of("tensor(x[3])").cell(Double.NaN, 0).cell(1, 1).cell(2, 2).build().median());
        assertNan(Tensor.Builder.of("tensor(x[3])").cell(Double.NaN, 2).cell(1, 1).cell(2, 0).build().median());
        assertNan(Tensor.Builder.of("tensor(x[1])").cell(Double.NaN, 0).build().median());
    }

    @Test
    public void testEmptyReduce() {
        assertEquals(0.0, Tensor.from("tensor(x[3],y{})", "{}").avg().asDouble(), delta);
        assertEquals(0.0, Tensor.from("tensor(x[3],y{})", "{}").max().asDouble(), delta);
        assertEquals(0.0, Tensor.from("tensor(x[3],y{})", "{}").median().asDouble(), delta);
        assertEquals(0.0, Tensor.from("tensor(x[3],y{})", "{}").min().asDouble(), delta);
        assertEquals(0.0, Tensor.from("tensor(x[3],y{})", "{}").prod().asDouble(), delta);
        assertEquals(0.0, Tensor.from("tensor(x[3],y{})", "{}").sum().asDouble(), delta);
        assertEquals(0.0, Tensor.from("tensor(x[3],y{})", "{}").count().asDouble(), delta);
    }

    private void assertNan(Tensor tensor) {
        assertTrue(tensor + " is NaN", Double.isNaN(tensor.asDouble()));
    }

}
