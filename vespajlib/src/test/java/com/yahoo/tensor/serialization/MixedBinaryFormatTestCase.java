// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.tensor.serialization;

import com.yahoo.io.GrowableByteBuffer;
import com.yahoo.tensor.MixedTensor;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import org.junit.Test;

import java.util.Arrays;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

/**
 * Tests for the mixed binary format.
 *
 * @author lesters
 */
public class MixedBinaryFormatTestCase {

    @Test
    public void testSerialization() {
        assertSerialization("tensor(x{},y[3]):{{x:1,y:0}:1.0,{x:1,y:1}:2.0,{x:1,y:2}:0.0,{x:2,y:0}:4.0,{x:2,y:1}:5.0,{x:2,y:2}:6.0}");
        assertSerialization("tensor(x{},y[]):{{x:1,y:0}:1.0,{x:1,y:1}:2.0,{x:1,y:2}:0.0,{x:2,y:0}:4.0,{x:2,y:1}:5.0,{x:2,y:2}:6.0}");

        assertSerialization("tensor(x{},y[3],z{}):{{x:x1,y:0,z:z1}:1.0,{x:x1,y:0,z:z2}:2.0,{x:x1,y:1,z:z1}:3.0,{x:x1,y:1,z:z2}:4.0,{x:x1,y:2,z:z1}:5.0,{x:x1,y:2,z:z2}:6.0,{x:x2,y:0,z:z1}:11.0,{x:x2,y:0,z:z2}:12.0,{x:x2,y:1,z:z1}:13.0,{x:x2,y:1,z:z2}:14.0,{x:x2,y:2,z:z1}:15.0,{x:x2,y:2,z:z2}:16.0}");
        assertSerialization("tensor(x{},y[],z{}):{{x:x1,y:0,z:z1}:1.0,{x:x1,y:0,z:z2}:2.0,{x:x1,y:1,z:z1}:3.0,{x:x1,y:1,z:z2}:4.0,{x:x1,y:2,z:z1}:5.0,{x:x1,y:2,z:z2}:6.0,{x:x2,y:0,z:z1}:11.0,{x:x2,y:0,z:z2}:12.0,{x:x2,y:1,z:z1}:13.0,{x:x2,y:1,z:z2}:14.0,{x:x2,y:2,z:z1}:15.0,{x:x2,y:2,z:z2}:16.0}");

        assertSerialization("tensor(i{},j[2],k{},l[2]):{{i:a,j:0,k:c,l:0}:1.0,{i:a,j:0,k:c,l:1}:2.0,{i:a,j:0,k:d,l:0}:5.0,{i:a,j:0,k:d,l:1}:6.0,{i:a,j:1,k:c,l:0}:3.0,{i:a,j:1,k:c,l:1}:4.0,{i:a,j:1,k:d,l:0}:7.0,{i:a,j:1,k:d,l:1}:8.0,{i:b,j:0,k:c,l:0}:9.0,{i:b,j:0,k:c,l:1}:10.0,{i:b,j:0,k:d,l:0}:13.0,{i:b,j:0,k:d,l:1}:14.0,{i:b,j:1,k:c,l:0}:11.0,{i:b,j:1,k:c,l:1}:12.0,{i:b,j:1,k:d,l:0}:15.0,{i:b,j:1,k:d,l:1}:16.0}");
        assertSerialization("tensor(i{},j[],k{},l[]):{{i:a,j:0,k:c,l:0}:1.0,{i:a,j:0,k:c,l:1}:2.0,{i:a,j:0,k:d,l:0}:5.0,{i:a,j:0,k:d,l:1}:6.0,{i:a,j:1,k:c,l:0}:3.0,{i:a,j:1,k:c,l:1}:4.0,{i:a,j:1,k:d,l:0}:7.0,{i:a,j:1,k:d,l:1}:8.0,{i:b,j:0,k:c,l:0}:9.0,{i:b,j:0,k:c,l:1}:10.0,{i:b,j:0,k:d,l:0}:13.0,{i:b,j:0,k:d,l:1}:14.0,{i:b,j:1,k:c,l:0}:11.0,{i:b,j:1,k:c,l:1}:12.0,{i:b,j:1,k:d,l:0}:15.0,{i:b,j:1,k:d,l:1}:16.0}");
    }

    @Test
    public void testOneIndexedSerialization() {
        TensorType type = new TensorType.Builder().indexed("y", 3).build();
        Tensor tensor = MixedTensor.Builder.of(type).
                cell().label("y", 0).value(1).
                cell().label("y", 1).value(2).
                build();
        assertSerialization(tensor);
    }

    @Test
    public void testTwoIndexedSerialization() {
        TensorType type = new TensorType.Builder().indexed("x").indexed("y", 3).build();
        Tensor tensor = MixedTensor.Builder.of(type).
                cell().label("x", 0).label("y", 0).value(1).
                cell().label("x", 0).label("y", 1).value(2).
                cell().label("x", 1).label("y", 0).value(4).
                cell().label("x", 1).label("y", 1).value(5).
                cell().label("x", 1).label("y", 2).value(6).
                build();
        assertSerialization(tensor);
    }

