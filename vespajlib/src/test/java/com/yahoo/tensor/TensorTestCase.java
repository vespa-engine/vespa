package com.yahoo.tensor;

import com.google.common.collect.ImmutableList;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * Tests functionality on Tensor
 * 
 * @author bratseth
 */
public class TensorTestCase {

    /** This is mostly tested in searchlib - spot checking here */
    @Test
    public void testTensorComputation() {
        Tensor tensor1 = Tensor.from("{ {x:1}:3, {x:2}:7 }");
        Tensor tensor2 = Tensor.from("{ {y:1}:5 }");
        assertEquals(Tensor.from("{ {x:1,y:1}:15, {x:2,y:1}:35 }"), tensor1.multiply(tensor2));
        assertEquals(Tensor.from("{ {x:1,y:1}:12, {x:2,y:1}:28 }"), tensor1.join(tensor2, (a, b) -> a * b - a ));
        assertEquals(Tensor.from("{ {x:1,y:1}:0, {x:2,y:1}:1 }"), tensor1.larger(tensor2));
        assertEquals(Tensor.from("{ {y:1}:50.0 }"), tensor1.matmul(tensor2, "x"));
        assertEquals(Tensor.from("{ {z:1}:3, {z:2}:7 }"), tensor1.rename("x", "z"));
        assertEquals(Tensor.from("{ {y:1,x:1}:8, {x:1,y:2}:12 }"), tensor1.add(tensor2).rename(ImmutableList.of("x", "y"),
                                                                                               ImmutableList.of("y", "x")));
    }

}
