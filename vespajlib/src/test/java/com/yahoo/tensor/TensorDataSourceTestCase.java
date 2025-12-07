// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor;

import com.yahoo.data.disclosure.DataSink;
import com.yahoo.data.disclosure.slime.SlimeDataSink;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.tensor.serialization.JsonFormat;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests for TensorDataSource
 *
 * @author arnej
 */
public class TensorDataSourceTestCase {

    /**
     * A simple mock DataSink that captures method calls for testing
     */
    private static class MockDataSink implements DataSink {
        private final StringBuilder output = new StringBuilder();
        private int indent = 0;

        private void indent() {
            output.append("  ".repeat(indent));
        }

        @Override
        public void fieldName(String utf16, byte[] utf8) {
            indent();
            output.append("field:").append(utf16 != null ? utf16 : new String(utf8, StandardCharsets.UTF_8)).append("\n");
        }

        @Override
        public void startObject() {
            indent();
            output.append("{\n");
            indent++;
        }

        @Override
        public void endObject() {
            indent--;
            indent();
            output.append("}\n");
        }

        @Override
        public void startArray() {
            indent();
            output.append("[\n");
            indent++;
        }

        @Override
        public void endArray() {
            indent--;
            indent();
            output.append("]\n");
        }

        @Override
        public void emptyValue() {
            indent();
            output.append("null\n");
        }

        @Override
        public void booleanValue(boolean v) {
            indent();
            output.append(v).append("\n");
        }

        @Override
        public void longValue(long v) {
            indent();
            output.append(v).append("L\n");
        }

        @Override
        public void doubleValue(double v) {
            indent();
            output.append(v).append("\n");
        }

        @Override
        public void stringValue(String utf16, byte[] utf8) {
            indent();
            output.append("\"").append(utf16 != null ? utf16 : new String(utf8, StandardCharsets.UTF_8)).append("\"\n");
        }

        @Override
        public void dataValue(byte[] data) {
            indent();
            output.append("data[").append(data.length).append("]\n");
        }

        public String getOutput() {
            return output.toString();
        }
    }

    @Test
    public void testSimpleIndexedTensor() {
        Tensor tensor = Tensor.from("tensor(x[3]):[1.0, 2.0, 3.0]");
        TensorDataSource dataSource = new TensorDataSource(tensor, new JsonFormat.EncodeOptions(true, false, false));

        MockDataSink sink = new MockDataSink();
        dataSource.emit(sink);

        String output = sink.getOutput();
        assertTrue("Should start with object", output.contains("{"));
        assertTrue("Should contain type field", output.contains("field:type"));
        assertTrue("Should contain values field", output.contains("field:values"));
        assertTrue("Should contain array", output.contains("["));
    }

    @Test
    public void testSimpleIndexedTensorDirectValues() {
        Tensor tensor = Tensor.from("tensor(x[3]):[1.0, 2.0, 3.0]");
        TensorDataSource dataSource = new TensorDataSource(tensor, new JsonFormat.EncodeOptions(true, true, false));

        MockDataSink sink = new MockDataSink();
        dataSource.emit(sink);

        String output = sink.getOutput();
        assertTrue("Should contain values", output.contains("1.0") || output.contains("2.0") || output.contains("3.0"));
    }

    @Test
    public void testMultiDimensionalIndexedTensor() {
        Tensor tensor = Tensor.from("tensor(x[2],y[2]):[[1.0, 2.0], [3.0, 4.0]]");
        TensorDataSource dataSource = new TensorDataSource(tensor, new JsonFormat.EncodeOptions(true, false, false));

        MockDataSink sink = new MockDataSink();
        dataSource.emit(sink);

        String output = sink.getOutput();
        assertTrue("Should contain nested arrays", output.contains("["));
    }

    @Test
    public void testSingleDimensionMappedTensor() {
        Tensor tensor = Tensor.from("tensor(x{}):{a:2.0, b:3.0}");
        TensorDataSource dataSource = new TensorDataSource(tensor, new JsonFormat.EncodeOptions(true, false, false));

        MockDataSink sink = new MockDataSink();
        dataSource.emit(sink);

        String output = sink.getOutput();
        assertTrue("Should contain cells field", output.contains("field:cells"));
        assertTrue("Should contain object", output.contains("{"));
    }

