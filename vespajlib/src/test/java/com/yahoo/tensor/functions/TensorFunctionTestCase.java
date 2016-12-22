package com.yahoo.tensor.functions;

import com.yahoo.tensor.TensorType;
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
        assertTranslated("join({{x:1}:1.0}, reduce({{x:1}:1.0}, sum, x), f(a,b)(a / b))",
                         new L1Normalize(new ConstantTensor("{{x:1}:1.0}"), "x"));
        assertTranslated("tensor(x[2],y[3],z[4])((x==y)*(y==z))", 
                         new Diag(new TensorType.Builder().indexed("y",3).indexed("x",2).indexed("z",4).build()));
    }
    
    private void assertTranslated(String expectedTranslation, TensorFunction inputFunction) {
        assertEquals(expectedTranslation, inputFunction.toPrimitive().toString());
    }

}
