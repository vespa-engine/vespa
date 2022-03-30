// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.functions;

import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.Name;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * Tests translation of composite to primitive tensor function translation.
 *
 * @author bratseth
 */
public class TensorFunctionTestCase {

    @Test
    public void testTranslation() {
        assertTranslated("join(tensor(x{}):{1:1.0}, reduce(tensor(x{}):{1:1.0}, sum, x), f(a,b)(a / b))",
                         new L1Normalize<>(new ConstantTensor<>("{{x:1}:1.0}"), "x"));
        assertTranslated("tensor(x[2],y[3],z[4])((x==y)*(y==z))",
                         new Diag<>(new TensorType.Builder().indexed("y",3).indexed("x",2).indexed("z",4).build()));
        assertTranslated("join(tensor(x{}):{1:1.0, 3:5.0, 9:3.0}, reduce(tensor(x{}):{1:1.0, 3:5.0, 9:3.0}, max, x), f(a,b)(a==b))",
                         new Argmax<>(new ConstantTensor<>("{ {x:1}:1, {x:3}:5, {x:9}:3 }"), "x"));
        assertTranslated("join(tensor(x{}):{1:1.0, 3:5.0, 9:3.0}, reduce(tensor(x{}):{1:1.0, 3:5.0, 9:3.0}, max), f(a,b)(a==b))",
                        new Argmax<>(new ConstantTensor<>("{ {x:1}:1, {x:3}:5, {x:9}:3 }")));
    }

    private void assertTranslated(String expectedTranslation, TensorFunction<Name> inputFunction) {
        assertEquals(expectedTranslation, inputFunction.toPrimitive().toString());
    }

}
