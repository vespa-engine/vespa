// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.functions;

import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author lesters
 */
public class CellCastTestCase {

    @Test
    public void testCellCasting() {
        Tensor tensor;

        tensor = Tensor.from("tensor(x[3]):[1.0, 2.0, 3.0]");
        assertEquals(TensorType.Value.DOUBLE, tensor.type().valueType());
        assertEquals(TensorType.Value.DOUBLE, tensor.cellCast(TensorType.Value.DOUBLE).type().valueType());
        assertEquals(TensorType.Value.FLOAT, tensor.cellCast(TensorType.Value.FLOAT).type().valueType());
        assertEquals(tensor, tensor.cellCast(TensorType.Value.FLOAT));

        tensor = Tensor.from("tensor<double>(x{}):{{x:0}:1.0,{x:1}:2.0,{x:2}:3.0}");
        assertEquals(TensorType.Value.DOUBLE, tensor.type().valueType());
        assertEquals(TensorType.Value.DOUBLE, tensor.cellCast(TensorType.Value.DOUBLE).type().valueType());
        assertEquals(TensorType.Value.FLOAT, tensor.cellCast(TensorType.Value.FLOAT).type().valueType());
        assertEquals(tensor, tensor.cellCast(TensorType.Value.FLOAT));

        tensor = Tensor.from("tensor<float>(x[3],y{}):{a:[1.0, 2.0, 3.0],b:[4.0,5.0,6.0]}");
        assertEquals(TensorType.Value.FLOAT, tensor.type().valueType());
        assertEquals(TensorType.Value.DOUBLE, tensor.cellCast(TensorType.Value.DOUBLE).type().valueType());
        assertEquals(TensorType.Value.FLOAT, tensor.cellCast(TensorType.Value.FLOAT).type().valueType());
        assertEquals(tensor, tensor.cellCast(TensorType.Value.DOUBLE));

        tensor         = Tensor.from("tensor<double>(x{}):{{x:0}:2.25,{x:1}:1.00000000001,{x:2}:256.0,{x:3}:1.00390625}");
        var asFloat    = Tensor.from("tensor<float>(x{}):{{x:0}:2.25,{x:1}:1.0,{x:2}:256.0,{x:3}:1.00390625}");
        var asBFloat16 = Tensor.from("tensor<bfloat16>(x{}):{{x:0}:2.25,{x:1}:1.0,{x:2}:256.0,{x:3}:1.0}");
        var asInt8     = Tensor.from("tensor<int8>(x{}):{{x:0}:2,{x:1}:1,{x:2}:0,{x:3}:1}");
        assertEquals(asFloat,    tensor.cellCast(TensorType.Value.FLOAT));
        assertEquals(asBFloat16, tensor.cellCast(TensorType.Value.BFLOAT16));
        assertEquals(asInt8,     tensor.cellCast(TensorType.Value.INT8));
        assertEquals(asBFloat16, asFloat.cellCast(TensorType.Value.BFLOAT16));
        assertEquals(asInt8,     asFloat.cellCast(TensorType.Value.INT8));
        assertEquals(asInt8,     asBFloat16.cellCast(TensorType.Value.INT8));
    }

}
