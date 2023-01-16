// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.serialization;

import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.text.JSON;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author bratseth
 */
public class JsonFormatTestCase {

    /** Tests parsing of various tensor values set at the root, i.e. no 'cells', 'blocks' or 'values' */
    @Test
    public void testDirectValue() {
        assertDecoded("tensor(x{}):{a:2, b:3}", "{'a':2.0, 'b':3.0}");
        assertDecoded("tensor(x{}):{a:2, b:3}", "{'a':2.0, 'b':3.0}");
        assertDecoded("tensor(x[2]):[1.0, 2.0]]", "[1, 2]");
        assertDecoded("tensor(x[2],y[3]):[[1.0, 2.0, 3.0], [4.0, 5.0, 6.0]]", "[1, 2, 3, 4, 5, 6]");
        assertDecoded("tensor(x[2],y[3]):[[1.0, 2.0, 3.0], [4.0, 5.0, 6.0]]", "[[1, 2, 3], [4, 5, 6]]");
        assertDecoded("tensor(x{},y[2]):{a:[2, 3], b:[4, 5]}", "{'a':[2, 3], 'b':[4, 5]}");
        assertDecoded("tensor(x{},y{}):{{x:a,y:0}:2, {x:b,y:1}:3}",
                      "[{'address':{'x':'a','y':'0'},'value':2}, {'address':{'x':'b','y':'1'},'value':3}]");
    }

    @Test
    public void testDirectValueReservedNameKeys() {
        // Single-valued
        assertDecoded("tensor(x{}):{cells:2, b:3}", "{'cells':2.0, 'b':3.0}");
        assertDecoded("tensor(x{}):{values:2, b:3}", "{'values':2.0, 'b':3.0}");
        assertDecoded("tensor(x{}):{block:2, b:3}", "{'block':2.0, 'b':3.0}");
        assertDecoded("tensor(x{}):{type:2, b:3}", "{'type':2.0, 'b':3.0}");

        // Multi-valued
        assertDecoded("tensor(x{},y[2]):{cells:[2, 3], b:[4, 5]}", "{'cells':[2, 3], 'b':[4, 5]}");
        assertDecoded("tensor(x{},y[2]):{values:[2, 3], b:[4, 5]}", "{'values':[2, 3], 'b':[4, 5]}");
        assertDecoded("tensor(x{},y[2]):{block:[2, 3], b:[4, 5]}", "{'block':[2, 3], 'b':[4, 5]}");
        assertDecoded("tensor(x{},y[2]):{type:[2, 3], b:[4, 5]}", "{'type':[2, 3], 'b':[4, 5]}");
    }

    @Test
    public void testEmptySparseTensor() {
        Tensor.Builder builder = Tensor.Builder.of(TensorType.fromSpec("tensor(x{},y{})"));
        Tensor tensor = builder.build();
        byte[] json = JsonFormat.encode(tensor, false, false);
        assertEquals("{\"type\":\"tensor(x{},y{})\",\"cells\":[]}",
                     new String(json, StandardCharsets.UTF_8));
        Tensor decoded = JsonFormat.decode(tensor.type(), json);
        assertEquals(tensor, decoded);

        json = "{}".getBytes(); // short form variant of the above
        decoded = JsonFormat.decode(tensor.type(), json);
        assertEquals(tensor, decoded);
    }

    @Test
    public void testSingleSparseDimensionShortForm() {
        Tensor.Builder builder = Tensor.Builder.of(TensorType.fromSpec("tensor(x{})"));
        builder.cell().label("x", "a").value(2.0);
        builder.cell().label("x", "c").value(3.0);
        Tensor expected = builder.build();

        String json= "{\"cells\":{" +
                     "\"a\":2.0," +
                     "\"c\":3.0" +
                     "}}";
        Tensor decoded = JsonFormat.decode(expected.type(), json.getBytes(StandardCharsets.UTF_8));
        assertEquals(expected, decoded);
    }

