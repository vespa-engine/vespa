// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.serialization;

import com.google.common.collect.Sets;
import com.yahoo.tensor.Tensor;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Arrays;
import java.util.Set;

import static org.junit.Assert.assertEquals;

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
    @Ignore // TODO: Activate when encoding in this format is activated
    public void requireThatSerializationFormatDoNotChange() {
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

    private void assertSerialization(String tensorString) {
        assertSerialization(Tensor.from(tensorString));
    }

    private void assertSerialization(Tensor tensor) {
        byte[] encodedTensor = TypedBinaryFormat.encode(tensor);
        Tensor decodedTensor = TypedBinaryFormat.decode(tensor.type(), encodedTensor);
        assertEquals(tensor, decodedTensor);
    }

}

