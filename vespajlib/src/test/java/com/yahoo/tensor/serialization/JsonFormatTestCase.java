package com.yahoo.tensor.serialization;

import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class JsonFormatTestCase {

    @Test
    public void testJsonEncodingOfSparseTensor() {
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
    }

    @Test
    public void testJsonEncodingOfDenseTensor() {
        Tensor.Builder builder = Tensor.Builder.of(TensorType.fromSpec("tensor(x{},y{})"));
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
    }

}