    @Test
    public void testEmptyTensor() {
        Tensor tensor = Tensor.Builder.of(TensorType.empty).build();

        String shortJson = """
                {
                  "type":"tensor()",
                  "values":[0.0]
                }
                """;
        byte[] shortEncoded = JsonFormat.encode(tensor, true, false);
        assertEqualJson(shortJson, new String(shortEncoded, StandardCharsets.UTF_8));
        assertEquals(tensor, JsonFormat.decode(tensor.type(), shortEncoded));

        String longJson = """
                {
                  "type":"tensor()",
                  "cells":[{"address":{},"value":0.0}]
                }
                """;
        byte[] longEncoded = JsonFormat.encode(tensor, false, false);
        assertEqualJson(longJson, new String(longEncoded, StandardCharsets.UTF_8));
        assertEquals(tensor, JsonFormat.decode(tensor.type(), longEncoded));

        String shortDirectJson = """
                [0.0]
                """;
        byte[] shortDirectEncoded = JsonFormat.encode(tensor, true, true);
        assertEqualJson(shortDirectJson, new String(shortDirectEncoded, StandardCharsets.UTF_8));
        assertEquals(tensor, JsonFormat.decode(tensor.type(), shortDirectEncoded));

        String longDirectJson = """
                [{"address":{},"value":0.0}]
                """;
        byte[] longDirectEncoded = JsonFormat.encode(tensor, false, true);
        assertEqualJson(longDirectJson, new String(longDirectEncoded, StandardCharsets.UTF_8));
        assertEquals(tensor, JsonFormat.decode(tensor.type(), longDirectEncoded));
    }

    @Test
    public void testDenseTensor() {
        Tensor.Builder builder = Tensor.Builder.of(TensorType.fromSpec("tensor(x[2],y[2])"));
        builder.cell().label("x", 0).label("y", 0).value(2.0);
        builder.cell().label("x", 0).label("y", 1).value(3.0);
        builder.cell().label("x", 1).label("y", 0).value(5.0);
        builder.cell().label("x", 1).label("y", 1).value(7.0);
        Tensor tensor = builder.build();

        String shortJson = """
                {
                  "type":"tensor(x[2],y[2])",
                  "values":[[2.0,3.0],[5.0,7.0]]
                }
                """;
        byte[] shortEncoded = JsonFormat.encode(tensor, true, false);
        assertEqualJson(shortJson, new String(shortEncoded, StandardCharsets.UTF_8));
        assertEquals(tensor, JsonFormat.decode(tensor.type(), shortEncoded));

        String longJson = """
                {
                  "type":"tensor(x[2],y[2])",
                  "cells":[
                    {"address":{"x":"0","y":"0"},"value":2.0},
                    {"address":{"x":"0","y":"1"},"value":3.0},
                    {"address":{"x":"1","y":"0"},"value":5.0},
                    {"address":{"x":"1","y":"1"},"value":7.0}
                  ]
                }
                """;
        byte[] longEncoded = JsonFormat.encode(tensor, false, false);
        assertEqualJson(longJson, new String(longEncoded, StandardCharsets.UTF_8));
        assertEquals(tensor, JsonFormat.decode(tensor.type(), longEncoded));

        String shortDirectJson = """
                [[2.0, 3.0], [5.0, 7.0]]
                """;
        byte[] shortDirectEncoded = JsonFormat.encode(tensor, true, true);
        assertEqualJson(shortDirectJson, new String(shortDirectEncoded, StandardCharsets.UTF_8));
        assertEquals(tensor, JsonFormat.decode(tensor.type(), shortDirectEncoded));

        String longDirectJson = """
                [
                    {"address":{"x":"0","y":"0"},"value":2.0},
                    {"address":{"x":"0","y":"1"},"value":3.0},
                    {"address":{"x":"1","y":"0"},"value":5.0},
                    {"address":{"x":"1","y":"1"},"value":7.0}
                ]
                """;
        byte[] longDirectEncoded = JsonFormat.encode(tensor, false, true);
        assertEqualJson(longDirectJson, new String(longDirectEncoded, StandardCharsets.UTF_8));
        assertEquals(tensor, JsonFormat.decode(tensor.type(), longDirectEncoded));
    }

