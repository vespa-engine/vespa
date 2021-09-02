// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.serialization;

import com.yahoo.tensor.IndexedTensor;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author bratseth
 */
public class JsonFormatTestCase {

    @Test
    public void testSparseTensor() {
        Tensor.Builder builder = Tensor.Builder.of(TensorType.fromSpec("tensor(x{},y{})"));
        builder.cell().label("x", "a").label("y", "b").value(2.0);
        builder.cell().label("x", "c").label("y", "d").value(3.0);
        Tensor tensor = builder.build();
        byte[] json = JsonFormat.encode(tensor);
        assertEquals("{\"cells\":[" +
                     "{\"address\":{\"x\":\"a\",\"y\":\"b\"},\"value\":2.0}," +
                     "{\"address\":{\"x\":\"c\",\"y\":\"d\"},\"value\":3.0}" +
                     "]}",
                     new String(json, StandardCharsets.UTF_8));
        Tensor decoded = JsonFormat.decode(tensor.type(), json);
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
    public void testDenseTensor() {
        Tensor.Builder builder = Tensor.Builder.of(TensorType.fromSpec("tensor(x[2],y[2])"));
        builder.cell().label("x", 0).label("y", 0).value(2.0);
        builder.cell().label("x", 0).label("y", 1).value(3.0);
        builder.cell().label("x", 1).label("y", 0).value(5.0);
        builder.cell().label("x", 1).label("y", 1).value(7.0);
        Tensor tensor = builder.build();
        byte[] json = JsonFormat.encode(tensor);
        assertEquals("{\"cells\":[" +
                     "{\"address\":{\"x\":\"0\",\"y\":\"0\"},\"value\":2.0}," +
                     "{\"address\":{\"x\":\"0\",\"y\":\"1\"},\"value\":3.0}," +
                     "{\"address\":{\"x\":\"1\",\"y\":\"0\"},\"value\":5.0}," +
                     "{\"address\":{\"x\":\"1\",\"y\":\"1\"},\"value\":7.0}" +
                     "]}",
                     new String(json, StandardCharsets.UTF_8));
        Tensor decoded = JsonFormat.decode(tensor.type(), json);
        assertEquals(tensor, decoded);
    }

    @Test
    public void testDisallowedEmptyDenseTensor() {
        TensorType type = TensorType.fromSpec("tensor(x[3])");
        assertDecodeFails(type, "{\"values\":[]}", "The 'values' array does not contain any values");
        assertDecodeFails(type, "{\"values\":\"\"}", "The 'values' string does not contain any values");
    }

    @Test
    public void testDisallowedEmptyMixedTensor() {
        TensorType type = TensorType.fromSpec("tensor(x{},y[3])");
        assertDecodeFails(type, "{\"blocks\":{ \"a\": [] } }", "The 'block' value array does not contain any values");
        assertDecodeFails(type, "{\"blocks\":[ {\"address\":{\"x\":\"a\"}, \"values\": [] } ] }",
                "The 'block' value array does not contain any values");
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
    public void testDenseTensorShortForm() {
        assertEncodeShortForm("tensor(x[]):[1.0, 2.0]",
                              "{\"type\":\"tensor(x[])\",\"value\":[1.0,2.0]}");
        assertEncodeShortForm("tensor<float>(x[]):[1.0, 2.0]",
                              "{\"type\":\"tensor<float>(x[])\",\"value\":[1.0,2.0]}");
        assertEncodeShortForm("tensor(x[],y[]):[[1,2,3,4]]",
                              "{\"type\":\"tensor(x[],y[])\",\"value\":[[1.0,2.0,3.0,4.0]]}");
        assertEncodeShortForm("tensor(x[],y[]):[[1,2],[3,4]]",
                              "{\"type\":\"tensor(x[],y[])\",\"value\":[[1.0,2.0],[3.0,4.0]]}");
        assertEncodeShortForm("tensor(x[],y[]):[[1],[2],[3],[4]]",
                              "{\"type\":\"tensor(x[],y[])\",\"value\":[[1.0],[2.0],[3.0],[4.0]]}");
        assertEncodeShortForm("tensor(x[],y[],z[]):[[[1,2],[3,4]]]",
                              "{\"type\":\"tensor(x[],y[],z[])\",\"value\":[[[1.0,2.0],[3.0,4.0]]]}");
        assertEncodeShortForm("tensor(x[],y[],z[]):[[[1],[2],[3],[4]]]",
                              "{\"type\":\"tensor(x[],y[],z[])\",\"value\":[[[1.0],[2.0],[3.0],[4.0]]]}");
        assertEncodeShortForm("tensor(x[],y[],z[]):[[[1,2,3,4]]]",
                              "{\"type\":\"tensor(x[],y[],z[])\",\"value\":[[[1.0,2.0,3.0,4.0]]]}");
        assertEncodeShortForm("tensor(x[],y[],z[]):[[[1]],[[2]],[[3]],[[4]]]",
                              "{\"type\":\"tensor(x[],y[],z[])\",\"value\":[[[1.0]],[[2.0]],[[3.0]],[[4.0]]]}");
        assertEncodeShortForm("tensor(x[],y[],z[2]):[[[1, 2]],[[3, 4]]]",
                              "{\"type\":\"tensor(x[],y[],z[2])\",\"value\":[[[1.0,2.0]],[[3.0,4.0]]]}");
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
            fail("Excpected exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("cell address (2) is not within the bounds of tensor(x[2])", e.getMessage());
        }
    }

    private void assertEncodeDecode(Tensor tensor) {
        Tensor decoded = JsonFormat.decode(tensor.type(), JsonFormat.encodeWithType(tensor));
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

    private void assertEncodeShortForm(String tensor, String expected) {
        byte[] json = JsonFormat.encodeShortForm((IndexedTensor) Tensor.from(tensor));
        assertEquals(expected, new String(json, StandardCharsets.UTF_8));
    }

    private void assertDecodeFails(TensorType type, String format, String msg) {
        try {
            Tensor decoded = JsonFormat.decode(type, format.getBytes(StandardCharsets.UTF_8));
            fail("Did not get exception as expected, decoded as: " + decoded);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), msg);
        }
    }

}