    @Test
    public void testOneMappedSerialization() {
        TensorType type = new TensorType.Builder().mapped("x").build();
        Tensor tensor = MixedTensor.Builder.of(type).
                cell().label("x", "0").value(1).
                cell().label("x", "1").value(2).
                build();
        assertSerialization(tensor);
    }

    @Test
    public void testTwoMappedSerialization() {
        TensorType type = new TensorType.Builder().mapped("x").mapped("y").build();
        Tensor tensor = MixedTensor.Builder.of(type).
                cell().label("x", "0").label("y", "0").value(1).
                cell().label("x", "0").label("y", "1").value(2).
                cell().label("x", "1").label("y", "0").value(4).
                cell().label("x", "1").label("y", "1").value(5).
                cell().label("x", "1").label("y", "2").value(6).
                build();
        assertSerialization(tensor);
    }

    @Test
    public void requireThatDefaultSerializationFormatDoesNotChange() {
        byte[] encodedTensor = new byte[] {3, // binary format type
                1, // number of sparse dimensions
                2, (byte)'x', (byte)'y',  // name of sparse dimension
                1, // number of dense dimensions
                1, (byte)'z', 1, // name and size of dense dimension
                2, // num cells,
                2, (byte)'a', (byte)'b', 64, 0, 0, 0, 0, 0, 0, 0, // cell 0
                2, (byte)'c', (byte)'d', 64, 8, 0, 0, 0, 0, 0, 0}; // cell 1
        Tensor tensor = Tensor.from("tensor(xy{},z[1]):{{xy:ab,z:0}:2.0,{xy:cd,z:0}:3.0}");
        assertEquals(Arrays.toString(encodedTensor), Arrays.toString(TypedBinaryFormat.encode(tensor)));
    }

    @Test
    public void requireThatFloatSerializationFormatDoesNotChange() {
        byte[] encodedTensor = new byte[] {7, // binary format type
                1, // float type
                1, // number of sparse dimensions
                2, (byte)'x', (byte)'y',  // name of sparse dimension
                1, // number of dense dimensions
                1, (byte)'z', 1, // name and size of dense dimension
                2, // num cells,
                2, (byte)'a', (byte)'b', 64, 0, 0, 0,  // cell 0
                2, (byte)'c', (byte)'d', 64, 64, 0, 0}; // cell 1
        Tensor tensor = Tensor.from("tensor<float>(xy{},z[1]):{{xy:ab,z:0}:2.0,{xy:cd,z:0}:3.0}");
        assertEquals(Arrays.toString(encodedTensor), Arrays.toString(TypedBinaryFormat.encode(tensor)));
    }

    @Test
    public void requireThatBFloat16SerializationFormatDoesNotChange() {
        byte[] encodedTensor = new byte[] {7, // binary format type
                2, // bfloat16 type
                1, // number of sparse dimensions
                2, (byte)'x', (byte)'y',  // name of sparse dimension
                1, // number of dense dimensions
                1, (byte)'z', 1, // name and size of dense dimension
                2, // num cells,
                2, (byte)'a', (byte)'b', 64, 0,  // cell 0
                2, (byte)'c', (byte)'d', 64, 64}; // cell 1
        Tensor tensor = Tensor.from("tensor<bfloat16>(xy{},z[1]):{{xy:ab,z:0}:2.0,{xy:cd,z:0}:3.0}");
        assertEquals(Arrays.toString(encodedTensor), Arrays.toString(TypedBinaryFormat.encode(tensor)));
    }

    @Test
    public void requireThatInt8SerializationFormatDoesNotChange() {
        byte[] encodedTensor = new byte[] {7, // binary format type
                3, // int8 type
                1, // number of sparse dimensions
                2, (byte)'x', (byte)'y',  // name of sparse dimension
                1, // number of dense dimensions
                1, (byte)'z', 1, // name and size of dense dimension
                2, // num cells,
                2, (byte)'a', (byte)'b', 2,  // cell 0
                2, (byte)'c', (byte)'d', 3}; // cell 1
        Tensor tensor = Tensor.from("tensor<int8>(xy{},z[1]):{{xy:ab,z:0}:2.0,{xy:cd,z:0}:3.0}");
        assertEquals(Arrays.toString(encodedTensor), Arrays.toString(TypedBinaryFormat.encode(tensor)));
    }

    @Test
    public void testSerializationOfDifferentValueTypes() {
        assertSerialization("tensor<double>(x{},y[2]):{{x:0,y:0}:2.0, {x:0,y:1}:3.0, {x:1,y:0}:4.0, {x:1,y:1}:5.0}");
        assertSerialization("tensor<float>(x{},y[2]):{{x:0,y:0}:2.0, {x:0,y:1}:3.0, {x:1,y:0}:4.0, {x:1,y:1}:5.0}");
        assertSerialization("tensor<bfloat16>(x{},y[2]):{{x:0,y:0}:2.0, {x:0,y:1}:3.0, {x:1,y:0}:4.0, {x:1,y:1}:5.0}");
        assertSerialization("tensor<int8>(x{},y[2]):{{x:0,y:0}:2, {x:0,y:1}:3, {x:1,y:0}:4, {x:1,y:1}:5}");
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

