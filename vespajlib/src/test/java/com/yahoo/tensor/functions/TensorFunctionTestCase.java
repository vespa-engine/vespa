package com.yahoo.tensor.functions;

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
        assertTranslated("join({{x:1}:1.0}, reduce({{x:1}:1.0}, avg, x), f(a,b)(a / b))",
                         new L1Normalize(new ConstantTensor("{{x:1}:1.0}"), "x"));
    }
    
    private void assertTranslated(String expectedTranslation, TensorFunction inputFunction) {
        assertEquals(expectedTranslation, inputFunction.toPrimitive().toString());
    }

}