    @Test
    public void testMixedTensor() {
        Tensor.Builder builder = Tensor.Builder.of(TensorType.fromSpec("tensor(x{},y[2])"));
        builder.cell().label("x", "a").label("y", 0).value(2.0);
        builder.cell().label("x", "a").label("y", 1).value(3.0);
        builder.cell().label("x", "b").label("y", 0).value(5.0);
        builder.cell().label("x", "b").label("y", 1).value(7.0);
        Tensor tensor = builder.build();

        String shortJson = """
                {
                  "type":"tensor(x{},y[2])",
                  "blocks":{"a":[2.0,3.0],"b":[5.0,7.0]}
                }
                """;
        byte[] shortEncoded = JsonFormat.encode(tensor, true, false);
        assertEqualJson(shortJson, new String(shortEncoded, StandardCharsets.UTF_8));
        assertEquals(tensor, JsonFormat.decode(tensor.type(), shortEncoded));

        String longJson = """
                {
                  "type":"tensor(x{},y[2])",
                  "cells":[
                    {"address":{"x":"a","y":"0"},"value":2.0},
                    {"address":{"x":"a","y":"1"},"value":3.0},
                    {"address":{"x":"b","y":"0"},"value":5.0},
                    {"address":{"x":"b","y":"1"},"value":7.0}
                  ]
                }
                """;
        byte[] longEncoded = JsonFormat.encode(tensor, false, false);
        assertEqualJson(longJson, new String(longEncoded, StandardCharsets.UTF_8));
        assertEquals(tensor, JsonFormat.decode(tensor.type(), longEncoded));

        String shortDirectJson = """
                {"a":[2.0,3.0],"b":[5.0,7.0]}
                """;
        byte[] shortDirectEncoded = JsonFormat.encode(tensor, true, true);
        assertEqualJson(shortDirectJson, new String(shortDirectEncoded, StandardCharsets.UTF_8));
        assertEquals(tensor, JsonFormat.decode(tensor.type(), shortDirectEncoded));

        String longDirectJson = """
                [
                    {"address":{"x":"a","y":"0"},"value":2.0},
                    {"address":{"x":"a","y":"1"},"value":3.0},
                    {"address":{"x":"b","y":"0"},"value":5.0},
                    {"address":{"x":"b","y":"1"},"value":7.0}
                ]
                """;
        byte[] longDirectEncoded = JsonFormat.encode(tensor, false, true);
        assertEqualJson(longDirectJson, new String(longDirectEncoded, StandardCharsets.UTF_8));
        assertEquals(tensor, JsonFormat.decode(tensor.type(), longDirectEncoded));
    }

    @Test
    public void testSparseTensor() {
        Tensor.Builder builder = Tensor.Builder.of(TensorType.fromSpec("tensor(x{},y{})"));
        builder.cell().label("x", "a").label("y", 0).value(2.0);
        builder.cell().label("x", "a").label("y", 1).value(3.0);
        builder.cell().label("x", "b").label("y", 0).value(5.0);
        builder.cell().label("x", "b").label("y", 1).value(7.0);
        Tensor tensor = builder.build();

        String shortJson = """
                {
                  "type":"tensor(x{},y{})",
                   "cells": [
                     {"address":{"x":"a","y":"0"},"value":2.0},
                     {"address":{"x":"a","y":"1"},"value":3.0},
                     {"address":{"x":"b","y":"0"},"value":5.0},
                     {"address":{"x":"b","y":"1"},"value":7.0}
                   ]
                }
                """;
        byte[] shortEncoded = JsonFormat.encode(tensor, true, false);
        assertEqualJson(shortJson, new String(shortEncoded, StandardCharsets.UTF_8));
        assertEquals(tensor, JsonFormat.decode(tensor.type(), shortEncoded));

        String longJson = """
                {
                  "type":"tensor(x{},y{})",
                  "cells":[
                    {"address":{"x":"a","y":"0"},"value":2.0},
                    {"address":{"x":"a","y":"1"},"value":3.0},
                    {"address":{"x":"b","y":"0"},"value":5.0},
                    {"address":{"x":"b","y":"1"},"value":7.0}
                  ]
                }
                """;
        byte[] longEncoded = JsonFormat.encode(tensor, false, false);
        assertEqualJson(longJson, new String(longEncoded, StandardCharsets.UTF_8));
        assertEquals(tensor, JsonFormat.decode(tensor.type(), longEncoded));

        String shortDirectJson = """
                [
                  {"address":{"x":"a","y":"0"},"value":2.0},
                  {"address":{"x":"a","y":"1"},"value":3.0},
                  {"address":{"x":"b","y":"0"},"value":5.0},
                  {"address":{"x":"b","y":"1"},"value":7.0}
                ]
                """;
        byte[] shortDirectEncoded = JsonFormat.encode(tensor, true, true);
        assertEqualJson(shortDirectJson, new String(shortDirectEncoded, StandardCharsets.UTF_8));
        assertEquals(tensor, JsonFormat.decode(tensor.type(), shortDirectEncoded));

        String longDirectJson = """
                [
                    {"address":{"x":"a","y":"0"},"value":2.0},
                    {"address":{"x":"a","y":"1"},"value":3.0},
                    {"address":{"x":"b","y":"0"},"value":5.0},
                    {"address":{"x":"b","y":"1"},"value":7.0}
                ]
                """;
        byte[] longDirectEncoded = JsonFormat.encode(tensor, false, true);
        assertEqualJson(longDirectJson, new String(longDirectEncoded, StandardCharsets.UTF_8));
        assertEquals(tensor, JsonFormat.decode(tensor.type(), longDirectEncoded));
    }

