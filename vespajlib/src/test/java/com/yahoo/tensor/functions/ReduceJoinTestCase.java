// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.functions;

import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.evaluation.Name;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author arnej
 */
public class ReduceJoinTestCase {

    @Test
    public void testReduceJoinTriggers() {
        var a = Tensor.from("tensor(x[3])", "[1,2,3]");
        var b = Tensor.from("tensor(x[3])", "[4,5,6]");
        var fa = new ConstantTensor<Name>(a);
        var fb = new ConstantTensor<Name>(a);
        var j = new Join<Name>(fa, fb, ScalarFunctions.add());
        var r = new Reduce<Name>(j, Reduce.Aggregator.sum, "x");
        var rj = new ReduceJoin<Name>(r, j);
        assertTrue(rj.canOptimize(a, b));
    }

    @Test
    public void testReduceJoinUnoptimized() {
        var a = Tensor.from("tensor(x[3])", "[1,2,3]");
        var b = Tensor.from("tensor(y[3])", "[4,5,6]");
        var fa = new ConstantTensor<Name>(a);
        var fb = new ConstantTensor<Name>(a);
        var j = new Join<Name>(fa, fb, ScalarFunctions.add());
        var r = new Reduce<Name>(j, Reduce.Aggregator.sum, "x");
        var rj = new ReduceJoin<Name>(r, j);
        assertFalse(rj.canOptimize(a, b));
    }
}
