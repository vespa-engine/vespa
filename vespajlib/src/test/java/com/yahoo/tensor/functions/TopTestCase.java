// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.functions;

import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.evaluation.VariableTensor;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * @author arnej
 */
public class TopTestCase {

    @Test
    public void testToString() {
        var n = new VariableTensor<>("n");
        var arg = new VariableTensor<>("myfeature");
        var op = new Top<>(n, arg);
        assertEquals("top(n, myfeature)", op.toString());
        var primitive = op.toPrimitive();
        assertEquals("join(" + (
                             "myfeature, " +
                             "filter_subspaces(" + (
                                     "join(" + (
                                             "cell_order(myfeature, max), " +
                                             "n, " +
                                             "f(a,b)(a < b)), ") +
                                     "f(s)(s)), ") +
                             "f(a,b)(a * b))"),
                     primitive.toString());
    }

    @Test
    public void testSimpleMapped() {
        var n = Tensor.from(3);
        var arg = Tensor.from("tensor(p{}):{a:7,b:-3,c:12,d:-11,e:-1}");
        var op = new Top<>(new ConstantTensor<>(n), new ConstantTensor<>(arg));
        Tensor result = op.evaluate();
        var expect = Tensor.from("tensor(p{}):{a:7,c:12,e:-1}");
        assertEquals(expect, result);
    }

    @Test
    public void testMixed() {
        var n = Tensor.from(3);
        var arg = Tensor.from("tensor(a{},b{},x[2]):{a:{b:[1,2],c:[2,4]},d:{e:[3,5],f:[2,1]}}");
        var op = new Top<>(new ConstantTensor<>(n), new ConstantTensor<>(arg));
        Tensor result = op.evaluate();
        System.err.println("RESULT: " + result);
        var expect = Tensor.from("tensor(a{},b{},x[2]):{a:{c:[0,4]},d:{e:[3,5]}}");
        assertEquals(expect, result);
    }
}
