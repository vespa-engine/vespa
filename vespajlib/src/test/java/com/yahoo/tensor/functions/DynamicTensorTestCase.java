// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.functions;

import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.EvaluationContext;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class DynamicTensorTestCase {

    @Test
    public void testDynamicTensorFunction() {
        TensorType dense = TensorType.fromSpec("tensor(x[3])");
        DynamicTensor t1 = DynamicTensor.from(dense,
                                              List.of(new Constant(1), new Constant(2), new Constant(3)));
        assertEquals(Tensor.from(dense, "[1, 2, 3]"), t1.evaluate());

        TensorType sparse = TensorType.fromSpec("tensor(x{})");
        DynamicTensor t2 = DynamicTensor.from(sparse,
                                              Collections.singletonMap(new TensorAddress.Builder(sparse).add("x", "a").build(),
                                                                       new Constant(5)));
        assertEquals(Tensor.from(sparse, "{{x:a}:5}"), t2.evaluate());
    }

    private static class Constant implements Function<EvaluationContext<?>, Double> {

        private final double value;

        public Constant(double value) { this.value = value; }

        @Override
        public Double apply(EvaluationContext<?> evaluationContext) { return value; }

    }

}