    @Test
    public void testDisallowedEmptyDenseTensor() {
        TensorType type = TensorType.fromSpec("tensor(x[3])");
        assertDecodeFails(type, "{\"values\":[]}", "The values array does not contain any values");
        assertDecodeFails(type, "{\"values\":\"\"}", "The values string does not contain any values");
    }

    @Test
    public void testDisallowedEmptyMixedTensor() {
        TensorType type = TensorType.fromSpec("tensor(x{},y[3])");
        assertDecodeFails(type, "{\"blocks\":{ \"a\": [] } }", "The block value array does not contain any values");
        assertDecodeFails(type, "{\"blocks\":[ {\"address\":{\"x\":\"a\"}, \"values\": [] } ] }",
                "The block value array does not contain any values");
    }

    @Test
    public void testDenseTensorInDenseForm() {
        Tensor.Builder builder = Tensor.Builder.of(TensorType.fromSpec("tensor(x[2],y[3])"));
        builder.cell().label("x", 0).label("y", 0).value(2.0);
        builder.cell().label("x", 0).label("y", 1).value(3.0);
        builder.cell().label("x", 0).label("y", 2).value(4.0);
        builder.cell().label("x", 1).label("y", 0).value(5.0);
        builder.cell().label("x", 1).label("y", 1).value(6.0);
        builder.cell().label("x", 1).label("y", 2).value(7.0);
        Tensor expected = builder.build();
        String denseJson = "{\"values\":[2.0, 3.0, 4.0, 5.0, 6.0, 7.0]}";
        Tensor decoded = JsonFormat.decode(expected.type(), denseJson.getBytes(StandardCharsets.UTF_8));
        assertEquals(expected, decoded);
    }

    @Test
    public void testEncodeIndexedShortForm() {
        assertEncodeShortForm("tensor(x[]):[1.0, 2.0]",
                              "{\"type\":\"tensor(x[])\",\"values\":[1.0,2.0]}");
        assertEncodeShortForm("tensor<float>(x[]):[1.0, 2.0]",
                              "{\"type\":\"tensor<float>(x[])\",\"values\":[1.0,2.0]}");
        assertEncodeShortForm("tensor(x[],y[]):[[1,2,3,4]]",
                              "{\"type\":\"tensor(x[],y[])\",\"values\":[[1.0,2.0,3.0,4.0]]}");
        assertEncodeShortForm("tensor(x[],y[]):[[1,2],[3,4]]",
                              "{\"type\":\"tensor(x[],y[])\",\"values\":[[1.0,2.0],[3.0,4.0]]}");
        assertEncodeShortForm("tensor(x[],y[]):[[1],[2],[3],[4]]",
                              "{\"type\":\"tensor(x[],y[])\",\"values\":[[1.0],[2.0],[3.0],[4.0]]}");
        assertEncodeShortForm("tensor(x[],y[],z[]):[[[1,2],[3,4]]]",
                              "{\"type\":\"tensor(x[],y[],z[])\",\"values\":[[[1.0,2.0],[3.0,4.0]]]}");
        assertEncodeShortForm("tensor(x[],y[],z[]):[[[1],[2],[3],[4]]]",
                              "{\"type\":\"tensor(x[],y[],z[])\",\"values\":[[[1.0],[2.0],[3.0],[4.0]]]}");
        assertEncodeShortForm("tensor(x[],y[],z[]):[[[1,2,3,4]]]",
                              "{\"type\":\"tensor(x[],y[],z[])\",\"values\":[[[1.0,2.0,3.0,4.0]]]}");
        assertEncodeShortForm("tensor(x[],y[],z[]):[[[1]],[[2]],[[3]],[[4]]]",
                              "{\"type\":\"tensor(x[],y[],z[])\",\"values\":[[[1.0]],[[2.0]],[[3.0]],[[4.0]]]}");
        assertEncodeShortForm("tensor(x[],y[],z[2]):[[[1, 2]],[[3, 4]]]",
                              "{\"type\":\"tensor(x[],y[],z[2])\",\"values\":[[[1.0,2.0]],[[3.0,4.0]]]}");
    }

