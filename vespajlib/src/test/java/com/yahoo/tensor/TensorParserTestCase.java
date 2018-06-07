package com.yahoo.tensor;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class TensorParserTestCase {

    @Test
    public void testParsing() {
        assertEquals(Tensor.Builder.of(TensorType.fromSpec("tensor()")).build(),
                     Tensor.from("{}"));
        assertEquals(Tensor.Builder.of(TensorType.fromSpec("tensor(x{})")).cell(1.0, 0).build(),
                     Tensor.from("{{x:0}:1.0}"));
        assertEquals(Tensor.Builder.of(TensorType.fromSpec("tensor(x{})")).cell().label("x", "l0").value(1.0).build(),
                     Tensor.from("{{x:l0}:1.0}"));
    }

    @Test
    public void testIllegalStrings() {
        assertIllegal("label must be an identifier or integer, not '\"l0\"'",
                      "{{x:\"l0\"}:1.0}");
        assertIllegal("dimension must be an identifier or integer, not ''x''",
                      "{{'x':\"l0\"}:1.0}");
        assertIllegal("dimension must be an identifier or integer, not '\"x\"'",
                      "{{\"x\":\"l0\", \"y\":\"l0\"}:1.0, {\"x\":\"l0\", \"y\":\"l1\"}:2.0}");
    }

    private void assertIllegal(String message, String tensor) {
        try {
            Tensor.from(tensor);
            fail("Expected an IllegalArgumentException when parsing " + tensor);
        }
        catch (IllegalArgumentException e) {
            assertEquals(message, e.getMessage());
        }
    }

}
