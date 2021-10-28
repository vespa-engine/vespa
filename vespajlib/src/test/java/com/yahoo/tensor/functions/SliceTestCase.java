// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.functions;

import com.yahoo.tensor.Tensor;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author bratseth
 */
public class SliceTestCase {

    private static final double delta = 0.000001;

    @Test
    public void testSliceFunctionGeneralFormToRank0() {
        Tensor input = Tensor.from("tensor(key{},x{}):{ {key:foo,x:0}:1.4, {key:bar,x:0}:2.3 }");
        Tensor result = new Slice<>(new ConstantTensor<>(input),
                                    List.of(new Slice.DimensionValue<>("key", "bar"),
                                          new Slice.DimensionValue<>("x", 0)))
                                .evaluate();
        assertEquals(0, result.type().rank());
        assertEquals(2.3, result.asDouble(), delta);
    }

    @Test
    public void testSliceFunctionGeneralFormToRank0ReverseDimensionOrder() {
        Tensor input = Tensor.from("tensor(key{},x{}):{ {key:foo,x:0}:1.4, {key:bar,x:0}:2.3 }");
        Tensor result = new Slice<>(new ConstantTensor<>(input),
                                    List.of(new Slice.DimensionValue<>("x", 0),
                                            new Slice.DimensionValue<>("key", "bar")))
                                .evaluate();
        assertEquals(0, result.type().rank());
        assertEquals(2.3, result.asDouble(), delta);
    }

    @Test
    public void testSliceFunctionGeneralFormToIndexedRank2to1() {
        Tensor input = Tensor.from("tensor(key{},x[2]):{ {key:foo,x:0}:1.3, {key:foo,x:1}:1.4, {key:bar,x:0}:2.3, {key:bar,x:1}:2.4 }");
        Tensor result = new Slice<>(new ConstantTensor<>(input),
                                    List.of(new Slice.DimensionValue<>("key", "bar")))
                                .evaluate();
        assertEquals(1, result.type().rank());
        assertEquals(Tensor.from("tensor(x[2]):[2.3, 2.4]]"), result);
    }

    @Test
    public void testSliceFunctionGeneralFormToMappedRank2to1() {
        Tensor input = Tensor.from("tensor(key{},x[2]):{ {key:foo,x:0}:1.3, {key:foo,x:1}:1.4, {key:bar,x:0}:2.3, {key:bar,x:1}:2.4 }");
        Tensor result = new Slice<>(new ConstantTensor<>(input),
                                    List.of(new Slice.DimensionValue<>("x", 0)))
                                .evaluate();
        assertEquals(1, result.type().rank());
        assertEquals(Tensor.from("tensor(key{}):{{key:foo}:1.3, {key:bar}:2.3}"), result);
    }

    @Test
    public void testSliceFunctionGeneralFormToMappedRank3to1() {
        Tensor input = Tensor.from("tensor(key{},x[2],y[1]):{ {key:foo,x:0,y:0}:1.3, {key:foo,x:1,y:0}:1.4, {key:bar,x:0,y:0}:2.3, {key:bar,x:1,y:0}:2.4 }");
        Tensor result = new Slice<>(new ConstantTensor<>(input),
                                    List.of(new Slice.DimensionValue<>("x", 1),
                                            new Slice.DimensionValue<>("y", 0)))
                                .evaluate();
        assertEquals(1, result.type().rank());
        assertEquals(Tensor.from("tensor(key{}):{{key:foo}:1.4, {key:bar}:2.4}"), result);
    }

    @Test
    public void testSliceFunctionGeneralFormToMappedRank3to1ReverseDimensionOrder() {
        Tensor input = Tensor.from("tensor(key{},x[2],y[1]):{ {key:foo,x:0,y:0}:1.3, {key:foo,x:1,y:0}:1.4, {key:bar,x:0,y:0}:2.3, {key:bar,x:1,y:0}:2.4 }");
        Tensor result = new Slice<>(new ConstantTensor<>(input),
                                    List.of(new Slice.DimensionValue<>("y", 0),
                                            new Slice.DimensionValue<>("x", 1)))
                                .evaluate();
        assertEquals(1, result.type().rank());
        assertEquals(Tensor.from("tensor(key{}):{{key:foo}:1.4, {key:bar}:2.4}"), result);
    }

    @Test
    public void testSliceFunctionGeneralFormToMappedRank3to2() {
        Tensor input = Tensor.from("tensor(key{},x[2],y[1]):{ {key:foo,x:0,y:0}:1.3, {key:foo,x:1,y:0}:1.4, {key:bar,x:0,y:0}:2.3, {key:bar,x:1,y:0}:2.4 }");
        Tensor result = new Slice<>(new ConstantTensor<>(input),
                                    List.of(new Slice.DimensionValue<>("x", 1)))
                                .evaluate();
        assertEquals(2, result.type().rank());
        assertEquals(Tensor.from("tensor(key{},y[1]):{{key:foo,y:0}:1.4, {key:bar,y:0}:2.4}"), result);
    }

    @Test
    public void testSliceFunctionSingleMappedDimensionToRank0() {
        Tensor input = Tensor.from("tensor(key{}):{ {key:foo}:1.4, {key:bar}:2.3 }");
        Tensor result = new Slice<>(new ConstantTensor<>(input),
                                    List.of(new Slice.DimensionValue<>("foo")))
                                .evaluate();
        assertEquals(0, result.type().rank());
        assertEquals(1.4, result.asDouble(), delta);
    }

    @Test
    public void testSliceFunctionSingleIndexedDimensionToRank0() {
        Tensor input = Tensor.from("tensor(key[3]):[1.1, 2.2, 3.3]");
        Tensor result = new Slice<>(new ConstantTensor<>(input),
                                    List.of(new Slice.DimensionValue<>(2)))
                                .evaluate();
        assertEquals(0, result.type().rank());
        assertEquals(3.3, result.asDouble(), delta);
    }

    @Test
    public void testSliceFunctionShortFormWithMultipleDimensionsIsNotAllowed() {
        try {
            Tensor input = Tensor.from("tensor(key{},x{}):{ {key:foo,x:0}:1.4, {key:bar,x:0}:2.3 }");
            new Slice<>(new ConstantTensor<>(input),
                        List.of(new Slice.DimensionValue<>("bar"),
                                new Slice.DimensionValue<>(0)))
                    .evaluate();
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("Short form of subspace addresses is only supported with a single dimension: Specify dimension names explicitly instead",
                         e.getMessage());
        }
    }

    @Test
    public void testToString() {
        Tensor input = Tensor.from("tensor(key[3]):[1.1, 2.2, 3.3]");
        assertEquals("tensor(key[3]):[1.1, 2.2, 3.3][2]",
                     new Slice<>(new ConstantTensor<>(input),
                                 List.of(new Slice.DimensionValue<>(2)))
                     .toString());
    }

}
