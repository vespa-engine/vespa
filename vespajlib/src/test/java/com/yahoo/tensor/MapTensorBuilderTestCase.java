// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor;

import com.google.common.collect.Sets;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author geirst
 */
public class MapTensorBuilderTestCase {

    @Test
    public void requireThatEmptyTensorCanBeBuilt() {
        Tensor tensor = new MapTensorBuilder(TensorType.empty).build();
        assertEquals(0, tensor.type().dimensions().size());
        assertEquals("{}", tensor.toString());
    }

    @Test
    public void requireThatOneDimensionalTensorCanBeBuilt() {
        TensorType type = new TensorType.Builder().mapped("x").build();
        Tensor tensor = new MapTensorBuilder(type).
                cell().label("x", "0").value(1).
                cell().label("x", "1").value(2).build();
        assertEquals(Sets.newHashSet("x"), tensor.type().dimensionNames());
        assertEquals("{{x:0}:1.0,{x:1}:2.0}", tensor.toString());
    }

    @Test
    public void requireThatTwoDimensionalTensorCanBeBuilt() {
        TensorType type = new TensorType.Builder().mapped("x").mapped("y").build();
        Tensor tensor = new MapTensorBuilder(type).
                cell().label("x", "0").label("y", "0").value(1).
                cell().label("x", "1").label("y", "0").value(2).build();
        assertEquals(Sets.newHashSet("x", "y"), tensor.type().dimensionNames());
        assertEquals("{{x:0,y:0}:1.0,{x:1,y:0}:2.0}", tensor.toString());
    }

}
