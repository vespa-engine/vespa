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
        Tensor tensor = new MapTensorBuilder().build();
        assertEquals(0, tensor.dimensions().size());
        assertEquals("{}", tensor.toString());
    }

    @Test
    public void requireThatOneDimensionalTensorCanBeBuilt() {
        Tensor tensor = new MapTensorBuilder().
                cell().label("x", "0").value(1).
                cell().label("x", "1").value(2).build();
        assertEquals(Sets.newHashSet("x"), tensor.dimensions());
        assertEquals("{{x:0}:1.0,{x:1}:2.0}", tensor.toString());
    }

    @Test
    public void requireThatTwoDimensionalTensorCanBeBuilt() {
        Tensor tensor = new MapTensorBuilder().
                cell().label("x", "0").label("y", "0").value(1).
                cell().label("x", "1").label("y", "0").value(2).build();
        assertEquals(Sets.newHashSet("x", "y"), tensor.dimensions());
        assertEquals("{{x:1,y:0}:2.0,{x:0,y:0}:1.0}", tensor.toString());
    }

    @Test
    public void requireThatExtraDimensionsCanBeSpecified() {
        Tensor tensor = new MapTensorBuilder().dimension("y").dimension("z").
                cell().label("x", "0").value(1).build();
        assertEquals(Sets.newHashSet("x", "y", "z"), tensor.dimensions());
        assertEquals("tensor(x{},y{},z{}):{{x:0}:1.0}", tensor.toString());
    }

}
