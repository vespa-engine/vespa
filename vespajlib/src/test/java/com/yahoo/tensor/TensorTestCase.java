package com.yahoo.tensor;

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
        MapTensor tensor1 = MapTensor.from("{ {x:1}:3, {x:2}:7 }");
        MapTensor tensor2 = MapTensor.from("{ {y:1}:5 }");
        assertEquals(MapTensor.from("{ {x:1,y:1}:15, {x:2,y:1}:35 }"), tensor1.multiply(tensor2));
        assertEquals(MapTensor.from("{ {x:1,y:1}:12, {x:2,y:1}:28 }"), tensor1.join(tensor2, (a, b) -> a * b - a ));
        assertEquals(MapTensor.from("{ {x:1,y:1}:0, {x:2,y:1}:1 }"), tensor1.larger(tensor2));
        assertEquals(MapTensor.from("{{y:1}:50.0}"), tensor1.matmul(tensor2, "x"));
    }

}
