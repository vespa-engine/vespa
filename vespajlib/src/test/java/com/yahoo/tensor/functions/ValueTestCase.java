// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.functions;

import com.yahoo.tensor.Tensor;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author bratseth
 */
public class ValueTestCase {

    private static final double delta = 0.000001;

    @Test
    public void testValueFunctionGeneralForm() {
        Tensor input = Tensor.from("tensor(key{},x{}):{ {key:foo,x:0}:1.4, {key:bar,x:0}:2.3 }");
        Tensor result = new Value<>(new ConstantTensor<>(input),
                                  List.of(new Value.DimensionValue<>("key", "bar"),
                                          new Value.DimensionValue<>("x", 0)))
                                .evaluate();
        assertEquals(0, result.type().rank());
        assertEquals(2.3, result.asDouble(), delta);
    }

    @Test
    public void testValueFunctionSingleMappedDimension() {
        Tensor input = Tensor.from("tensor(key{}):{ {key:foo}:1.4, {key:bar}:2.3 }");
        Tensor result = new Value<>(new ConstantTensor<>(input),
                                    List.of(new Value.DimensionValue<>("foo")))
                                .evaluate();
        assertEquals(0, result.type().rank());
        assertEquals(1.4, result.asDouble(), delta);
    }

    @Test
    public void testValueFunctionSingleIndexedDimension() {
        Tensor input = Tensor.from("tensor(key[3]):[1.1, 2.2, 3.3]");
        Tensor result = new Value<>(new ConstantTensor<>(input),
                                    List.of(new Value.DimensionValue<>(2)))
                                .evaluate();
        assertEquals(0, result.type().rank());
        assertEquals(3.3, result.asDouble(), delta);
    }

    @Test
    public void testValueFunctionShortFormWithMultipleDimensionsIsNotAllowed() {
        try {
            Tensor input = Tensor.from("tensor(key{},x{}):{ {key:foo,x:0}:1.4, {key:bar,x:0}:2.3 }");
            new Value<>(new ConstantTensor<>(input),
                        List.of(new Value.DimensionValue<>("bar"),
                                new Value.DimensionValue<>(0)))
                    .evaluate();
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("Short form of cell addresses is only supported with a single dimension: Specify dimension names explicitly",
                         e.getMessage());
        }
    }

    @Test
    public void testToString() {
        Tensor input = Tensor.from("tensor(key[3]):[1.1, 2.2, 3.3]");
        assertEquals("tensor(key[3]):[1.1, 2.2, 3.3][2]",
                     new Value<>(new ConstantTensor<>(input),
                                 List.of(new Value.DimensionValue<>(2)))
                     .toString());
    }

}