    @Test
    public void testEncodeMappedSingleDimensionShortForm() {
        assertEncodeShortForm("tensor(x{}):{}",
                              "{\"type\":\"tensor(x{})\",\"cells\":{}}");
        assertEncodeShortForm("tensor(x{}):{a:1,b:2}",
                              "{\"type\":\"tensor(x{})\",\"cells\":{\"a\":1.0,\"b\":2.0}}");
        // Multiple mapped dimensions: no short form available
        assertEncodeShortForm("tensor(x{},y{}):{{x:a,y:b}:1,{x:c,y:d}:2}",
                              "{\"type\":\"tensor(x{},y{})\",\"cells\":[{\"address\":{\"x\":\"a\",\"y\":\"b\"},\"value\":1.0},{\"address\":{\"x\":\"c\",\"y\":\"d\"},\"value\":2.0}]}");
    }

    @Test
    public void testEncodeMixedShortForm() {
        assertEncodeShortForm("tensor(x{},y[2]):{a:[1,2], b:[3,4] }",
                              "{\"type\":\"tensor(x{},y[2])\",\"blocks\":{\"a\":[1.0,2.0],\"b\":[3.0,4.0]}}");
        assertEncodeShortForm("tensor(x[2],y{}):{a:[1,2], b:[3,4] }",
                              "{\"type\":\"tensor(x[2],y{})\",\"blocks\":{\"a\":[1.0,2.0],\"b\":[3.0,4.0]}}");
        assertEncodeShortForm("tensor(x{},y[2],z[2]):{a:[[1,2],[3,4]], b:[[5,6],[7,8]] }",
                              "{\"type\":\"tensor(x{},y[2],z[2])\",\"blocks\":{\"a\":[[1.0,2.0],[3.0,4.0]],\"b\":[[5.0,6.0],[7.0,8.0]]}}");
        assertEncodeShortForm("tensor(x[1],y{},z[4]):{a:[[1,2,3,4]], b:[[5,6,7,8]] }",
                              "{\"type\":\"tensor(x[1],y{},z[4])\",\"blocks\":{\"a\":[[1.0,2.0,3.0,4.0]],\"b\":[[5.0,6.0,7.0,8.0]]}}");
        assertEncodeShortForm("tensor(x[4],y[1],z{}):{a:[[1],[2],[3],[4]], b:[[5],[6],[7],[8]] }",
                              "{\"type\":\"tensor(x[4],y[1],z{})\",\"blocks\":{\"a\":[[1.0],[2.0],[3.0],[4.0]],\"b\":[[5.0],[6.0],[7.0],[8.0]]}}");
        assertEncodeShortForm("tensor(a[2],b[2],c{},d[2]):{a:[[[1,2], [3,4]], [[5,6], [7,8]]], b:[[[1,2], [3,4]], [[5,6], [7,8]]] }",
                              "{\"type\":\"tensor(a[2],b[2],c{},d[2])\",\"blocks\":{" +
                                    "\"a\":[[[1.0,2.0],[3.0,4.0]],[[5.0,6.0],[7.0,8.0]]]," +
                                    "\"b\":[[[1.0,2.0],[3.0,4.0]],[[5.0,6.0],[7.0,8.0]]]}}");

        // Multiple mapped dimensions
        assertEncodeShortForm("tensor(x{},y{},z[2]):{{x:a,y:0,z:0}:1, {x:a,y:0,z:1}:2, {x:b,y:1,z:0}:3, {x:b,y:1,z:1}:4 }",
                              "{\"type\":\"tensor(x{},y{},z[2])\",\"blocks\":[{\"address\":{\"x\":\"a\",\"y\":\"0\"},\"values\":[1.0,2.0]},{\"address\":{\"x\":\"b\",\"y\":\"1\"},\"values\":[3.0,4.0]}]}");
        assertEncodeShortForm("tensor(x{},y[2],z{}):{{x:a,y:0,z:0}:1, {x:a,y:1,z:0}:2, {x:b,y:0,z:1}:3, {x:b,y:1,z:1}:4 }",
                              "{\"type\":\"tensor(x{},y[2],z{})\",\"blocks\":[{\"address\":{\"x\":\"a\",\"z\":\"0\"},\"values\":[1.0,2.0]},{\"address\":{\"x\":\"b\",\"z\":\"1\"},\"values\":[3.0,4.0]}]}");
    }

