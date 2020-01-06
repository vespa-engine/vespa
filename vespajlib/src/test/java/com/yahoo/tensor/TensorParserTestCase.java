// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class TensorParserTestCase {

    @Test
    public void testEmpty() {
        assertEquals(Tensor.Builder.of(TensorType.empty).cell(1).build(), Tensor.from("tensor():{{}:1}"));
    }

    @Test
    public void testSparseParsing() {
        assertEquals(Tensor.Builder.of(TensorType.fromSpec("tensor()")).build(),
                     Tensor.from("{}"));
        assertEquals(Tensor.Builder.of(TensorType.fromSpec("tensor(x{})")).cell(1.0, 0).build(),
                     Tensor.from("{{x:0}:1.0}"));
        assertEquals(Tensor.Builder.of(TensorType.fromSpec("tensor(x{})")).cell().label("x", "l0").value(1.0).build(),
                     Tensor.from("{{x:l0}:1.0}"));
        assertEquals("If the type is specified, a dense tensor can be created from the sparse text form",
                     Tensor.Builder.of(TensorType.fromSpec("tensor(x[1])")).cell(1.0, 0).build(),
                     Tensor.from("tensor(x[1]):{{x:0 }:1.0}"));
        assertEquals(Tensor.Builder.of(TensorType.fromSpec("tensor(x{})")).cell().label("x", "..\",]}:..").value(1.0).build(),
                     Tensor.from("{{x:'..\",]}:..'}:1.0}"));
        assertEquals(Tensor.Builder.of(TensorType.fromSpec("tensor(x{})")).cell().label("x", "..'..").value(1.0).build(),
                     Tensor.from("{{x:\"..'..\"}:1.0}"));
    }

    @Test
    public void testSingle() {
        assertDense(Tensor.Builder.of(TensorType.fromSpec("tensor(x[1])")).cell(1.0, 0).build(),
                    "tensor(x[1]):[1.0]");
    }

    @Test
    public void testDenseParsing() {
        assertDense(Tensor.Builder.of(TensorType.fromSpec("tensor()")).build(),
                    "tensor():{0.0}");
        assertDense(Tensor.Builder.of(TensorType.fromSpec("tensor()")).cell(1.3).build(),
                    "tensor():{1.3}");
        assertDense(Tensor.Builder.of(TensorType.fromSpec("tensor(x[])")).cell(1.0, 0).build(),
                    "tensor(x[]):{0:1.0}");
        assertDense(Tensor.Builder.of(TensorType.fromSpec("tensor(x[1])")).cell(1.0, 0).build(),
                    "tensor(x[1]):[1.0]");
        assertDense(Tensor.Builder.of(TensorType.fromSpec("tensor(x[2])")).cell(1.0, 0).cell(2.0, 1).build(),
                    "tensor(x[2]):[1.0, 2.0]");
        assertDense(Tensor.Builder.of(TensorType.fromSpec("tensor(x[2],y[3])"))
                                   .cell(1.0, 0, 0)
                                   .cell(2.0, 0, 1)
                                   .cell(3.0, 0, 2)
                                   .cell(4.0, 1, 0)
                                   .cell(5.0, 1, 1)
                                   .cell(6.0, 1, 2).build(),
                    "tensor(x[2],y[3]):[[1.0, 2.0, 3.0], [4.0, 5.0, 6.0]]");
        assertDense(Tensor.Builder.of(TensorType.fromSpec("tensor(x[1],y[2],z[3])"))
                                   .cell(1.0, 0, 0, 0)
                                   .cell(2.0, 0, 0, 1)
                                   .cell(3.0, 0, 0, 2)
                                   .cell(4.0, 0, 1, 0)
                                   .cell(5.0, 0, 1, 1)
                                   .cell(6.0, 0, 1, 2).build(),
                    "tensor(x[1],y[2],z[3]):[[[1.0, 2.0, 3.0], [4.0, 5.0, 6.0]]]");
        assertDense(Tensor.Builder.of(TensorType.fromSpec("tensor(x[3],y[2],z[1])"))
                                   .cell(1.0, 0, 0, 0)
                                   .cell(2.0, 0, 1, 0)
                                   .cell(3.0, 1, 0, 0)
                                   .cell(4.0, 1, 1, 0)
                                   .cell(5.0, 2, 0, 0)
                                   .cell(-6.0, 2, 1, 0).build(),
                    "tensor(x[3],y[2],z[1]):[[[1.0], [2.0]], [[3.0], [4.0]], [[5.0], [-6.0]]]");
        assertEquals("Skipping structure",
                     Tensor.Builder.of(TensorType.fromSpec("tensor(x[3],y[2],z[1])"))
                                   .cell( 1.0, 0, 0, 0)
                                   .cell( 2.0, 0, 1, 0)
                                   .cell( 3.0, 1, 0, 0)
                                   .cell( 4.0, 1, 1, 0)
                                   .cell( 5.0, 2, 0, 0)
                                   .cell(-6.0, 2, 1, 0).build(),
                     Tensor.from("tensor( x[3],y[2],z[1]) : [1.0, 2.0, 3.0 , 4.0, 5, -6.0]"));
    }

    @Test
    public void testDenseWrongOrder() {
        assertEquals("Opposite order of dimensions",
                     Tensor.Builder.of(TensorType.fromSpec("tensor(x[3],y[2])"))
                                   .cell(1, 0, 0)
                                   .cell(4, 0, 1)
                                   .cell(2, 1, 0)
                                   .cell(5, 1, 1)
                                   .cell(3, 2, 0)
                                   .cell(6, 2, 1).build(),
                     Tensor.from("tensor(y[2],x[3]):[[1,2,3],[4,5,6]]"));
    }

    @Test
    public void testMixedParsing() {
        assertEquals(Tensor.Builder.of(TensorType.fromSpec("tensor(key{}, x[2])"))
                                   .cell(TensorAddress.ofLabels("a", "0"), 1)
                                   .cell(TensorAddress.ofLabels("a", "1"), 2)
                                   .cell(TensorAddress.ofLabels("b", "0"), 3)
                                   .cell(TensorAddress.ofLabels("b", "1"), 4).build(),
                     Tensor.from("tensor(key{}, x[2]):{a:[1, 2], b:[3, 4]}"));
        assertEquals(Tensor.Builder.of(TensorType.fromSpec("tensor(key{}, x[2])"))
                                   .cell(TensorAddress.ofLabels(",:", "0"), 1)
                                   .cell(TensorAddress.ofLabels(",:", "1"), 2)
                                   .cell(TensorAddress.ofLabels("b", "0"), 3)
                                   .cell(TensorAddress.ofLabels("b", "1"), 4).build(),
                     Tensor.from("tensor(key{}, x[2]):{',:':[1, 2], b:[3, 4]}"));
    }

    @Test
    public void testSparseShortFormParsing() {
        assertEquals(Tensor.Builder.of(TensorType.fromSpec("tensor(key{})"))
                                   .cell(TensorAddress.ofLabels("a"), 1)
                                   .cell(TensorAddress.ofLabels("b"), 2).build(),
                     Tensor.from("tensor(key{}):{a:1, b:2}"));
        assertEquals(Tensor.Builder.of(TensorType.fromSpec("tensor(key{})"))
                                   .cell(TensorAddress.ofLabels("..\",}]:.."), 1)
                                   .cell(TensorAddress.ofLabels("b"), 2).build(),
                     Tensor.from("tensor(key{}):{'..\",}]:..':1, b:2}"));
        assertEquals(Tensor.Builder.of(TensorType.fromSpec("tensor(key{})"))
                                   .cell(TensorAddress.ofLabels("..'.."), 1)
                                   .cell(TensorAddress.ofLabels("b"), 2).build(),
                     Tensor.from("tensor(key{}):{\"..'..\":1, b:2}"));
    }

    @Test
    public void testMixedWrongOrder() {
        assertEquals("Opposite order of dimensions",
                     Tensor.Builder.of(TensorType.fromSpec("tensor(key{},x[3],y[2])"))
                                   .cell(TensorAddress.ofLabels("key1", "0", "0"), 1)
                                   .cell(TensorAddress.ofLabels("key1", "0", "1"), 4)
                                   .cell(TensorAddress.ofLabels("key1", "1", "0"), 2)
                                   .cell(TensorAddress.ofLabels("key1", "1", "1"), 5)
                                   .cell(TensorAddress.ofLabels("key1", "2", "0"), 3)
                                   .cell(TensorAddress.ofLabels("key1", "2", "1"), 6)
                                   .cell(TensorAddress.ofLabels("key2", "0", "0"), 7)
                                   .cell(TensorAddress.ofLabels("key2", "0", "1"), 10)
                                   .cell(TensorAddress.ofLabels("key2", "1", "0"), 8)
                                   .cell(TensorAddress.ofLabels("key2", "1", "1"), 11)
                                   .cell(TensorAddress.ofLabels("key2", "2", "0"), 9)
                                   .cell(TensorAddress.ofLabels("key2", "2", "1"), 12).build(),
                     Tensor.from("tensor(key{},y[2],x[3]):{key1:[[1,2,3],[4,5,6]], key2:[[7,8,9],[10,11,12]]}"));
        assertEquals("Opposite order of dimensions",
                     Tensor.from("tensor(key{},x[3],y[2]):{key1:[[1,4],[2,5],[3,6]], key2:[[7,10],[8,11],[9,12]]}"),
                     Tensor.from("tensor(key{},y[2],x[3]):{key1:[[1,2,3],[4,5,6]], key2:[[7,8,9],[10,11,12]]}"));
    }

    private void assertDense(Tensor expectedTensor, String denseFormat) {
        assertEquals(denseFormat, expectedTensor, Tensor.from(denseFormat));
        assertEquals(denseFormat, expectedTensor.toString());
    }

    @Test
    public void testIllegalStrings() {
        assertIllegal("A dimension name must be an identifier or integer, not ''x''",
                      "{{'x':\"l0\"}:1.0}");
        assertIllegal("A dimension name must be an identifier or integer, not '\"x\"'",
                      "{{\"x\":\"l0\", \"y\":\"l0\"}:1.0, {\"x\":\"l0\", \"y\":\"l1\"}:2.0}");
        assertIllegal("At {x:0}: '1-.0' is not a valid double",
                      "{{x:0}:1-.0}");
        assertIllegal("At value position 1: '1-.0' is not a valid double",
                      "tensor(x[1]):[1-.0]");
        assertIllegal("At value position 5: Expected a ',' but got ']'",
                      "tensor(x[3]):[1, 2]");
        assertIllegal("At value position 8: Expected a ']' but got ','",
                      "tensor(x[3]):[1, 2, 3, 4]");
    }

    private void assertIllegal(String message, String tensor) {
        try {
            Tensor.from(tensor);
            fail("Expected an IllegalArgumentException when parsing " + tensor);
        }
        catch (IllegalArgumentException e) {
            assertEquals(message, e.getCause().getMessage());
        }
    }

}
