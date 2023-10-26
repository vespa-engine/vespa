// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.serialization;

import com.yahoo.io.GrowableByteBuffer;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import org.junit.Test;

import java.util.Arrays;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Tests for the dense binary format.
 *
 * @author bratseth
 */
public class DenseBinaryFormatTestCase {

    @Test
    public void testSerialization() {
        assertSerialization("{-5.37}");
        assertSerialization("tensor(x[]):{{x:0}:2.0}");
        assertSerialization("tensor(x[],y[]):{{x:0,y:0}:2.0}");
        assertSerialization("tensor(x[],y[]):{{x:0,y:0}:2.0, {x:0,y:1}:3.0, {x:1,y:0}:4.0, {x:1,y:1}:5.0}");
        assertSerialization("tensor(x[1],y[2],z[3]):{{y:0,x:0,z:0}:2.0}");
    }

    @Test
    public void testSerializationToSeparateType() {
        assertSerialization(Tensor.from("tensor(x[1],y[1]):{{x:0,y:0}:2.0}"), TensorType.fromSpec("tensor(x[],y[])"));
        try {
            assertSerialization(Tensor.from("tensor(x[2],y[2]):{{x:0,y:0}:2.0}"), TensorType.fromSpec("tensor(x[1],y[1])"));
            fail("Expected exception");
        }
        catch (IllegalArgumentException expected) {
            assertEquals("Type/instance mismatch: A tensor of type tensor(x[2],y[2]) cannot be assigned to type tensor(x[1],y[1])", expected.getMessage());
        }
    }

    @Test
    public void requireThatDefaultSerializationFormatDoesNotChange() {
        byte[] encodedTensor = new byte[]{2, // binary format type
                                          2, // dimension count
                                          2, (byte) 'x', (byte) 'y', 2, // dimension xy with size
                                          1, (byte) 'z', 1, // dimension z with size
                                          64, 0, 0, 0, 0, 0, 0, 0, // value 1
                                          64, 8, 0, 0, 0, 0, 0, 0  // value 2
        };
        assertEquals(Arrays.toString(encodedTensor),
                     Arrays.toString(TypedBinaryFormat.encode(Tensor.from("tensor(xy[],z[]):{{xy:0,z:0}:2.0,{xy:1,z:0}:3.0}"))));
    }

    @Test
    public void requireThatFloatSerializationFormatDoesNotChange() {
        byte[] encodedTensor = new byte[]{6, // binary format type
                1, // float type
                2, // dimension count
                2, (byte) 'x', (byte) 'y', 2, // dimension xy with size
                1, (byte) 'z', 1, // dimension z with size
                64, 0, 0, 0, // value 1
                64, 64, 0, 0,  // value 2
        };
        Tensor tensor = Tensor.from("tensor<float>(xy[],z[]):{{xy:0,z:0}:2.0,{xy:1,z:0}:3.0}");
        assertEquals(Arrays.toString(encodedTensor), Arrays.toString(TypedBinaryFormat.encode(tensor)));
    }

    @Test
    public void requireThatBFloat16SerializationFormatDoesNotChange() {
        byte[] encodedTensor = new byte[]{6, // binary format type
                2, // bfloat16 type
                2, // dimension count
                2, (byte) 'x', (byte) 'y', 2, // dimension xy with size
                1, (byte) 'z', 1, // dimension z with size
                64, 0,   // value 1
                64, 64,  // value 2
        };
        Tensor tensor = Tensor.from("tensor<bfloat16>(xy[],z[]):{{xy:0,z:0}:2.0,{xy:1,z:0}:3.0}");
        assertEquals(Arrays.toString(encodedTensor), Arrays.toString(TypedBinaryFormat.encode(tensor)));
    }

    @Test
    public void requireThatInt8SerializationFormatDoesNotChange() {
        byte[] encodedTensor = new byte[]{6, // binary format type
                3, // int8 type
                2, // dimension count
                2, (byte) 'x', (byte) 'y', 2, // dimension xy with size
                1, (byte) 'z', 1, // dimension z with size
                2,  // value 1
                3,  // value 2
        };
        Tensor tensor = Tensor.from("tensor<int8>(xy[],z[]):{{xy:0,z:0}:2.0,{xy:1,z:0}:3.0}");
        assertEquals(Arrays.toString(encodedTensor), Arrays.toString(TypedBinaryFormat.encode(tensor)));
    }

    @Test
    public void testSerializationOfDifferentValueTypes() {
        assertSerialization("tensor(x[],y[]):{{x:0,y:0}:2.0, {x:0,y:1}:3.0, {x:1,y:0}:4.0, {x:1,y:1}:5.0}");
        assertSerialization("tensor<double>(x[],y[]):{{x:0,y:0}:2.0, {x:0,y:1}:3.0, {x:1,y:0}:4.0, {x:1,y:1}:5.0}");
        assertSerialization("tensor<float>(x[],y[]):{{x:0,y:0}:2.0, {x:0,y:1}:3.0, {x:1,y:0}:4.0, {x:1,y:1}:5.0}");
        assertSerialization("tensor<bfloat16>(x[],y[]):{{x:0,y:0}:2.0, {x:0,y:1}:3.0, {x:1,y:0}:4.0, {x:1,y:1}:5.0}");
        assertSerialization("tensor<int8>(x[],y[]):{{x:0,y:0}:2, {x:0,y:1}:3, {x:1,y:0}:4, {x:1,y:1}:5}");
        assertSerialization("tensor<double>(x[2],y[2]):[2.0, 3.0, 4.0, 5.0]");
        assertSerialization("tensor<float>(x[2],y[2]):[2.0, 3.0, 4.0, 5.0]");
        assertSerialization("tensor<bfloat16>(x[2],y[2]):[2.0, 3.0, 4.0, 5.0]");
        assertSerialization("tensor<int8>(x[2],y[2]):[2, 3, 4, 5]");
    }

    private void assertSerialization(String tensorString) {
        assertSerialization(Tensor.from(tensorString));
    }

    private void assertSerialization(Tensor tensor) {
        assertSerialization(tensor, tensor.type());
    }

    private void assertSerialization(Tensor tensor, TensorType expectedType) {
        byte[] encodedTensor = TypedBinaryFormat.encode(tensor);
        Tensor decodedTensor = TypedBinaryFormat.decode(Optional.of(expectedType), GrowableByteBuffer.wrap(encodedTensor));
        assertEquals(tensor, decodedTensor);
    }

}

