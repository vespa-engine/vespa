// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.functions;

import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.MapEvaluationContext;
import com.yahoo.tensor.evaluation.TypeContext;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author bratseth
 */
public class ConcatTestCase {

    @Test
    public void testConcatNumbers() {
        Tensor a = Tensor.from("{1}");
        Tensor b = Tensor.from("{2}");
        assertConcat("tensor(x[2]):{ {x:0}:1, {x:1}:2 }", a, b, "x");
        assertConcat("tensor(x[2]):{ {x:0}:2, {x:1}:1 }", b, a , "x");
    }

    @Test
    public void testConcatEqualShapes() {
        Tensor a = Tensor.from("tensor(x[3]):{ {x:0}:1, {x:1}:2, {x:2}:3 }");
        Tensor b = Tensor.from("tensor(x[3]):{ {x:0}:4, {x:1}:5, {x:2}:6 }");
        assertConcat("tensor(x[6]):{ {x:0}:1, {x:1}:2, {x:2}:3, {x:3}:4, {x:4}:5, {x:5}:6 }", a, b, "x");
        assertConcat("tensor(x[3],y[2]):{ {x:0,y:0}:1, {x:1,y:0}:2, {x:2,y:0}:3, " +
                                                   "{x:0,y:1}:4, {x:1,y:1}:5, {x:2,y:1}:6  }",
                     a, b, "y");
    }

    @Test
    public void testConcatNumberAndVector() {
        Tensor a = Tensor.from("{1}");
        Tensor b = Tensor.from("tensor(x[3]):{ {x:0}:2, {x:1}:3, {x:2}:4 }");
        assertConcat("tensor(x[4]):{ {x:0}:1, {x:1}:2, {x:2}:3, {x:3}:4 }", a, b, "x");
        assertConcat("tensor(x[3],y[2]):{ {x:0,y:0}:1, {x:1,y:0}:1, {x:2,y:0}:1, " +
                                                  "{x:0,y:1}:2, {x:1,y:1}:3, {x:2,y:1}:4  }",
                     a, b, "y");
    }

    @Test
    public void testConcatNumberAndVectorUnbound() {
        Tensor a = Tensor.from("{1}");
        Tensor b = Tensor.from("tensor(x[]):{ {x:0}:2, {x:1}:3, {x:2}:4 }");
        assertConcat("tensor(x[])","tensor(x[4]):{ {x:0}:1, {x:1}:2, {x:2}:3, {x:3}:4 }", a, b, "x");
        assertConcat("tensor(x[],y[2])", "tensor(x[3],y[2]):{ {x:0,y:0}:1, {x:1,y:0}:1, {x:2,y:0}:1, " +
                     "{x:0,y:1}:2, {x:1,y:1}:3, {x:2,y:1}:4  }",
                     a, b, "y");
    }

    @Test
    public void testUnequalSizesSameDimension() {
        Tensor a = Tensor.from("tensor(x[2]):{ {x:0}:1, {x:1}:2 }");
        Tensor b = Tensor.from("tensor(x[3]):{ {x:0}:4, {x:1}:5, {x:2}:6 }");
        assertConcat("tensor(x[5]):{ {x:0}:1, {x:1}:2, {x:2}:4, {x:3}:5, {x:4}:6 }", a, b, "x");
        assertConcat("tensor(x[2],y[2]):{ {x:0,y:0}:1, {x:1,y:0}:2, {x:0,y:1}:4, {x:1,y:1}:5 }", a, b,"y");
    }

    @Test
    public void testUnequalSizesSameDimensionUnbound() {
        Tensor a = Tensor.from("tensor(x[]):{ {x:0}:1, {x:1}:2 }");
        Tensor b = Tensor.from("tensor(x[]):{ {x:0}:4, {x:1}:5, {x:2}:6 }");
        assertConcat("tensor(x[])", "tensor(x[5]):{ {x:0}:1, {x:1}:2, {x:2}:4, {x:3}:5, {x:4}:6 }", a, b, "x");
        assertConcat("tensor(x[],y[2])", "tensor(x[2],y[2]):{ {x:0,y:0}:1, {x:1,y:0}:2, {x:0,y:1}:4, {x:1,y:1}:5 }", a, b,"y");
    }

