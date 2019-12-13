// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.functions;

import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.EvaluationContext;
import com.yahoo.tensor.evaluation.Name;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class DynamicTensorTestCase {

    @Test
    public void testDynamicTensorFunction() {
        TensorType dense = TensorType.fromSpec("tensor(x[3])");
        DynamicTensor<Name> t1 = DynamicTensor.from(dense,
                                                    List.of(new Constant(1), new Constant(2), new Constant(3)));
        assertEquals(Tensor.from(dense, "[1, 2, 3]"), t1.evaluate());
        assertEquals("tensor(x[3]):{{x:0}:1.0,{x:1}:2.0,{x:2}:3.0}", t1.toString());

        TensorType sparse = TensorType.fromSpec("tensor(x{})");
        DynamicTensor<Name>  t2 = DynamicTensor.from(sparse,
                                                     Collections.singletonMap(new TensorAddress.Builder(sparse).add("x", "a").build(),
                                                                                          new Constant(5)));
        assertEquals(Tensor.from(sparse, "{{x:a}:5}"), t2.evaluate());
        assertEquals("tensor(x{}):{{x:a}:5.0}", t2.toString());
    }

    @Ignore // Enable for benchmarking
    public void benchMarkTensorAddressBuilder() {
        long start = System.nanoTime();
        TensorType sparse = TensorType.fromSpec("tensor(x{})");
        for (int i=0; i < 10000; i++) {
            TensorAddress.Builder builder = new TensorAddress.Builder(sparse);
            for (int j=0; j < 1000; j++) {
                builder.add("x", String.valueOf(j));
            }
        }
        System.out.println("Took " + (System.nanoTime() - start) + " ns");
    }

    private static class Constant implements ScalarFunction<Name> {

        private final double value;

        public Constant(double value) { this.value = value; }

        @Override
        public Double apply(EvaluationContext<Name> evaluationContext) { return value; }

        @Override
        public String toString() { return String.valueOf(value); }

    }

}