    @Test
    public void testMultiDimensionMappedTensor() {
        Tensor tensor = Tensor.from("tensor(x{},y{}):{{x:a,y:1}:2.0, {x:b,y:1}:3.0}");
        TensorDataSource dataSource = new TensorDataSource(tensor, new JsonFormat.EncodeOptions(true, false, false));

        MockDataSink sink = new MockDataSink();
        dataSource.emit(sink);

        String output = sink.getOutput();
        assertTrue("Should contain cells field", output.contains("field:cells"));
    }

    @Test
    public void testMixedTensorSingleMappedDimension() {
        Tensor tensor = Tensor.from("tensor(x{},y[2]):{a:[1.0, 2.0], b:[3.0, 4.0]}");
        TensorDataSource dataSource = new TensorDataSource(tensor, new JsonFormat.EncodeOptions(true, false, false));

        MockDataSink sink = new MockDataSink();
        dataSource.emit(sink);

        String output = sink.getOutput();
        assertTrue("Should contain blocks field", output.contains("field:blocks"));
    }

    @Test
    public void testMixedTensorMultipleMappedDimensions() {
        Tensor tensor = Tensor.from("tensor(x{},y{},z[2]):{{x:a,y:b,z:0}:1.0, {x:a,y:b,z:1}:2.0, {x:c,y:d,z:0}:3.0, {x:c,y:d,z:1}:4.0}");
        TensorDataSource dataSource = new TensorDataSource(tensor, new JsonFormat.EncodeOptions(true, false, false));

        MockDataSink sink = new MockDataSink();
        dataSource.emit(sink);

        String output = sink.getOutput();
        assertTrue("Should contain blocks field", output.contains("field:blocks"));
    }

    @Test
    public void testLongForm() {
        Tensor tensor = Tensor.from("tensor(x[2]):[1.0, 2.0]");
        TensorDataSource dataSource = new TensorDataSource(tensor, new JsonFormat.EncodeOptions(false, false, false));

        MockDataSink sink = new MockDataSink();
        dataSource.emit(sink);

        String output = sink.getOutput();
        assertTrue("Should contain cells field in long form", output.contains("field:cells"));
    }

    @Test
    public void testHexEncodingForDenseTensor() {
        Tensor tensor = Tensor.from("tensor(x[3]):[1.0, 2.0, 3.0]");
        TensorDataSource dataSource = new TensorDataSource(tensor, new JsonFormat.EncodeOptions(true, false, true));

        MockDataSink sink = new MockDataSink();
        dataSource.emit(sink);

        String output = sink.getOutput();
        assertTrue("Should contain values field", output.contains("field:values"));
        assertTrue("Should contain hex string", output.contains("\""));
    }

    @Test
    public void testHexEncodingForMixedTensor() {
        Tensor tensor = Tensor.from("tensor(x{},y[2]):{a:[1.0, 2.0], b:[3.0, 4.0]}");
        TensorDataSource dataSource = new TensorDataSource(tensor, new JsonFormat.EncodeOptions(true, false, true));

        MockDataSink sink = new MockDataSink();
        dataSource.emit(sink);

        String output = sink.getOutput();
        assertTrue("Should contain blocks field", output.contains("field:blocks"));
    }

    @Test
    public void testEmptyMappedTensor() {
        TensorType type = TensorType.fromSpec("tensor(x{})");
        Tensor tensor = Tensor.Builder.of(type).build();
        TensorDataSource dataSource = new TensorDataSource(tensor, new JsonFormat.EncodeOptions(true, false, false));

        MockDataSink sink = new MockDataSink();
        dataSource.emit(sink);

        String output = sink.getOutput();
        assertTrue("Should handle empty tensor", output.contains("field:cells"));
    }

    @Test
    public void testScalarTensor() {
        Tensor tensor = Tensor.from("tensor():{5.7}");
        TensorDataSource dataSource = new TensorDataSource(tensor, new JsonFormat.EncodeOptions(true, false, false));

        MockDataSink sink = new MockDataSink();
        dataSource.emit(sink);

        String output = sink.getOutput();
        assertTrue("Should contain value", output.contains("5.7"));
    }