    @Test
    public void testInt8VectorInHexForm() {
        Tensor.Builder builder = Tensor.Builder.of(TensorType.fromSpec("tensor<int8>(x[2],y[3])"));
        builder.cell().label("x", 0).label("y", 0).value(2.0);
        builder.cell().label("x", 0).label("y", 1).value(127.0);
        builder.cell().label("x", 0).label("y", 2).value(-1.0);
        builder.cell().label("x", 1).label("y", 0).value(-128.0);
        builder.cell().label("x", 1).label("y", 1).value(0.0);
        builder.cell().label("x", 1).label("y", 2).value(42.0);
        Tensor expected = builder.build();

        String denseJson = "{\"values\":\"027FFF80002A\"}";
        Tensor decoded = JsonFormat.decode(expected.type(), denseJson.getBytes(StandardCharsets.UTF_8));
        assertEquals(expected, decoded);

        denseJson = "\"027FFF80002A\"";
        decoded = JsonFormat.decode(expected.type(), denseJson.getBytes(StandardCharsets.UTF_8));
        assertEquals(expected, decoded);
    }

    @Test
    public void testInt8VectorInvalidHex() {
        var type = TensorType.fromSpec("tensor<int8>(x[2])");
        String denseJson = "{\"values\":\"abXc\"}";
        try {
            Tensor decoded = JsonFormat.decode(type, denseJson.getBytes(StandardCharsets.UTF_8));
            fail("did not get exception as expected, decoded as: "+decoded);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Invalid digit 'X' at index 2 in input abXc");
        }
    }

    @Test
    public void testMixedInt8TensorWithHexForm() {
        Tensor.Builder builder = Tensor.Builder.of(TensorType.fromSpec("tensor<int8>(x{},y[3])"));
        builder.cell().label("x", 0).label("y", 0).value(2.0);
        builder.cell().label("x", 0).label("y", 1).value(3.0);
        builder.cell().label("x", 0).label("y", 2).value(4.0);
        builder.cell().label("x", 1).label("y", 0).value(5.0);
        builder.cell().label("x", 1).label("y", 1).value(6.0);
        builder.cell().label("x", 1).label("y", 2).value(7.0);
        Tensor expected = builder.build();

        String mixedJson = "{\"blocks\":[" +
                           "{\"address\":{\"x\":\"0\"},\"values\":\"020304\"}," +
                           "{\"address\":{\"x\":\"1\"},\"values\":\"050607\"}" +
                           "]}";
        Tensor decoded = JsonFormat.decode(expected.type(), mixedJson.getBytes(StandardCharsets.UTF_8));
        assertEquals(expected, decoded);
    }

    @Test
    public void testBFloat16VectorInHexForm() {
        var builder = Tensor.Builder.of(TensorType.fromSpec("tensor<bfloat16>(x[3],y[4])"));
        builder.cell().label("x", 0).label("y", 0).value(42.0);
        builder.cell().label("x", 0).label("y", 1).value(1048576.0);
        builder.cell().label("x", 0).label("y", 2).value(0.00000095367431640625);
        builder.cell().label("x", 0).label("y", 3).value(-255.00);

        builder.cell().label("x", 1).label("y", 0).value(0.0);
        builder.cell().label("x", 1).label("y", 1).value(-0.0);
        builder.cell().label("x", 1).label("y", 2).value(Float.MIN_NORMAL);
        builder.cell().label("x", 1).label("y", 3).value(0x1.feP+127);

        builder.cell().label("x", 2).label("y", 0).value(Float.POSITIVE_INFINITY);
        builder.cell().label("x", 2).label("y", 1).value(Float.NEGATIVE_INFINITY);
        builder.cell().label("x", 2).label("y", 2).value(Float.NaN);
        builder.cell().label("x", 2).label("y", 3).value(-Float.NaN);
        Tensor expected = builder.build();

        String denseJson = "{\"values\":\"422849803580c37f0000800000807f7f7f80ff807fc0ffc0\"}";
        Tensor decoded = JsonFormat.decode(expected.type(), denseJson.getBytes(StandardCharsets.UTF_8));
        assertEquals(expected, decoded);
    }

