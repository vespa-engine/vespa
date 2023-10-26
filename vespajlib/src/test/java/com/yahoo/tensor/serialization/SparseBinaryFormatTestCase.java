// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.serialization;

import com.yahoo.io.GrowableByteBuffer;
import com.yahoo.tensor.MixedTensor;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;
import com.yahoo.tensor.TensorType;
import org.junit.Test;

import java.util.Arrays;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Tests for the sparse binary format.
 *
 * @author geirst
 */
public class SparseBinaryFormatTestCase {

    @Test
    public void testSerialization() {
        assertSerialization("tensor(x{}):{}");
        assertSerialization("tensor(x{}):{{x:0}:2.0}");
        assertSerialization("tensor(dimX{},dimY{}):{{dimX:labelA,dimY:labelB}:2.0,{dimY:labelC,dimX:labelD}:3.0}");
        assertSerialization("tensor(x{},y{}):{{x:0,y:1}:2.0}");
        assertSerialization("tensor(x{},y{}):{{x:0,y:1}:2.0,{x:1,y:4}:3.0}");
        assertSerialization("tensor(x{},y{},z{}):{{y:0,x:0,z:3}:2.0}");
        assertSerialization("tensor(x{},y{},z{}):{{y:0,x:0,z:3}:2.0,{y:1,x:0,z:6}:3.0}");
    }

    @Test
    public void testSerializationFormatIsDecidedByTensorTypeNotImplementationType() {
        Tensor sparse        =      Tensor.Builder.of(TensorType.fromSpec("tensor(x{})"))
                                                  .cell(TensorAddress.ofLabels("key1"), 9.1).build();
        Tensor sparseAsMixed = MixedTensor.Builder.of(TensorType.fromSpec("tensor(x{})"))
                                                  .cell(TensorAddress.ofLabels("key1"), 9.1).build();
        byte[] sparseEncoded        = TypedBinaryFormat.encode(sparse);
        byte[] sparseAsMixedEncoded = TypedBinaryFormat.encode(sparseAsMixed);
        assertEquals(Arrays.toString(sparseEncoded), Arrays.toString(sparseAsMixedEncoded));
    }

    @Test
    public void testSerializationToSeparateType() {
        try {
            assertSerialization(Tensor.from("tensor(x{},y{}):{{x:0,y:0}:2.0}"), TensorType.fromSpec("tensor(x{})"));
            fail("Expected exception");
        }
        catch (IllegalArgumentException expected) {
            assertEquals("Type/instance mismatch: A tensor of type tensor(x{},y{}) cannot be assigned to type tensor(x{})", expected.getMessage());
        }
    }

    @Test
    public void requireThatSerializationFormatDoesNotChange() {
        byte[] encodedTensor = new byte[] {1, // binary format type
                2, // num dimensions
                2, (byte)'x', (byte)'y', 1, (byte)'z', // dimensions
                2, // num cells,
                2, (byte)'a', (byte)'b', 1, (byte)'e', 64, 0, 0, 0, 0, 0, 0, 0, // cell 0
                2, (byte)'c', (byte)'d', 1, (byte)'e', 64, 8, 0, 0, 0, 0, 0, 0}; // cell 1
        Tensor tensor = Tensor.from("tensor(xy{},z{}):{{xy:ab,z:e}:2.0,{xy:cd,z:e}:3.0}");
        assertEquals(Arrays.toString(encodedTensor), Arrays.toString(TypedBinaryFormat.encode(tensor)));
    }

    @Test
    public void requireThatFloatSerializationFormatDoesNotChange() {
        byte[] encodedTensor = new byte[] {
                5, // binary format type
                1, // float type
                2, // num dimensions
                2, (byte)'x', (byte)'y', 1, (byte)'z', // dimensions
                2, // num cells,
                2, (byte)'a', (byte)'b', 1, (byte)'e', 64, 0, 0, 0, // cell 0
                2, (byte)'c', (byte)'d', 1, (byte)'e', 64, 64, 0, 0}; // cell 1
        Tensor tensor = Tensor.from("tensor<float>(xy{},z{}):{{xy:ab,z:e}:2.0,{xy:cd,z:e}:3.0}");
        assertEquals(Arrays.toString(encodedTensor), Arrays.toString(TypedBinaryFormat.encode(tensor)));
    }

    @Test
    public void requireThatBFloat16SerializationFormatDoesNotChange() {
        byte[] encodedTensor = new byte[] {
                5, // binary format type
                2, // bfloat16 type
                2, // num dimensions
                2, (byte)'x', (byte)'y', 1, (byte)'z', // dimensions
                2, // num cells,
                2, (byte)'a', (byte)'b', 1, (byte)'e', 64, 0,   // cell 0
                2, (byte)'c', (byte)'d', 1, (byte)'e', 64, 64}; // cell 1
        Tensor tensor = Tensor.from("tensor<bfloat16>(xy{},z{}):{{xy:ab,z:e}:2.0,{xy:cd,z:e}:3.0}");
        assertEquals(Arrays.toString(encodedTensor), Arrays.toString(TypedBinaryFormat.encode(tensor)));
    }

    @Test
    public void requireThatInt8SerializationFormatDoesNotChange() {
        byte[] encodedTensor = new byte[] {
                5, // binary format type
                3, // int8 type
                2, // num dimensions
                2, (byte)'x', (byte)'y', 1, (byte)'z', // dimensions
                2, // num cells,
                2, (byte)'a', (byte)'b', 1, (byte)'e', 2,  // cell 0
                2, (byte)'c', (byte)'d', 1, (byte)'e', 3}; // cell 1
        Tensor tensor = Tensor.from("tensor<int8>(xy{},z{}):{{xy:ab,z:e}:2.0,{xy:cd,z:e}:3.0}");
        assertEquals(Arrays.toString(encodedTensor), Arrays.toString(TypedBinaryFormat.encode(tensor)));
    }

    @Test
    public void testSerializationOfDifferentValueTypes() {
        assertSerialization("tensor<double>(x{},y{}):{{x:0,y:0}:2.0, {x:0,y:1}:3.0, {x:1,y:0}:4.0, {x:1,y:1}:5.0}");
        assertSerialization("tensor<float>(x{},y{}):{{x:0,y:0}:2.0, {x:0,y:1}:3.0, {x:1,y:0}:4.0, {x:1,y:1}:5.0}");
        assertSerialization("tensor<bfloat16>(x{},y{}):{{x:0,y:0}:2.0, {x:0,y:1}:3.0, {x:1,y:0}:4.0, {x:1,y:1}:5.0}");
        assertSerialization("tensor<int8>(x{},y{}):{{x:0,y:0}:2, {x:0,y:1}:3, {x:1,y:0}:4, {x:1,y:1}:5}");
    }

    private void assertSerialization(String tensorString) {
        assertSerialization(Tensor.from(tensorString));
    }

    private void assertSerialization(Tensor tensor) {
        assertSerialization(tensor, tensor.type());
    }

    private void assertSerialization(Tensor tensor, TensorType expectedType) {
        byte[] encodedTensor = TypedBinaryFormat.encode(tensor);
        Tensor decodedTensor = TypedBinaryFormat.decode(Optional.of(expectedType),
                                                        GrowableByteBuffer.wrap(encodedTensor));
        assertEquals(tensor, decodedTensor);
    }

}

