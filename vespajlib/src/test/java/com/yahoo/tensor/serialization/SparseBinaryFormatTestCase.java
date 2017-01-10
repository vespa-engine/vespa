// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.serialization;

import com.google.common.collect.Sets;
import com.yahoo.tensor.Tensor;
import org.junit.Test;

import java.util.Arrays;
import java.util.Set;

import static org.junit.Assert.assertEquals;

/**
 * Tests for the sparse binary format.
 *
 * TODO: When new formats are added we should refactor this test to test all formats
 *       with the same set of tensor inputs (if feasible).
 *
 * @author geirst
 */
public class SparseBinaryFormatTestCase {

    private static void assertSerialization(String tensorString) {
        assertSerialization(Tensor.from(tensorString));
    }

    private static void assertSerialization(String tensorString, Set<String> dimensions) {
        Tensor tensor = Tensor.from(tensorString);
        assertEquals(dimensions, tensor.type().dimensionNames());
        assertSerialization(tensor);
    }

    private static void assertSerialization(Tensor tensor) {
        byte[] encodedTensor = TypedBinaryFormat.encode(tensor);
        Tensor decodedTensor = TypedBinaryFormat.decode(encodedTensor);
        assertEquals(tensor, decodedTensor);
    }

    @Test
    public void testSerializationOfTensorsWithDenseTensorAddresses() {
        assertSerialization("{}");
        assertSerialization("{{x:0}:2.0}");
        assertSerialization("{{x:0}:2.0,{x:1}:3.0}");
        assertSerialization("{{x:0,y:0}:2.0}");
        assertSerialization("{{x:0,y:0}:2.0,{x:0,y:1}:3.0}");
        assertSerialization("{{y:0,x:0}:2.0}");
        assertSerialization("{{y:0,x:0}:2.0,{y:1,x:0}:3.0}");
        assertSerialization("{{dimX:labelA,dimY:labelB}:2.0,{dimY:labelC,dimX:labelD}:3.0}");
    }

    @Test
    public void testSerializationOfTensorsWithSparseTensorAddresses() {
        assertSerialization("{{x:0}:2.0, {x:1}:3.0}", Sets.newHashSet("x"));
        assertSerialization("tensor(x{},y{}):{{x:0,y:1}:2.0}", Sets.newHashSet("x", "y"));
        assertSerialization("tensor(x{},y{}):{{x:0,y:1}:2.0,{x:1,y:4}:3.0}", Sets.newHashSet("x", "y"));
        assertSerialization("tensor(x{},y{},z{}):{{y:0,x:0,z:3}:2.0}", Sets.newHashSet("x", "y", "z"));
        assertSerialization("tensor(x{},y{},z{}):{{y:0,x:0,z:3}:2.0,{y:1,x:0,z:6}:3.0}", Sets.newHashSet("x", "y", "z"));
    }

    @Test
    public void requireThatCompactSerializationFormatDoNotChange() {
        byte[] encodedTensor = new byte[] {1, // binary format type
                2, // num dimensions
                2, (byte)'x', (byte)'y', 1, (byte)'z', // dimensions
                2, // num cells,
                2, (byte)'a', (byte)'b', 1, (byte)'e', 64, 0, 0, 0, 0, 0, 0, 0, // cell 0
                2, (byte)'c', (byte)'d', 1, (byte)'e', 64, 8, 0, 0, 0, 0, 0, 0}; // cell 1
        assertEquals(Arrays.toString(encodedTensor),
                Arrays.toString(TypedBinaryFormat.encode(Tensor.from("{{xy:ab,z:e}:2.0,{xy:cd,z:e}:3.0}"))));
    }

}

