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
     * Test data holder for tensor type combinations
     */
    private static class TensorTypeTestData {
        final String name;
        final TensorType type;
        final String sampleTensorString;
        final int indexedDims;
        final int mappedDims;

        TensorTypeTestData(String name, String typeSpec, String sampleTensorString, int indexedDims, int mappedDims) {
            this.name = name;
            this.type = TensorType.fromSpec(typeSpec);
            this.sampleTensorString = sampleTensorString;
            this.indexedDims = indexedDims;
            this.mappedDims = mappedDims;
        }

        Tensor createSampleTensor() {
            return Tensor.from(sampleTensorString);
        }
    }

    /**
     * All combinations of 0, 1, and 2 indexed dimensions with 0, 1, and 2 mapped dimensions
     */
    private static final List<TensorTypeTestData> ALL_TYPE_COMBINATIONS = List.of(
            // 0 indexed, 0 mapped (scalar)
            new TensorTypeTestData(
                    "scalar",
                    "tensor()",
                    "tensor():{5.0}",
                    0, 0
            ),
            // 0 indexed, 1 mapped (sparse 1D)
            new TensorTypeTestData(
                    "sparse_1d",
                    "tensor(cat{})",
                    "tensor(cat{}):{a:1.0, b:2.0, c:3.0}",
                    0, 1
            ),
            // 0 indexed, 2 mapped (sparse 2D)
            new TensorTypeTestData(
                    "sparse_2d",
                    "tensor(cat{},key{})",
                    "tensor(cat{},key{}):{{cat:a,key:p}:1.0, {cat:b,key:q}:2.0, {cat:c,key:r}:3.0}",
                    0, 2
            ),
            // 1 indexed, 0 mapped (dense 1D)
            new TensorTypeTestData(
                    "dense_1d",
                    "tensor(x[3])",
                    "tensor(x[3]):[1.0, 2.0, 3.0]",
                    1, 0
            ),
            // 1 indexed, 1 mapped (mixed with 1 mapped, 1 indexed)
            new TensorTypeTestData(
                    "mixed_1m_1i",
                    "tensor(cat{},x[3])",
                    "tensor(cat{},x[3]):{a:[1.0, 2.0, 3.0], b:[4.0, 5.0, 6.0]}",
                    1, 1
            ),
            // 1 indexed, 2 mapped (mixed with 2 mapped, 1 indexed)
            new TensorTypeTestData(
                    "mixed_2m_1i",
                    "tensor(cat{},key{},x[3])",
                    "tensor(cat{},key{},x[3]):{{cat:a,key:p,x:0}:1.0, {cat:a,key:p,x:1}:2.0, {cat:a,key:p,x:2}:3.0, {cat:b,key:q,x:0}:4.0, {cat:b,key:q,x:1}:5.0, {cat:b,key:q,x:2}:6.0}",
                    1, 2
            ),
            // 2 indexed, 0 mapped (dense 2D)
            new TensorTypeTestData(
                    "dense_2d",
                    "tensor(x[2],y[3])",
                    "tensor(x[2],y[3]):[[1.0, 2.0, 3.0], [4.0, 5.0, 6.0]]",
                    2, 0
            ),
            // 2 indexed, 1 mapped (mixed with 1 mapped, 2 indexed)
            new TensorTypeTestData(
                    "mixed_1m_2i",
                    "tensor(cat{},x[2],y[3])",
                    "tensor(cat{},x[2],y[3]):{{cat:a,x:0,y:0}:1.0, {cat:a,x:0,y:1}:2.0, {cat:a,x:0,y:2}:3.0, {cat:a,x:1,y:0}:4.0, {cat:a,x:1,y:1}:5.0, {cat:a,x:1,y:2}:6.0, {cat:b,x:0,y:0}:7.0, {cat:b,x:0,y:1}:8.0, {cat:b,x:0,y:2}:9.0, {cat:b,x:1,y:0}:10.0, {cat:b,x:1,y:1}:11.0, {cat:b,x:1,y:2}:12.0}",
                    2, 1
            ),
            // 2 indexed, 2 mapped (mixed with 2 mapped, 2 indexed)
            new TensorTypeTestData(
                    "mixed_2m_2i",
                    "tensor(cat{},key{},x[2],y[2])",
                    "tensor(cat{},key{},x[2],y[2]):{{cat:a,key:p,x:0,y:0}:1.0, {cat:a,key:p,x:0,y:1}:2.0, {cat:a,key:p,x:1,y:0}:3.0, {cat:a,key:p,x:1,y:1}:4.0, {cat:b,key:q,x:0,y:0}:5.0, {cat:b,key:q,x:0,y:1}:6.0, {cat:b,key:q,x:1,y:0}:7.0, {cat:b,key:q,x:1,y:1}:8.0}",
                    2, 2
            )
    );

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
            if (indent > 0) {
                indent--;
            }
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
            if (indent > 0) {
                indent--;
            }
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
            String result = output.toString();
            System.out.println("MockDataSink output:\n" + result);
            return result;
        }
    }

    @Test
    public void testSimpleIndexedTensor() {
        Tensor tensor = Tensor.from("tensor(x[3]):[1.0, 2.0, 3.0]");
        TensorDataSource dataSource = new TensorDataSource(tensor, new JsonFormat.EncodeOptions(true, false, false));

        Slime slime = SlimeDataSink.buildSlime(dataSource);
        String json = SlimeUtils.toJson(slime);
        System.out.println("testSimpleIndexedTensor: " + json);

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

        Slime slime = SlimeDataSink.buildSlime(dataSource);
        String json = SlimeUtils.toJson(slime);
        System.out.println("testSimpleIndexedTensorDirectValues: " + json);

        MockDataSink sink = new MockDataSink();
        dataSource.emit(sink);

        String output = sink.getOutput();
        assertTrue("Should contain values", output.contains("1.0") || output.contains("2.0") || output.contains("3.0"));
    }

    @Test
    public void testMultiDimensionalIndexedTensor() {
        Tensor tensor = Tensor.from("tensor(x[2],y[2]):[[1.0, 2.0], [3.0, 4.0]]");
        TensorDataSource dataSource = new TensorDataSource(tensor, new JsonFormat.EncodeOptions(true, false, false));

        Slime slime = SlimeDataSink.buildSlime(dataSource);
        String json = SlimeUtils.toJson(slime);
        System.out.println("testMultiDimensionalIndexedTensor: " + json);

        MockDataSink sink = new MockDataSink();
        dataSource.emit(sink);

        String output = sink.getOutput();
        assertTrue("Should contain nested arrays", output.contains("["));
    }

    @Test
    public void testSingleDimensionMappedTensor() {
        Tensor tensor = Tensor.from("tensor(x{}):{a:2.0, b:3.0}");
        TensorDataSource dataSource = new TensorDataSource(tensor, new JsonFormat.EncodeOptions(true, false, false));

        Slime slime = SlimeDataSink.buildSlime(dataSource);
        String json = SlimeUtils.toJson(slime);
        System.out.println("testSingleDimensionMappedTensor: " + json);

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

        Slime slime = SlimeDataSink.buildSlime(dataSource);
        String json = SlimeUtils.toJson(slime);
        System.out.println("testMultiDimensionMappedTensor: " + json);

        MockDataSink sink = new MockDataSink();
        dataSource.emit(sink);

        String output = sink.getOutput();
        assertTrue("Should contain cells field", output.contains("field:cells"));
    }

    @Test
    public void testMixedTensorSingleMappedDimension() {
        Tensor tensor = Tensor.from("tensor(x{},y[2]):{a:[1.0, 2.0], b:[3.0, 4.0]}");
        TensorDataSource dataSource = new TensorDataSource(tensor, new JsonFormat.EncodeOptions(true, false, false));

        Slime slime = SlimeDataSink.buildSlime(dataSource);
        String json = SlimeUtils.toJson(slime);
        System.out.println("testMixedTensorSingleMappedDimension: " + json);

        MockDataSink sink = new MockDataSink();
        dataSource.emit(sink);

        String output = sink.getOutput();
        assertTrue("Should contain blocks field", output.contains("field:blocks"));
    }

    @Test
    public void testMixedTensorMultipleMappedDimensions() {
        Tensor tensor = Tensor.from("tensor(x{},y{},z[2]):{{x:a,y:b,z:0}:1.0, {x:a,y:b,z:1}:2.0, {x:c,y:d,z:0}:3.0, {x:c,y:d,z:1}:4.0}");
        TensorDataSource dataSource = new TensorDataSource(tensor, new JsonFormat.EncodeOptions(true, false, false));

        Slime slime = SlimeDataSink.buildSlime(dataSource);
        String json = SlimeUtils.toJson(slime);
        System.out.println("testMixedTensorMultipleMappedDimensions: " + json);

        MockDataSink sink = new MockDataSink();
        dataSource.emit(sink);

        String output = sink.getOutput();
        assertTrue("Should contain blocks field", output.contains("field:blocks"));
    }

    @Test
    public void testLongForm() {
        Tensor tensor = Tensor.from("tensor(x[2]):[1.0, 2.0]");
        TensorDataSource dataSource = new TensorDataSource(tensor, new JsonFormat.EncodeOptions(false, false, false));

        Slime slime = SlimeDataSink.buildSlime(dataSource);
        String json = SlimeUtils.toJson(slime);
        System.out.println("testLongForm: " + json);

        MockDataSink sink = new MockDataSink();
        dataSource.emit(sink);

        String output = sink.getOutput();
        assertTrue("Should contain cells field in long form", output.contains("field:cells"));
    }

    @Test
    public void testHexEncodingForDenseTensor() {
        Tensor tensor = Tensor.from("tensor(x[3]):[1.0, 2.0, 3.0]");
        TensorDataSource dataSource = new TensorDataSource(tensor, new JsonFormat.EncodeOptions(true, false, true));

        Slime slime = SlimeDataSink.buildSlime(dataSource);
        String json = SlimeUtils.toJson(slime);
        System.out.println("testHexEncodingForDenseTensor: " + json);

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

        Slime slime = SlimeDataSink.buildSlime(dataSource);
        String json = SlimeUtils.toJson(slime);
        System.out.println("testHexEncodingForMixedTensor: " + json);

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

        Slime slime = SlimeDataSink.buildSlime(dataSource);
        String json = SlimeUtils.toJson(slime);
        System.out.println("testEmptyMappedTensor: " + json);

        MockDataSink sink = new MockDataSink();
        dataSource.emit(sink);

        String output = sink.getOutput();
        assertTrue("Should handle empty tensor", output.contains("field:cells"));
    }

    @Test
    public void testScalarTensor() {
        Tensor tensor = Tensor.from("tensor():{5.7}");
        TensorDataSource dataSource = new TensorDataSource(tensor, new JsonFormat.EncodeOptions(true, false, false));

        Slime slime = SlimeDataSink.buildSlime(dataSource);
        String json = SlimeUtils.toJson(slime);
        System.out.println("testScalarTensor: " + json);

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

        Slime slime = SlimeDataSink.buildSlime(dataSource);
        String json = SlimeUtils.toJson(slime);
        System.out.println("testComplexMixedTensorWithAddressedBlocks: " + json);

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

    @Test
    public void testAllTypeCombinationsShortForm() {
        // Test all combinations of indexed and mapped dimensions with short form encoding
        System.out.println("\n=== testAllTypeCombinationsShortForm ===");
        for (TensorTypeTestData testData : ALL_TYPE_COMBINATIONS) {
            Tensor tensor = testData.createSampleTensor();
            TensorDataSource dataSource = new TensorDataSource(tensor, new JsonFormat.EncodeOptions(true, false, false));

            // Verify the output is valid by checking it can be converted to Slime
            Slime slime = SlimeDataSink.buildSlime(dataSource);
            String json = SlimeUtils.toJson(slime);
            System.out.println(testData.name + ": " + json);

            MockDataSink sink = new MockDataSink();
            dataSource.emit(sink);

            String output = sink.getOutput();
            assertTrue("Should produce output for " + testData.name, output.length() > 0);
            assertTrue("Should contain type field for " + testData.name, output.contains("field:type"));
            assertTrue("Should contain type in JSON for " + testData.name, json.contains("type"));
        }
    }

    @Test
    public void testAllTypeCombinationsLongForm() {
        // Test all combinations with long form encoding
        System.out.println("\n=== testAllTypeCombinationsLongForm ===");
        for (TensorTypeTestData testData : ALL_TYPE_COMBINATIONS) {
            Tensor tensor = testData.createSampleTensor();
            TensorDataSource dataSource = new TensorDataSource(tensor, new JsonFormat.EncodeOptions(false, false, false));

            Slime slime = SlimeDataSink.buildSlime(dataSource);
            String json = SlimeUtils.toJson(slime);
            System.out.println(testData.name + ": " + json);

            MockDataSink sink = new MockDataSink();
            dataSource.emit(sink);

            String output = sink.getOutput();
            assertTrue("Should produce output for " + testData.name, output.length() > 0);
            assertTrue("Should contain cells in long form for " + testData.name, output.contains("field:cells"));
        }
    }

    @Test
    public void testAllTypeCombinationsDirectValues() {
        // Test all combinations with direct values (no wrapping)
        System.out.println("\n=== testAllTypeCombinationsDirectValues ===");
        for (TensorTypeTestData testData : ALL_TYPE_COMBINATIONS) {
            Tensor tensor = testData.createSampleTensor();
            TensorDataSource dataSource = new TensorDataSource(tensor, new JsonFormat.EncodeOptions(true, true, false));

            Slime slime = SlimeDataSink.buildSlime(dataSource);
            String json = SlimeUtils.toJson(slime);
            System.out.println(testData.name + ": " + json);

            MockDataSink sink = new MockDataSink();
            dataSource.emit(sink);

            String output = sink.getOutput();
            assertTrue("Should produce output for " + testData.name, output.length() > 0);
            // With directValues=true, should not have wrapper object
            assertTrue("Should not contain type field wrapper for " + testData.name, !output.contains("field:type"));
        }
    }

    @Test
    public void testAllTypeCombinationsWithHexEncoding() {
        // Test combinations that support hex encoding (those with indexed dimensions)
        System.out.println("\n=== testAllTypeCombinationsWithHexEncoding ===");
        for (TensorTypeTestData testData : ALL_TYPE_COMBINATIONS) {
            if (testData.indexedDims > 0) {
                Tensor tensor = testData.createSampleTensor();
                TensorDataSource dataSource = new TensorDataSource(tensor, new JsonFormat.EncodeOptions(true, false, true));

                Slime slime = SlimeDataSink.buildSlime(dataSource);
                String json = SlimeUtils.toJson(slime);
                System.out.println(testData.name + ": " + json);

                MockDataSink sink = new MockDataSink();
                dataSource.emit(sink);

                String output = sink.getOutput();
                assertTrue("Should produce output for " + testData.name, output.length() > 0);
                // Hex encoding should produce string values for dense parts
                assertTrue("Should contain string values for hex encoding in " + testData.name, output.contains("\""));
            }
        }
    }

    @Test
    public void testTypeCombinationCounts() {
        // Verify we have all 9 combinations
        assertEquals("Should have 9 type combinations (3x3)", 9, ALL_TYPE_COMBINATIONS.size());

        // Verify the distribution
        long scalarCount = ALL_TYPE_COMBINATIONS.stream().filter(t -> t.indexedDims == 0 && t.mappedDims == 0).count();
        long sparseCount = ALL_TYPE_COMBINATIONS.stream().filter(t -> t.indexedDims == 0 && t.mappedDims > 0).count();
        long denseCount = ALL_TYPE_COMBINATIONS.stream().filter(t -> t.indexedDims > 0 && t.mappedDims == 0).count();
        long mixedCount = ALL_TYPE_COMBINATIONS.stream().filter(t -> t.indexedDims > 0 && t.mappedDims > 0).count();

        assertEquals("Should have 1 scalar type", 1, scalarCount);
        assertEquals("Should have 2 sparse types", 2, sparseCount);
        assertEquals("Should have 2 dense types", 2, denseCount);
        assertEquals("Should have 4 mixed types", 4, mixedCount);
    }
}
