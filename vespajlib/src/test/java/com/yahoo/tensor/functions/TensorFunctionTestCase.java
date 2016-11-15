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
        assertTranslated("join({{x:1}:1.0}, {{x:2}:1.0}, lambda(a, b) (...))", 
                         new Product(new ConstantTensor("{{x:1}:1.0}"), new ConstantTensor("{{x:2}:1.0}")));
    }
    
    private void assertTranslated(String expectedTranslation, TensorFunction inputFunction) {
        assertEquals(expectedTranslation, inputFunction.toPrimitive().toString());
    }

}
