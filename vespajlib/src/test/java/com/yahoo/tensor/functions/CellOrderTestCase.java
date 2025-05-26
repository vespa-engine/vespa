// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.functions;

import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.Name;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author arnej
 */
public class CellOrderTestCase {

    @Test
    public void testCellOrderAsString() {
        var in = new ConstantTensor<Name>(Tensor.from("tensor(x[3]):[1,2,3]"));
        var op_max = new CellOrder<Name>(in, CellOrder.Order.MAX);
        var op_min = new CellOrder<Name>(in, CellOrder.Order.MIN);
        assertEquals("cell_order(tensor(x[3]):[1.0, 2.0, 3.0], max)", op_max.toString());
        assertEquals("cell_order(tensor(x[3]):[1.0, 2.0, 3.0], min)", op_min.toString());
    }

    @Test
    public void testCellOrderMax() {
        var input = new ConstantTensor<Name>(Tensor.from("tensor(x[5]):[1.0, 3.0, 2.0, 5.0, 4.0]"));
        var op = new CellOrder<Name>(input, CellOrder.Order.MAX);
        var result = op.evaluate(null);
        var expected = Tensor.from("tensor(x[5]):[4, 2, 3, 0, 1]");
        assertEquals(expected, result);
    }

    @Test
    public void testCellOrderMin() {
        var input = new ConstantTensor<Name>(Tensor.from("tensor(x[5]):[1.0, 3.0, 2.0, 5.0, 4.0]"));
        var op = new CellOrder<Name>(input, CellOrder.Order.MIN);
        var result = op.evaluate(null);
        var expected = Tensor.from("tensor(x[5]):[0, 2, 1, 4, 3]");
        assertEquals(expected, result);
    }

    @Test
    public void testCellOrderOutputValueType() {
        var in_int8 = Tensor.from("tensor<int8>(x[3]):[1,2,3]");
        var in_bfloat16 = Tensor.from("tensor<bfloat16>(x[3]):[1,2,3]");
        var in_float = Tensor.from("tensor<float>(x[3]):[1,2,3]");
        var in_double = Tensor.from("tensor<double>(x[3]):[1,2,3]");
        assertEquals(CellOrder.outputType(in_int8.type()).valueType(), TensorType.Value.FLOAT);
        assertEquals(CellOrder.outputType(in_bfloat16.type()).valueType(), TensorType.Value.FLOAT);
        assertEquals(CellOrder.outputType(in_float.type()).valueType(), TensorType.Value.FLOAT);
        assertEquals(CellOrder.outputType(in_double.type()).valueType(), TensorType.Value.DOUBLE);
    }
    
    @Test
    public void testNanAwareCompare() {
        assertTrue(CellOrder.nanAwareCompare(1.0, 2.0) < 0);
        assertTrue(CellOrder.nanAwareCompare(2.0, 1.0) > 0);
        assertEquals(0, CellOrder.nanAwareCompare(1.0, 1.0));

        double nan = Double.NaN;
        assertTrue(CellOrder.nanAwareCompare(nan, 1.0) > 0);
        assertTrue(CellOrder.nanAwareCompare(1.0, nan) < 0);
        assertEquals(0, CellOrder.nanAwareCompare(nan, nan));
    }
}
