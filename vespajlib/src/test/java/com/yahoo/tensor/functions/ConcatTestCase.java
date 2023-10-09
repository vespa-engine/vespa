// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

    @Test
    public void testAdvancedMixed() {
        Tensor a = Tensor.from("tensor(a[2],b[2],c{},d[2],e{}):{"+
                               "{a:0,b:0,c:17,d:0,e:42}:1.0,"+
                               "{a:0,b:0,c:17,d:1,e:42}:2.0,"+
                               "{a:0,b:1,c:17,d:0,e:42}:3.0,"+
                               "{a:0,b:1,c:17,d:1,e:42}:4.0,"+
                               "{a:1,b:0,c:17,d:0,e:42}:5.0,"+
                               "{a:1,b:0,c:17,d:1,e:42}:6.0,"+
                               "{a:1,b:1,c:17,d:0,e:42}:7.0,"+
                               "{a:1,b:1,c:17,d:1,e:42}:8.0}");
        Tensor b = Tensor.from("tensor(a[2],b[2],c{},f[2],g{}):{"+
                               "{a:0,b:0,c:17,f:0,g:666}:51.0,"+
                               "{a:0,b:0,c:17,f:1,g:666}:52.0,"+
                               "{a:0,b:1,c:17,f:0,g:666}:53.0,"+
                               "{a:0,b:1,c:17,f:1,g:666}:54.0,"+
                               "{a:1,b:0,c:17,f:0,g:666}:55.0,"+
                               "{a:1,b:0,c:17,f:1,g:666}:56.0,"+
                               "{a:1,b:1,c:17,f:0,g:666}:57.0,"+
                               "{a:1,b:1,c:17,f:1,g:666}:58.0}");

        assertConcat("tensor(a[4],b[2],c{},d[2],e{},f[2],g{})",
                     "tensor(a[4],b[2],c{},d[2],e{},f[2],g{}):{"+
                     "{a:0,b:0,c:17,d:0,e:42,f:0,g:666}:1.0,"+
                     "{a:0,b:0,c:17,d:0,e:42,f:1,g:666}:1.0,"+
                     "{a:0,b:0,c:17,d:1,e:42,f:0,g:666}:2.0,"+
                     "{a:0,b:0,c:17,d:1,e:42,f:1,g:666}:2.0,"+
                     "{a:0,b:1,c:17,d:0,e:42,f:0,g:666}:3.0,"+
                     "{a:0,b:1,c:17,d:0,e:42,f:1,g:666}:3.0,"+
                     "{a:0,b:1,c:17,d:1,e:42,f:0,g:666}:4.0,"+
                     "{a:0,b:1,c:17,d:1,e:42,f:1,g:666}:4.0,"+
                     "{a:1,b:0,c:17,d:0,e:42,f:0,g:666}:5.0,"+
                     "{a:1,b:0,c:17,d:0,e:42,f:1,g:666}:5.0,"+
                     "{a:1,b:0,c:17,d:1,e:42,f:0,g:666}:6.0,"+
                     "{a:1,b:0,c:17,d:1,e:42,f:1,g:666}:6.0,"+
                     "{a:1,b:1,c:17,d:0,e:42,f:0,g:666}:7.0,"+
                     "{a:1,b:1,c:17,d:0,e:42,f:1,g:666}:7.0,"+
                     "{a:1,b:1,c:17,d:1,e:42,f:0,g:666}:8.0,"+
                     "{a:1,b:1,c:17,d:1,e:42,f:1,g:666}:8.0,"+
                     "{a:2,b:0,c:17,d:0,e:42,f:0,g:666}:51.0,"+
                     "{a:2,b:0,c:17,d:0,e:42,f:1,g:666}:52.0,"+
                     "{a:2,b:0,c:17,d:1,e:42,f:0,g:666}:51.0,"+
                     "{a:2,b:0,c:17,d:1,e:42,f:1,g:666}:52.0,"+
                     "{a:2,b:1,c:17,d:0,e:42,f:0,g:666}:53.0,"+
                     "{a:2,b:1,c:17,d:0,e:42,f:1,g:666}:54.0,"+
                     "{a:2,b:1,c:17,d:1,e:42,f:0,g:666}:53.0,"+
                     "{a:2,b:1,c:17,d:1,e:42,f:1,g:666}:54.0,"+
                     "{a:3,b:0,c:17,d:0,e:42,f:0,g:666}:55.0,"+
                     "{a:3,b:0,c:17,d:0,e:42,f:1,g:666}:56.0,"+
                     "{a:3,b:0,c:17,d:1,e:42,f:0,g:666}:55.0,"+
                     "{a:3,b:0,c:17,d:1,e:42,f:1,g:666}:56.0,"+
                     "{a:3,b:1,c:17,d:0,e:42,f:0,g:666}:57.0,"+
                     "{a:3,b:1,c:17,d:0,e:42,f:1,g:666}:58.0,"+
                     "{a:3,b:1,c:17,d:1,e:42,f:0,g:666}:57.0,"+
                     "{a:3,b:1,c:17,d:1,e:42,f:1,g:666}:58.0}",
                     a, b, "a");

        assertConcat("tensor(a[2],b[2],c{},d[2],e{},f[2],g{},x[2])",
                     "tensor(a[2],b[2],c{},d[2],e{},f[2],g{},x[2]):{"+
                     "{a:0,b:0,c:17,d:0,e:42,f:0,g:666,x:0}:1.0,"+
                     "{a:0,b:0,c:17,d:0,e:42,f:1,g:666,x:0}:1.0,"+
                     "{a:0,b:0,c:17,d:1,e:42,f:0,g:666,x:0}:2.0,"+
                     "{a:0,b:0,c:17,d:1,e:42,f:1,g:666,x:0}:2.0,"+
                     "{a:0,b:1,c:17,d:0,e:42,f:0,g:666,x:0}:3.0,"+
                     "{a:0,b:1,c:17,d:0,e:42,f:1,g:666,x:0}:3.0,"+
                     "{a:0,b:1,c:17,d:1,e:42,f:0,g:666,x:0}:4.0,"+
                     "{a:0,b:1,c:17,d:1,e:42,f:1,g:666,x:0}:4.0,"+
                     "{a:1,b:0,c:17,d:0,e:42,f:0,g:666,x:0}:5.0,"+
                     "{a:1,b:0,c:17,d:0,e:42,f:1,g:666,x:0}:5.0,"+
                     "{a:1,b:0,c:17,d:1,e:42,f:0,g:666,x:0}:6.0,"+
                     "{a:1,b:0,c:17,d:1,e:42,f:1,g:666,x:0}:6.0,"+
                     "{a:1,b:1,c:17,d:0,e:42,f:0,g:666,x:0}:7.0,"+
                     "{a:1,b:1,c:17,d:0,e:42,f:1,g:666,x:0}:7.0,"+
                     "{a:1,b:1,c:17,d:1,e:42,f:0,g:666,x:0}:8.0,"+
                     "{a:1,b:1,c:17,d:1,e:42,f:1,g:666,x:0}:8.0,"+
                     "{a:0,b:0,c:17,d:0,e:42,f:0,g:666,x:1}:51.0,"+
                     "{a:0,b:0,c:17,d:0,e:42,f:1,g:666,x:1}:52.0,"+
                     "{a:0,b:0,c:17,d:1,e:42,f:0,g:666,x:1}:51.0,"+
                     "{a:0,b:0,c:17,d:1,e:42,f:1,g:666,x:1}:52.0,"+
                     "{a:0,b:1,c:17,d:0,e:42,f:0,g:666,x:1}:53.0,"+
                     "{a:0,b:1,c:17,d:0,e:42,f:1,g:666,x:1}:54.0,"+
                     "{a:0,b:1,c:17,d:1,e:42,f:0,g:666,x:1}:53.0,"+
                     "{a:0,b:1,c:17,d:1,e:42,f:1,g:666,x:1}:54.0,"+
                     "{a:1,b:0,c:17,d:0,e:42,f:0,g:666,x:1}:55.0,"+
                     "{a:1,b:0,c:17,d:0,e:42,f:1,g:666,x:1}:56.0,"+
                     "{a:1,b:0,c:17,d:1,e:42,f:0,g:666,x:1}:55.0,"+
                     "{a:1,b:0,c:17,d:1,e:42,f:1,g:666,x:1}:56.0,"+
                     "{a:1,b:1,c:17,d:0,e:42,f:0,g:666,x:1}:57.0,"+
                     "{a:1,b:1,c:17,d:0,e:42,f:1,g:666,x:1}:58.0,"+
                     "{a:1,b:1,c:17,d:1,e:42,f:0,g:666,x:1}:57.0,"+
                     "{a:1,b:1,c:17,d:1,e:42,f:1,g:666,x:1}:58.0}",
                     a, b, "x");

        assertConcat("tensor(a[2],b[2],c{},d[3],e{},f[2],g{})",
                     "tensor(a[2],b[2],c{},d[3],e{},f[2],g{}):{"+
                     "{a:0,b:0,c:17,d:0,e:42,f:0,g:666}:1.0,"+
                     "{a:0,b:0,c:17,d:0,e:42,f:1,g:666}:1.0,"+
                     "{a:0,b:0,c:17,d:1,e:42,f:0,g:666}:2.0,"+
                     "{a:0,b:0,c:17,d:1,e:42,f:1,g:666}:2.0,"+
                     "{a:0,b:1,c:17,d:0,e:42,f:0,g:666}:3.0,"+
                     "{a:0,b:1,c:17,d:0,e:42,f:1,g:666}:3.0,"+
                     "{a:0,b:1,c:17,d:1,e:42,f:0,g:666}:4.0,"+
                     "{a:0,b:1,c:17,d:1,e:42,f:1,g:666}:4.0,"+
                     "{a:1,b:0,c:17,d:0,e:42,f:0,g:666}:5.0,"+
                     "{a:1,b:0,c:17,d:0,e:42,f:1,g:666}:5.0,"+
                     "{a:1,b:0,c:17,d:1,e:42,f:0,g:666}:6.0,"+
                     "{a:1,b:0,c:17,d:1,e:42,f:1,g:666}:6.0,"+
                     "{a:1,b:1,c:17,d:0,e:42,f:0,g:666}:7.0,"+
                     "{a:1,b:1,c:17,d:0,e:42,f:1,g:666}:7.0,"+
                     "{a:1,b:1,c:17,d:1,e:42,f:0,g:666}:8.0,"+
                     "{a:1,b:1,c:17,d:1,e:42,f:1,g:666}:8.0,"+
                     "{a:0,b:0,c:17,d:2,e:42,f:0,g:666}:51.0,"+
                     "{a:0,b:0,c:17,d:2,e:42,f:1,g:666}:52.0,"+
                     "{a:0,b:1,c:17,d:2,e:42,f:0,g:666}:53.0,"+
                     "{a:0,b:1,c:17,d:2,e:42,f:1,g:666}:54.0,"+
                     "{a:1,b:0,c:17,d:2,e:42,f:0,g:666}:55.0,"+
                     "{a:1,b:0,c:17,d:2,e:42,f:1,g:666}:56.0,"+
                     "{a:1,b:1,c:17,d:2,e:42,f:0,g:666}:57.0,"+
                     "{a:1,b:1,c:17,d:2,e:42,f:1,g:666}:58.0}",
                     a, b, "d");
    }

    @Test
    public void testWithEmptyMixed() {
        Tensor a = Tensor.from("tensor(a[2],c{},d[2]):{"+
                               "{a:0,c:17,d:0}:1.0,"+
                               "{a:0,c:17,d:1}:2.0,"+
                               "{a:1,c:17,d:0}:3.0,"+
                               "{a:1,c:17,d:1}:4.0}");
        Tensor b = Tensor.from("tensor(b{}):{}");
        Tensor c = Tensor.from("tensor(c{}):{}");
        Tensor d = Tensor.from("tensor(c{},d[3]):{}");
        
        assertConcat("tensor(a[3],b{},c{},d[2])", "tensor(a[3],b{},c{},d[2]):{}",
                     a, b, "a");
        assertConcat("tensor(a[2],b{},c{},d[2],x[2])", "tensor(a[2],b{},c{},d[2],x[2]):{}",
                     a, b, "x");

        assertConcat("tensor(a[3],c{},d[2])", "tensor(a[3],c{},d[2]):{}",
                     a, c, "a");
        assertConcat("tensor(a[2],c{},d[2],x[2])", "tensor(a[2],c{},d[2],x[2]):{}",
                     a, c, "x");

        assertConcat("tensor(a[2],c{},d[5])", "tensor(a[2],c{},d[5]):{}",
                     a, d, "d");
    }

    private void assertConcat(String expected, Tensor a, Tensor b, String dimension) {
        assertConcat(null, expected, a, b, dimension);
    }

    private void assertConcat(String expectedType, String expected, Tensor a, Tensor b, String dimension) {
        Tensor expectedAsTensor = Tensor.from(expected);
        TensorType inferredType = new Concat<>(new ConstantTensor<>(a), new ConstantTensor<>(b), dimension)
                                          .type(new MapEvaluationContext<>());
        Tensor result = a.concat(b, dimension);

        if (expectedType != null)
            assertEquals(TensorType.fromSpec(expectedType), inferredType);
        else
            assertEquals(expectedAsTensor.type(), inferredType);

        assertEquals(expectedAsTensor, result);
    }

}