    @Test
    public void testWithSlimeDataSink() {
        Tensor tensor = Tensor.from("tensor(x[2]):[1.0, 2.0]");
        TensorDataSource dataSource = new TensorDataSource(tensor, new JsonFormat.EncodeOptions(true, false, false));

        Slime slime = SlimeDataSink.buildSlime(dataSource);

        String json = SlimeUtils.toJson(slime);
        assertTrue("Should produce valid JSON", json.contains("type"));
        assertTrue("Should contain tensor type", json.contains("tensor(x[2])"));
    }

    @Test
    public void testFloatValueType() {
        TensorType type = TensorType.fromSpec("tensor<float>(x[2])");
        Tensor tensor = Tensor.Builder.of(type)
                .cell(1.5f, 0)
                .cell(2.5f, 1)
                .build();
        TensorDataSource dataSource = new TensorDataSource(tensor, new JsonFormat.EncodeOptions(true, false, false));

        MockDataSink sink = new MockDataSink();
        dataSource.emit(sink);

        String output = sink.getOutput();
        assertTrue("Should contain values", output.contains("["));
    }

    @Test
    public void testInt8ValueType() {
        TensorType type = TensorType.fromSpec("tensor<int8>(x[2])");
        Tensor tensor = Tensor.Builder.of(type)
                .cell(1, 0)
                .cell(2, 1)
                .build();
        TensorDataSource dataSource = new TensorDataSource(tensor, new JsonFormat.EncodeOptions(true, false, false));

        MockDataSink sink = new MockDataSink();
        dataSource.emit(sink);

        String output = sink.getOutput();
        assertTrue("Should contain values", output.contains("["));
    }

    @Test
    public void testBfloat16ValueType() {
        TensorType type = TensorType.fromSpec("tensor<bfloat16>(x[2])");
        Tensor tensor = Tensor.Builder.of(type)
                .cell(1.5, 0)
                .cell(2.5, 1)
                .build();
        TensorDataSource dataSource = new TensorDataSource(tensor, new JsonFormat.EncodeOptions(true, false, false));

        MockDataSink sink = new MockDataSink();
        dataSource.emit(sink);

        String output = sink.getOutput();
        assertTrue("Should contain values", output.contains("["));
    }

    @Test
    public void testComplexMixedTensorWithAddressedBlocks() {
        Tensor tensor = Tensor.from("tensor(m{},n{},x[2]):{{m:a,n:b,x:0}:1.0, {m:a,n:b,x:1}:2.0, {m:c,n:d,x:0}:3.0, {m:c,n:d,x:1}:4.0}");
        TensorDataSource dataSource = new TensorDataSource(tensor, new JsonFormat.EncodeOptions(true, false, false));

        MockDataSink sink = new MockDataSink();
        dataSource.emit(sink);

        String output = sink.getOutput();
        assertTrue("Should contain blocks", output.contains("field:blocks"));
        assertTrue("Should contain address", output.contains("field:address"));
        assertTrue("Should contain values", output.contains("field:values"));
    }

    @Test
    public void testDifferentValueTypesInEmitValue() {
        TensorType doubleType = TensorType.fromSpec("tensor<double>(x[1])");
        Tensor doubleTensor = Tensor.Builder.of(doubleType).cell(1.5, 0).build();

        TensorType floatType = TensorType.fromSpec("tensor<float>(x[1])");
        Tensor floatTensor = Tensor.Builder.of(floatType).cell(1.5f, 0).build();

        TensorType int8Type = TensorType.fromSpec("tensor<int8>(x[1])");
        Tensor int8Tensor = Tensor.Builder.of(int8Type).cell(1, 0).build();

        for (Tensor tensor : List.of(doubleTensor, floatTensor, int8Tensor)) {
            TensorDataSource dataSource = new TensorDataSource(tensor, new JsonFormat.EncodeOptions(false, false, false));
            MockDataSink sink = new MockDataSink();
            dataSource.emit(sink);

            String output = sink.getOutput();
            assertTrue("Should contain cells", output.contains("field:cells"));
        }
    }
}
