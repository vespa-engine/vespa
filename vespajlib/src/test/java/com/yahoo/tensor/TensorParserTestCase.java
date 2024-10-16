// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class TensorParserTestCase {

    @Test
    public void testEmpty() {
        assertEquals(Tensor.Builder.of(TensorType.empty).cell(1).build(), Tensor.from("tensor():{{}:1}"));
        assertEquals(Tensor.Builder.of(TensorType.empty).cell(10).build(), Tensor.from("10.0"));
        assertEquals(Tensor.Builder.of(TensorType.empty).cell(10).build(), Tensor.from(TensorType.empty, "10.0"));
        // looks like a hex string, but should not be interpreted as such:
        assertEquals(Tensor.Builder.of(TensorType.empty).cell(10).build(), Tensor.from("0000000000000010"));
        assertEquals(Tensor.Builder.of(TensorType.empty).cell(10).build(), Tensor.from(TensorType.empty, "0000000000000010"));
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

        var int8TT = TensorType.fromSpec("tensor<int8>(x[2],y[3])");

        assertEquals("binary tensor A",
                     Tensor.Builder.of(int8TT)
                     .cell(1, 0, 0)
                     .cell(20, 0, 1)
                     .cell(127, 0, 2)
                     .cell(-1, 1, 0)
                     .cell(50, 1, 1)
                     .cell(-128, 1, 2).build(),
                     Tensor.from(int8TT, "01147FFF3280"));

        assertEquals("binary tensor B",
                     Tensor.Builder.of(int8TT)
                     .cell(26.0, 0, 0)
                     .cell(0.0, 0, 1)
                     .cell(31.0, 0, 2)
                     .cell(-68.0, 1, 0)
                     .cell(-98.0, 1, 1)
                     .cell(-34.0, 1, 2).build(),
                     Tensor.from(int8TT, "1a001fbc9ede"));

        assertEquals("binary tensor C",
                     Tensor.Builder.of(int8TT)
                     .cell(16, 0, 0)
                     .cell(32, 0, 1)
                     .cell(48, 0, 2)
                     .cell(-16, 1, 0)
                     .cell(-32, 1, 1)
                     .cell(-64, 1, 2).build(),
                     Tensor.from(int8TT, "102030F0E0C0"));

        var floatTT = TensorType.fromSpec("tensor<float>(x[3])");
        assertEquals("float tensor hexdump",
                     Tensor.Builder.of(floatTT)
                     .cell(0, 0)
                     .cell(1.25, 1)
                     .cell(-19.125, 2).build(),
                     Tensor.from(floatTT, "000000003FA00000c1990000"));

        assertEquals("binary matrix",
                     Tensor.Builder.of(int8TT)
                     .cell(1, 0, 0)
                     .cell(3, 0, 1)
                     .cell(5, 0, 2)
                     .cell(2, 1, 0)
                     .cell(4, 1, 1)
                     .cell(6, 1, 2).build(),
                     Tensor.from("tensor<int8>(y[3],x[2]): 010203040506"));
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
    public void testMultiMappedParsing() {
        Tensor expected = Tensor.Builder.of(TensorType.fromSpec("tensor(ma{}, mb{}, x[2], y[3])"))
                .cell(TensorAddress.ofLabels("a", "b", "0", "0"), 1)
                .cell(TensorAddress.ofLabels("a", "b", "0", "1"), 2)
                .cell(TensorAddress.ofLabels("a", "b", "0", "2"), 3)
                .cell(TensorAddress.ofLabels("a", "b", "1", "0"), 4)
                .cell(TensorAddress.ofLabels("a", "b", "1", "1"), 5)
                .cell(TensorAddress.ofLabels("a", "b", "1", "2"), 6)
                .cell(TensorAddress.ofLabels("foo", "bar", "0", "0"), 7)
                .cell(TensorAddress.ofLabels("foo", "bar", "0", "1"), 8)
                .cell(TensorAddress.ofLabels("foo", "bar", "0", "2"), 9)
                .cell(TensorAddress.ofLabels("foo", "bar", "1", "0"), 10)
                .cell(TensorAddress.ofLabels("foo", "bar", "1", "1"), 11)
                .cell(TensorAddress.ofLabels("foo", "bar", "1", "2"), 12)
                .build();

        assertEquals(expected,
                     Tensor.from("tensor(ma{}, mb{}, x[2], y[3]):{a:{b:[[1,2,3],[4,5,6]]}, foo:{bar:[[7,8,9],[10,11,12]]}}"));

        assertEquals(expected,
                     Tensor.from("tensor(mb{}, ma{}, x[2], y[3]):{b:{a:[[1,2,3],[4,5,6]]}, bar:{foo:[[7,8,9],[10,11,12]]}}"));

        assertEquals(expected,
                     Tensor.from("tensor(mb{}, ma{}, y[3], x[2]):{b:{a:[[1,4], [2,5], [3,6]]}, bar:{foo:[[7,10], [8,11], [9,12]]}}"));


        assertEquals(
                Tensor.from("""
                            tensor(category{},key{},x[2],y[3]):{
                               {category:cat1, key:key1, x:0, y:0}: 1.0,
                               {category:cat1, key:key1, x:0, y:1}: 2.0,
                               {category:cat1, key:key1, x:0, y:2}: 3.0,
                               {category:cat1, key:key1, x:1, y:0}: 4.0,
                               {category:cat1, key:key1, x:1, y:1}: 5.0,
                               {category:cat1, key:key1, x:1, y:2}: 6.0,

                               {category:cat1, key:key2, x:0, y:0}: 1.1,
                               {category:cat1, key:key2, x:0, y:1}: 2.1,
                               {category:cat1, key:key2, x:0, y:2}: 3.1,
                               {category:cat1, key:key2, x:1, y:0}: 4.1,
                               {category:cat1, key:key2, x:1, y:1}: 5.1,
                               {category:cat1, key:key2, x:1, y:2}: 6.1,

                               {category:cat2, key:key1, x:0, y:0}: 7.3,
                               {category:cat2, key:key1, x:0, y:1}: 8.3,
                               {category:cat2, key:key1, x:0, y:2}: 9.3,
                               {category:cat2, key:key1, x:1, y:0}: 7.0,
                               {category:cat2, key:key1, x:1, y:1}: 8.0,
                               {category:cat2, key:key1, x:1, y:2}: 9.0,

                               {category:cat2, key:key3, x:0, y:0}: 7.5,
                               {category:cat2, key:key3, x:0, y:1}: 8.5,
                               {category:cat2, key:key3, x:0, y:2}: 9.5,
                               {category:cat2, key:key3, x:1, y:0}: 7.9,
                               {category:cat2, key:key3, x:1, y:1}: 8.9,
                               {category:cat2, key:key3, x:1, y:2}: 9.9
                            }
                            """),
                Tensor.from("""
                            tensor(category{},key{},x[2],y[3]):{
                               cat1:{key1:[[1.0, 2.0, 3.0], [4.0, 5.0, 6.0]],
                                     key2:[[1.1, 2.1, 3.1], [4.1, 5.1, 6.1]]},
                               cat2:{key1:[[7.3, 8.3, 9.3], [7.0, 8.0, 9.0]],
                                     key3:[[7.5, 8.5, 9.5], [7.9, 8.9, 9.9]]}
                            }
                            """));
    }

    @Test
    public void testMixedHexParsing() {
        assertEquals(Tensor.Builder.of(TensorType.fromSpec("tensor<int8>(key{}, x[2])"))
                                   .cell(TensorAddress.ofLabels("a", "0"), (byte)0xa1)
                                   .cell(TensorAddress.ofLabels("a", "1"), 2)
                                   .cell(TensorAddress.ofLabels("b", "0"), (byte)0xA3)
                                   .cell(TensorAddress.ofLabels("b", "1"), 4).build(),
                     Tensor.from("tensor<int8>(key{}, x[2]):{a: a102, b: A304}"));
        assertEquals(Tensor.Builder.of(TensorType.fromSpec("tensor<float>(key{}, x[1])"))
                                   .cell(TensorAddress.ofLabels("a", "0"), 0)
                                   .cell(TensorAddress.ofLabels("b", "0"), 1.25)
                                   .cell(TensorAddress.ofLabels("c", "0"), -19.125).build(),
                     Tensor.from("tensor<float>(key{}, x[1]):{a: 00000000, b:3FA00000, c:  c1990000 }"));
        assertEquals(Tensor.Builder.of(TensorType.fromSpec("tensor<float>(key{}, x[1])"))
                                   .cell(TensorAddress.ofLabels("a", "0"), 0)
                                   .cell(TensorAddress.ofLabels("b", "0"), 1.25)
                                   .cell(TensorAddress.ofLabels("c", "0"), -19.125).build(),
                     Tensor.from("tensor<float>(key{}, x[1])", "{a: 00000000, b:3FA00000, c:  c1990000 }"));
        assertEquals(Tensor.Builder.of(TensorType.fromSpec("tensor<int8>(key{}, x[2], y[2])"))
                     .cell(TensorAddress.ofLabels("a", "0", "0"), 1)
                     .cell(TensorAddress.ofLabels("a", "0", "1"), 2)
                     .cell(TensorAddress.ofLabels("a", "1", "0"), 3)
                     .cell(TensorAddress.ofLabels("a", "1", "1"), 4)
                     .cell(TensorAddress.ofLabels("b", "0", "0"), 5)
                     .cell(TensorAddress.ofLabels("b", "0", "1"), 6)
                     .cell(TensorAddress.ofLabels("b", "1", "0"), 7)
                     .cell(TensorAddress.ofLabels("b", "1", "1"), 8)
                     .build(),
                     Tensor.from("tensor<int8>(key{}, y[2], x[2])", "{a: 01020304, b: 05060708}"));
        assertEquals(Tensor.Builder.of(TensorType.fromSpec("tensor<int8>(key{}, x[2], y[2])"))
                     .cell(TensorAddress.ofLabels("a", "0", "0"), 1)
                     .cell(TensorAddress.ofLabels("a", "0", "1"), 3)
                     .cell(TensorAddress.ofLabels("a", "1", "0"), 2)
                     .cell(TensorAddress.ofLabels("a", "1", "1"), 4)
                     .cell(TensorAddress.ofLabels("b", "0", "0"), 5)
                     .cell(TensorAddress.ofLabels("b", "0", "1"), 7)
                     .cell(TensorAddress.ofLabels("b", "1", "0"), 6)
                     .cell(TensorAddress.ofLabels("b", "1", "1"), 8)
                     .build(),
                     Tensor.from("tensor<int8>(key{}, y[2], x[2]):{a: 01020304, b: 05060708}"));
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

    @Test
    public void testUnboundShortFormParsing() {
        assertEquals(Tensor.from("tensor(x[]):[1.0, 2.0]"),
                     Tensor.Builder.of(TensorType.fromSpec("tensor(x[])")).cell(1.0, 0).cell(2.0, 1).build());
        assertEquals(Tensor.from("tensor<float>(x[]):[1.0, 2.0]"),
                Tensor.Builder.of(TensorType.fromSpec("tensor<float>(x[])")).cell(1.0, 0).cell(2.0, 1).build());
        assertEquals(Tensor.from("tensor<int8>(x[]):[1.0, 2.0]"),
                Tensor.Builder.of(TensorType.fromSpec("tensor<int8>(x[])")).cell(1.0, 0).cell(2.0, 1).build());
        assertEquals(Tensor.from("tensor<bfloat16>(x[]):[1.0, 2.0]"),
                Tensor.Builder.of(TensorType.fromSpec("tensor<bfloat16>(x[])")).cell(1.0, 0).cell(2.0, 1).build());

        assertEquals(Tensor.from("tensor(x[],y[]):[[1,2,3,4]]"),
                     Tensor.Builder.of(TensorType.fromSpec("tensor(x[],y[])"))
                        .cell(1.0, 0, 0).cell(2.0, 0, 1).cell(3.0, 0, 2).cell(4.0, 0, 3).build());
        assertEquals(Tensor.from("tensor(x[],y[]):[[1,2],[3,4]]"),
                     Tensor.Builder.of(TensorType.fromSpec("tensor(x[],y[])"))
                         .cell(1.0, 0, 0).cell(2.0, 0, 1).cell(3.0, 1, 0).cell(4.0, 1, 1).build());
        assertEquals(Tensor.from("tensor(x[],y[]):[[1],[2],[3],[4]]"),
                     Tensor.Builder.of(TensorType.fromSpec("tensor(x[],y[])"))
                        .cell(1.0, 0, 0).cell(2.0, 1, 0).cell(3.0, 2, 0).cell(4.0, 3, 0).build());
        assertEquals(Tensor.from("tensor(x[],y[],z[]):[[[1,2],[3,4]]]"),
                     Tensor.Builder.of(TensorType.fromSpec("tensor(x[],y[],z[])"))
                        .cell(1.0, 0, 0, 0).cell(2.0, 0, 0, 1).cell(3.0, 0, 1, 0).cell(4.0, 0, 1, 1).build());
        assertEquals(Tensor.from("tensor(x[],y[],z[]):[[[1],[2],[3],[4]]]"),
                     Tensor.Builder.of(TensorType.fromSpec("tensor(x[],y[],z[])"))
                        .cell(1.0, 0, 0, 0).cell(2.0, 0, 1, 0).cell(3.0, 0, 2, 0).cell(4.0, 0, 3, 0).build());
        assertEquals(Tensor.from("tensor(x[],y[],z[]):[[[1,2,3,4]]]"),
                     Tensor.Builder.of(TensorType.fromSpec("tensor(x[],y[],z[])"))
                        .cell(1.0, 0, 0, 0).cell(2.0, 0, 0, 1).cell(3.0, 0, 0, 2).cell(4.0, 0, 0, 3).build());
        assertEquals(Tensor.from("tensor(x[],y[],z[]):[[[1]],[[2]],[[3]],[[4]]]"),
                     Tensor.Builder.of(TensorType.fromSpec("tensor(x[],y[],z[])"))
                        .cell(1.0, 0, 0, 0).cell(2.0, 1, 0, 0).cell(3.0, 2, 0, 0).cell(4.0, 3, 0, 0).build());
        assertEquals(Tensor.from("tensor(x[],y[],z[]):[[[1, 2]],[[3, 4]]]"),
                     Tensor.Builder.of(TensorType.fromSpec("tensor(x[],y[],z[])"))
                        .cell(1.0, 0, 0, 0).cell(2.0, 0, 0, 1).cell(3.0, 1, 0, 0).cell(4.0, 1, 0, 1).build());

        assertEquals(Tensor.from("tensor(x[],y[],z[4]):[[[1,2,3,4]]]"),
                     Tensor.Builder.of(TensorType.fromSpec("tensor(x[],y[],z[])"))
                        .cell(1.0, 0, 0, 0).cell(2.0, 0, 0, 1).cell(3.0, 0, 0, 2).cell(4.0, 0, 0, 3).build());
        assertEquals(Tensor.from("tensor(x[2],y[],z[2]):[[[1, 2]],[[3, 4]]]"),
                     Tensor.Builder.of(TensorType.fromSpec("tensor(x[],y[],z[])"))
                        .cell(1.0, 0, 0, 0).cell(2.0, 0, 0, 1).cell(3.0, 1, 0, 0).cell(4.0, 1, 0, 1).build());

        assertIllegal("Unexpected size 2 for dimension y for type tensor(x[],y[3])",
                "tensor(x[],y[3]):[[1,2],[3,4]]");
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
 //     assertIllegal("At {x:0}: '1-.0' is not a valid double",
 //                   "{{x:0}:1-.0}");
        assertIllegal("At value position 7: '1-.0' is not a valid double",
                      "{{x:0}:1-.0}");
        assertIllegal("At value position 1: '1-.0' is not a valid double",
                      "tensor(x[1]):[1-.0]");
        assertIllegal("At value position 5: Expected a ',' but got ']'",
                      "tensor(x[3]):[1, 2]");
        assertIllegal("At value position 8: Expected a ']' but got ','",
                      "tensor(x[3]):[1, 2, 3, 4]");
        assertIllegal("No suitable dimension in tensor(x[3]) for parsing a tensor on the mixed form: Should have one mapped dimension",
                      "tensor(x[3]):{1:[1,2,3], 2:[2,3,4], 3:[3,4,5]}");
        assertIllegal("Garbage after mapped tensor string: foo",
                      "{{x:0}:1.0} foo");
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