    @Test
    public void testFloatVectorInHexForm() {
        var builder = Tensor.Builder.of(TensorType.fromSpec("tensor<float>(x[3],y[4])"));
        builder.cell().label("x", 0).label("y", 0).value(42.0);
        builder.cell().label("x", 0).label("y", 1).value(1048577.0);
        builder.cell().label("x", 0).label("y", 2).value(0.00000095367431640625);
        builder.cell().label("x", 0).label("y", 3).value(-255.00);

        builder.cell().label("x", 1).label("y", 0).value(0.0);
        builder.cell().label("x", 1).label("y", 1).value(-0.0);
        builder.cell().label("x", 1).label("y", 2).value(Float.MIN_VALUE);
        builder.cell().label("x", 1).label("y", 3).value(Float.MAX_VALUE);

        builder.cell().label("x", 2).label("y", 0).value(Float.POSITIVE_INFINITY);
        builder.cell().label("x", 2).label("y", 1).value(Float.NEGATIVE_INFINITY);
        builder.cell().label("x", 2).label("y", 2).value(Float.NaN);
        builder.cell().label("x", 2).label("y", 3).value(-Float.NaN);
        Tensor expected = builder.build();

        String denseJson = "{\"values\":\""
            +"42280000"+"49800008"+"35800000"+"c37f0000"
            +"00000000"+"80000000"+"00000001"+"7f7fffff"
            +"7f800000"+"ff800000"+"7fc00000"+"ffc00000"
            +"\"}";
        Tensor decoded = JsonFormat.decode(expected.type(), denseJson.getBytes(StandardCharsets.UTF_8));
        assertEquals(expected, decoded);
    }

    @Test
    public void testDoubleVectorInHexForm() {
        var builder = Tensor.Builder.of(TensorType.fromSpec("tensor<double>(x[3],y[4])"));
        builder.cell().label("x", 0).label("y", 0).value(42.0);
        builder.cell().label("x", 0).label("y", 1).value(1048577.0);
        builder.cell().label("x", 0).label("y", 2).value(0.00000095367431640625);
        builder.cell().label("x", 0).label("y", 3).value(-255.00);

        builder.cell().label("x", 1).label("y", 0).value(0.0);
        builder.cell().label("x", 1).label("y", 1).value(-0.0);
        builder.cell().label("x", 1).label("y", 2).value(Double.MIN_VALUE);
        builder.cell().label("x", 1).label("y", 3).value(Double.MAX_VALUE);

        builder.cell().label("x", 2).label("y", 0).value(Double.POSITIVE_INFINITY);
        builder.cell().label("x", 2).label("y", 1).value(Double.NEGATIVE_INFINITY);
        builder.cell().label("x", 2).label("y", 2).value(Double.NaN);
        builder.cell().label("x", 2).label("y", 3).value(-Double.NaN);
        Tensor expected = builder.build();

        String denseJson = "{\"values\":\""
            +"4045000000000000"+"4130000100000000"+"3eb0000000000000"+"c06fe00000000000"
            +"0000000000000000"+"8000000000000000"+"0000000000000001"+"7fefffffffffffff"
            +"7ff0000000000000"+"fff0000000000000"+"7ff8000000000000"+"fff8000000000000"
            +"\"}";
        Tensor decoded = JsonFormat.decode(expected.type(), denseJson.getBytes(StandardCharsets.UTF_8));
        assertEquals(expected, decoded);
    }

    @Test
    public void testMixedTensorInMixedForm() {
        Tensor.Builder builder = Tensor.Builder.of(TensorType.fromSpec("tensor(x{},y[3])"));
        builder.cell().label("x", 0).label("y", 0).value(2.0);
        builder.cell().label("x", 0).label("y", 1).value(3.0);
        builder.cell().label("x", 0).label("y", 2).value(4.0);
        builder.cell().label("x", 1).label("y", 0).value(5.0);
        builder.cell().label("x", 1).label("y", 1).value(6.0);
        builder.cell().label("x", 1).label("y", 2).value(7.0);
        Tensor expected = builder.build();
        String mixedJson = "{\"blocks\":[" +
                           "{\"address\":{\"x\":\"0\"},\"values\":[2.0,3.0,4.0]}," +
                           "{\"address\":{\"x\":\"1\"},\"values\":[5.0,6.0,7.0]}" +
                           "]}";
        Tensor decoded = JsonFormat.decode(expected.type(), mixedJson.getBytes(StandardCharsets.UTF_8));
        assertEquals(expected, decoded);
    }

