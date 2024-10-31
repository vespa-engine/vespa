package com.yahoo.tensor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

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

    @Test
    void testPackBits() {
        assertPacked("tensor<int8>(x[2]):[129,14]", "tensor(x[16]):[1,0,0,0,0,0,0,1, 0,0,0,0,1,1,1,0]");
        assertPacked("tensor<int8>(x[2]):[129,14]", "tensor(x[15]):[1,0,0,0,0,0,0,1, 0,0,0,0,1,1,1]");
        assertPacked("tensor<int8>(x[1]):[128]",    "tensor(x[1]):[1]");
        assertPacked("tensor<int8>(key{},x[2]):{a:[129,14], b:[12, 7]}",
                         "tensor(key{},x[16]):{a:[1,0,0,0,0,0,0,1, 0,0,0,0,1,1,1,0]," +
                               "                     b:[0,0,0,0,1,1,0,0, 0,0,0,0,0,1,1,1]}");
        assertPacked("tensor<int8>(key{},x[1]):{a:[160],b:[32]}",
                     "tensor(key{},x[3]):{a:[1,0,1],b:[0,0,1]}");
        assertPacked("tensor<int8>(key{},x[1]):{a:[128]}",    "tensor(key{}, x[1]):{a:[1]}");

        try {
            Tensors.packBits(Tensor.from("tensor(x[1],y[1]):[1]"));
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("packBits requires a tensor with one dense dimensions, but got tensor(x[1],y[1])",
                         e.getMessage());
        }
        try {
            Tensors.packBits(Tensor.from("tensor(x[3]):[0, 1, 2]"));
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("The tensor to be packed can only contain 0 or 1 values, but has 2.0 at position 2",
                         e.getMessage());
        }
    }

    void assertConvertedToSparse(String inputType, String outputType, String tensorValue, String ... dimensions) {
        var tensor = Tensor.from(inputType + ":" + tensorValue);
        assertEquals(outputType + ":" + tensorValue, Tensors.toSparse(tensor, dimensions).toString(true, false));
    }

    void assertPacked(String expected, String input) {
        assertEquals(Tensor.from(expected), Tensors.packBits(Tensor.from(input)));
    }

}
