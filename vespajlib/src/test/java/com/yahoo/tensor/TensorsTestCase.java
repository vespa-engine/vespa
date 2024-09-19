package com.yahoo.tensor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author bratseth
 */
public class TensorsTestCase {

    @Test
    void testToSparse() {
        var value = "{{x:0,y:0}:1.0, {x:0,y:1}:2.0, {x:1,y:0}:3.0, {x:1,y:1}:4.0}";
        assertConvertedToSparse("tensor(x[2],y[2])", "tensor(x{},y{})", value);
        assertConvertedToSparse("tensor(x[2],y[2])", "tensor(x{},y[2])", value, "x");
        assertConvertedToSparse("tensor(x[2],y[2])", "tensor(x[2],y{})", value, "y");
        assertConvertedToSparse("tensor(x{},y{})", "tensor(x{},y{})", value, "x", "y");
        try {
            assertConvertedToSparse("tensor(x[2],y[2])", "tensor(x{},y{})", value, "noneSuch");
        }
        catch (IllegalArgumentException e) {
            assertEquals("The tensor tensor(x[2],y[2]) is missing the specified dimension 'noneSuch'", e.getMessage());
        }
    }

    void assertConvertedToSparse(String inputType, String outputType, String tensorValue, String ... dimensions) {
        var tensor = Tensor.from(inputType + ":" + tensorValue);
        assertEquals(outputType + ":" + tensorValue, Tensors.toSparse(tensor, dimensions).toString(true, false));
    }

}