    @Test
    public void testUnequalEqualSizesDifferentDimension() {
        Tensor a = Tensor.from("tensor(x[2]):{ {x:0}:1, {x:1}:2 }");
        Tensor b = Tensor.from("tensor(y[3]):{ {y:0}:4, {y:1}:5, {y:2}:6 }");
        assertConcat("tensor(x[3],y[3]):{{x:0,y:0}:1.0,{x:0,y:1}:1.0,{x:0,y:2}:1.0,{x:1,y:0}:2.0,{x:1,y:1}:2.0,{x:1,y:2}:2.0,{x:2,y:0}:4.0,{x:2,y:1}:5.0,{x:2,y:2}:6.0}", a, b, "x");
        assertConcat("tensor(x[2],y[4]):{{x:0,y:0}:1.0,{x:0,y:1}:4.0,{x:0,y:2}:5.0,{x:0,y:3}:6.0,{x:1,y:0}:2.0,{x:1,y:1}:4.0,{x:1,y:2}:5.0,{x:1,y:3}:6.0}", a, b, "y");
        assertConcat("tensor(x[2],y[3],z[2]):{{x:0,y:0,z:0}:1.0,{x:0,y:0,z:1}:4.0,{x:0,y:1,z:0}:1.0,{x:0,y:1,z:1}:5.0,{x:0,y:2,z:0}:1.0,{x:0,y:2,z:1}:6.0,{x:1,y:0,z:0}:2.0,{x:1,y:0,z:1}:4.0,{x:1,y:1,z:0}:2.0,{x:1,y:1,z:1}:5.0,{x:1,y:2,z:0}:2.0,{x:1,y:2,z:1}:6.0}", a, b, "z");
    }

    @Test
    public void testUnequalEqualSizesDifferentDimensionOneUnbound() {
        Tensor a = Tensor.from("tensor(x[]):{ {x:0}:1, {x:1}:2 }");
        Tensor b = Tensor.from("tensor(y[3]):{ {y:0}:4, {y:1}:5, {y:2}:6 }");
        assertConcat("tensor(x[],y[3])", "tensor(x[3],y[3]):{{x:0,y:0}:1.0,{x:0,y:1}:1.0,{x:0,y:2}:1.0,{x:1,y:0}:2.0,{x:1,y:1}:2.0,{x:1,y:2}:2.0,{x:2,y:0}:4.0,{x:2,y:1}:5.0,{x:2,y:2}:6.0}", a, b, "x");
        assertConcat("tensor(x[],y[4])", "tensor(x[2],y[4]):{{x:0,y:0}:1.0,{x:0,y:1}:4.0,{x:0,y:2}:5.0,{x:0,y:3}:6.0,{x:1,y:0}:2.0,{x:1,y:1}:4.0,{x:1,y:2}:5.0,{x:1,y:3}:6.0}", a, b, "y");
        assertConcat("tensor(x[],y[3],z[2])", "tensor(x[2],y[3],z[2]):{{x:0,y:0,z:0}:1.0,{x:0,y:0,z:1}:4.0,{x:0,y:1,z:0}:1.0,{x:0,y:1,z:1}:5.0,{x:0,y:2,z:0}:1.0,{x:0,y:2,z:1}:6.0,{x:1,y:0,z:0}:2.0,{x:1,y:0,z:1}:4.0,{x:1,y:1,z:0}:2.0,{x:1,y:1,z:1}:5.0,{x:1,y:2,z:0}:2.0,{x:1,y:2,z:1}:6.0}", a, b, "z");
    }

    @Test
    public void testDimensionsubset() {
        Tensor a = Tensor.from("tensor(x[],y[]):{ {x:0,y:0}:1, {x:1,y:0}:2, {x:0,y:1}:3, {x:1,y:1}:4 }");
        Tensor b = Tensor.from("tensor(y[2]):{ {y:0}:5, {y:1}:6 }");
        assertConcat("tensor(x[],y[])", "tensor(x[3],y[2]):{{x:0,y:0}:1.0,{x:0,y:1}:3.0,{x:1,y:0}:2.0,{x:1,y:1}:4.0,{x:2,y:0}:5.0,{x:2,y:1}:6.0}", a, b, "x");
        assertConcat("tensor(x[],y[])", "tensor(x[2],y[4]):{{x:0,y:0}:1.0,{x:0,y:1}:3.0,{x:0,y:2}:5.0,{x:0,y:3}:6.0,{x:1,y:0}:2.0,{x:1,y:1}:4.0,{x:1,y:2}:5.0,{x:1,y:3}:6.0}", a, b, "y");
    }

    private void assertConcat(String expected, Tensor a, Tensor b, String dimension) {
        assertConcat(null, expected, a, b, dimension);
    }

    private void assertConcat(String expectedType, String expected, Tensor a, Tensor b, String dimension) {
        Tensor expectedAsTensor = Tensor.from(expected);
        TensorType inferredType = new Concat(new ConstantTensor(a), new ConstantTensor(b), dimension)
                                           .type(new MapEvaluationContext());
        Tensor result = a.concat(b, dimension);

        if (expectedType != null)
            assertEquals(TensorType.fromSpec(expectedType), inferredType);
        else
            assertEquals(expectedAsTensor.type(), inferredType);

        assertEquals(expectedAsTensor, result);
    }

}
