// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor;

import com.google.common.collect.Sets;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Basic tensor tests. Tensor operations are tested in EvaluationTestCase
 *
 * @author bratseth
 */
public class MappedTensorTestCase {

    @Test
    public void testEmpty() {
        TensorType type = new TensorType.Builder().mapped("x").build();
        Tensor empty = Tensor.Builder.of(type).build();
        assertTrue(empty instanceof MappedTensor);
        assertTrue(empty.isEmpty());
        assertEquals("tensor(x{}):{}", empty.toString());
        Tensor emptyFromString = Tensor.from(type, "{}");
        assertEquals("tensor(x{}):{}", Tensor.from("tensor(x{}):{}").toString());
        assertTrue(emptyFromString.isEmpty());
        assertTrue(emptyFromString instanceof MappedTensor);
        assertEquals(empty, emptyFromString);
    }

    @Test
    public void testOneDimensionalBuilding() {
        TensorType type = new TensorType.Builder().mapped("x").build();
        Tensor tensor = Tensor.Builder.of(type).
                cell().label("x", "0").value(1).
                cell().label("x", "1").value(2).build();
        assertEquals(Sets.newHashSet("x"), tensor.type().dimensionNames());
        assertEquals("{{x:0}:1.0,{x:1}:2.0}", tensor.toString());
    }

    @Test
    public void testTwoDimensionalBuilding() {
        TensorType type = new TensorType.Builder().mapped("x").mapped("y").build();
        Tensor tensor = Tensor.Builder.of(type).
                cell().label("x", "0").label("y", "0").value(1).
                cell().label("x", "1").label("y", "0").value(2).build();
        assertEquals(Sets.newHashSet("x", "y"), tensor.type().dimensionNames());
        assertEquals("{{x:0,y:0}:1.0,{x:1,y:0}:2.0}", tensor.toString());
    }

}