    @Test
    public void testMixedTensorInMixedFormWithSingleSparseDimensionShortForm() {
        Tensor.Builder builder = Tensor.Builder.of(TensorType.fromSpec("tensor(x{},y[3])"));
        builder.cell().label("x", 0).label("y", 0).value(2.0);
        builder.cell().label("x", 0).label("y", 1).value(3.0);
        builder.cell().label("x", 0).label("y", 2).value(4.0);
        builder.cell().label("x", 1).label("y", 0).value(5.0);
        builder.cell().label("x", 1).label("y", 1).value(6.0);
        builder.cell().label("x", 1).label("y", 2).value(7.0);
        Tensor expected = builder.build();
        String mixedJson = "{\"blocks\":{" +
                           "\"0\":[2.0,3.0,4.0]," +
                           "\"1\":[5.0,6.0,7.0]" +
                           "}}";
        Tensor decoded = JsonFormat.decode(expected.type(), mixedJson.getBytes(StandardCharsets.UTF_8));
        assertEquals(expected, decoded);
    }

    @Test
    public void testTooManyCells() {
        TensorType x2 = TensorType.fromSpec("tensor(x[2])");
        String json = "{\"cells\":[" +
                      "{\"address\":{\"x\":\"0\"},\"value\":2.0}," +
                      "{\"address\":{\"x\":\"1\"},\"value\":3.0}," +
                      "{\"address\":{\"x\":\"2\"},\"value\":5.0}" +
                      "]}";
        try {
            JsonFormat.decode(x2, json.getBytes(StandardCharsets.UTF_8));
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("cell address (2) is not within the bounds of tensor(x[2])", e.getMessage());
        }
    }

    private void assertEncodeDecode(Tensor tensor) {
        Tensor decoded = JsonFormat.decode(tensor.type(), JsonFormat.encode(tensor, false, false));
        assertEquals(tensor, decoded);
        assertEquals(tensor.type(), decoded.type());
    }

    @Test
    public void testTensorCellTypes() {
        assertEncodeDecode(Tensor.from("tensor(x[2],y[2]):[2.0, 3.0, 5.0 ,8.0]"));
        assertEncodeDecode(Tensor.from("tensor<double>(x[2],y[2]):[2.0, 3.0, 5.0 ,8.0]"));
        assertEncodeDecode(Tensor.from("tensor<float>(x[2],y[2]):[2.0, 3.0, 5.0 ,8.0]"));
        assertEncodeDecode(Tensor.from("tensor<bfloat16>(x[2],y[2]):[2.0, 3.0, 5.0 ,8.0]"));
        assertEncodeDecode(Tensor.from("tensor<int8>(x[2],y[2]):[2,3,5,8]"));
    }

    /** All cell types are rendered as double. */
    @Test
    public void testTensorCellTypeRenderingPrecision() {
        assertEncodeShortForm(Tensor.Builder.of("tensor<double>(x[1])").cell(1.0/3, 0).build(),
                              "{\"type\":\"tensor(x[1])\",\"values\":[0.3333333333333333]}");
        assertEncodeShortForm(Tensor.Builder.of("tensor<float>(x[1])").cell((float)1.0/3, 0).build(),
                              "{\"type\":\"tensor<float>(x[1])\",\"values\":[0.3333333432674408]}");
    }

    private void assertEncodeShortForm(String tensor, String expected) {
        assertEncodeShortForm(Tensor.from(tensor), expected);
    }

    private void assertEncodeShortForm(Tensor tensor, String expected) {
        byte[] json = JsonFormat.encode(tensor, true, false);
        assertEquals(expected, new String(json, StandardCharsets.UTF_8));
    }

    private void assertDecoded(String expected, String jsonToDecode) {
        assertDecoded(Tensor.from(expected), jsonToDecode);
    }

    private void assertDecoded(Tensor expected, String jsonToDecode) {
        assertEquals(expected, JsonFormat.decode(expected.type(), jsonToDecode.getBytes(StandardCharsets.UTF_8)));
    }

    private void assertDecodeFails(TensorType type, String format, String msg) {
        try {
            Tensor decoded = JsonFormat.decode(type, format.getBytes(StandardCharsets.UTF_8));
            fail("Did not get exception as expected, decoded as: " + decoded);
        } catch (IllegalArgumentException e) {
            assertEquals(msg, e.getMessage());
        }
    }

    private void assertEqualJson(String expected, String generated) {
        Assertions.assertEquals(JSON.canonical(expected), JSON.canonical(generated));
    }

}
