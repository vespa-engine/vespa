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
import java.util.Iterator;
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
        final Tensor sampleTensor;
        final int indexedDims;
        final int mappedDims;
        final TensorType.Value cellType;

        TensorTypeTestData(String name, TensorType type, Tensor sampleTensor, int indexedDims, int mappedDims) {
            this.name = name;
            this.type = type;
            this.sampleTensor = sampleTensor;
            this.indexedDims = indexedDims;
            this.mappedDims = mappedDims;
            this.cellType = type.valueType();
        }

        Tensor createSampleTensor() {
            return sampleTensor;
        }
    }

    /**
     * Helper to create a sample tensor with given dimensions and values
     */
    private static Tensor createSampleTensor(TensorType type, List<TensorType.Dimension> dimensions) {
        Tensor.Builder builder = Tensor.Builder.of(type);

        if (dimensions.isEmpty()) {
            // Scalar tensor
            builder.cell(5.0);
        } else if (type.dimensions().stream().allMatch(TensorType.Dimension::isIndexed)) {
            // Pure dense tensor - add values in order
            int totalSize = 1;
            for (TensorType.Dimension dim : dimensions) {
                totalSize *= dim.size().get().intValue();
            }
            for (int i = 0; i < totalSize; i++) {
                builder.cell(i + 1.0, indexToAddress(i, dimensions));
            }
        } else if (type.dimensions().stream().allMatch(TensorType.Dimension::isMapped)) {
            // Pure sparse tensor
            if (dimensions.size() == 1) {
                builder.cell().label(dimensions.get(0).name(), "a").value(1.0);
                builder.cell().label(dimensions.get(0).name(), "b").value(2.0);
                builder.cell().label(dimensions.get(0).name(), "c").value(3.0);
            } else {
                builder.cell().label(dimensions.get(0).name(), "a").label(dimensions.get(1).name(), "p").value(1.0);
                builder.cell().label(dimensions.get(0).name(), "b").label(dimensions.get(1).name(), "q").value(2.0);
                builder.cell().label(dimensions.get(0).name(), "c").label(dimensions.get(1).name(), "r").value(3.0);
            }
        } else {
            // Mixed tensor
            List<TensorType.Dimension> mappedDims = type.dimensions().stream().filter(TensorType.Dimension::isMapped).toList();
            List<TensorType.Dimension> indexedDims = type.dimensions().stream().filter(TensorType.Dimension::isIndexed).toList();

            String[] labels = {"a", "b"};
            String[] labels2 = {"p", "q"};

            for (int labelIdx = 0; labelIdx < labels.length; labelIdx++) {
                int denseSize = 1;
                for (TensorType.Dimension dim : indexedDims) {
                    denseSize *= dim.size().get().intValue();
                }

                for (int i = 0; i < denseSize; i++) {
                    var cellBuilder = builder.cell();

                    // Add mapped dimensions
                    if (mappedDims.size() >= 1) {
                        cellBuilder.label(mappedDims.get(0).name(), labels[labelIdx]);
                    }
                    if (mappedDims.size() >= 2) {
                        cellBuilder.label(mappedDims.get(1).name(), labels2[labelIdx]);
                    }

                    // Add indexed dimensions
                    long[] addr = indexToAddress(i, indexedDims);
                    for (int j = 0; j < indexedDims.size(); j++) {
                        cellBuilder.label(indexedDims.get(j).name(), addr[j]);
                    }

                    cellBuilder.value(labelIdx * denseSize + i + 1.0);
                }
            }
        }

        return builder.build();
    }

    private static long[] indexToAddress(int index, List<TensorType.Dimension> dimensions) {
        long[] result = new long[dimensions.size()];
        for (int i = dimensions.size() - 1; i >= 0; i--) {
            int size = dimensions.get(i).size().get().intValue();
            result[i] = index % size;
            index /= size;
        }
        return result;
    }

    /**
     * Generate all valid combinations of tensor types
     * - Scalar (0 indexed, 0 mapped) only supports DOUBLE
     * - All other dimension combinations support all 4 cell types (DOUBLE, FLOAT, BFLOAT16, INT8)
     * Total: 1 + (8 × 4) = 33 combinations
     */
    private static List<TensorTypeTestData> generateAllTypeCombinations() {
        List<TensorTypeTestData> combinations = new ArrayList<>();

        // Define the 4 cell types to test
        TensorType.Value[] cellTypes = {
            TensorType.Value.DOUBLE,
            TensorType.Value.FLOAT,
            TensorType.Value.BFLOAT16,
            TensorType.Value.INT8
        };

        // Base dimension patterns
        record DimensionPattern(String baseName, List<TensorType.Dimension> dimensions, int indexedDims, int mappedDims) {}

        List<DimensionPattern> patterns = List.of(
            // 0 indexed, 0 mapped (scalar) - only DOUBLE
            new DimensionPattern("scalar", List.of(), 0, 0),

            // 0 indexed, 1 mapped (sparse 1D)
            new DimensionPattern("sparse_1d",
                List.of(TensorType.Dimension.mapped("cat")), 0, 1),

            // 0 indexed, 2 mapped (sparse 2D)
            new DimensionPattern("sparse_2d",
                List.of(TensorType.Dimension.mapped("cat"), TensorType.Dimension.mapped("key")), 0, 2),

            // 1 indexed, 0 mapped (dense 1D)
            new DimensionPattern("dense_1d",
                List.of(TensorType.Dimension.indexed("x", 3)), 1, 0),

            // 1 indexed, 1 mapped (mixed)
            new DimensionPattern("mixed_1m_1i",
                List.of(TensorType.Dimension.mapped("cat"), TensorType.Dimension.indexed("x", 3)), 1, 1),

            // 1 indexed, 2 mapped (mixed)
            new DimensionPattern("mixed_2m_1i",
                List.of(TensorType.Dimension.mapped("cat"), TensorType.Dimension.mapped("key"),
                        TensorType.Dimension.indexed("x", 3)), 1, 2),

            // 2 indexed, 0 mapped (dense 2D)
            new DimensionPattern("dense_2d",
                List.of(TensorType.Dimension.indexed("x", 2), TensorType.Dimension.indexed("y", 3)), 2, 0),

            // 2 indexed, 1 mapped (mixed)
            new DimensionPattern("mixed_1m_2i",
                List.of(TensorType.Dimension.mapped("cat"),
                        TensorType.Dimension.indexed("x", 2), TensorType.Dimension.indexed("y", 3)), 2, 1),

            // 2 indexed, 2 mapped (mixed)
            new DimensionPattern("mixed_2m_2i",
                List.of(TensorType.Dimension.mapped("cat"), TensorType.Dimension.mapped("key"),
                        TensorType.Dimension.indexed("x", 2), TensorType.Dimension.indexed("y", 2)), 2, 2)
        );

        // Generate combinations
        for (DimensionPattern pattern : patterns) {
            // Scalar only supports DOUBLE
            if (pattern.indexedDims == 0 && pattern.mappedDims == 0) {
                TensorType type = new TensorType(TensorType.Value.DOUBLE, pattern.dimensions);
                Tensor sampleTensor = createSampleTensor(type, pattern.dimensions);
                combinations.add(new TensorTypeTestData(
                    pattern.baseName + "_double",
                    type,
                    sampleTensor,
                    pattern.indexedDims,
                    pattern.mappedDims
                ));
            } else {
                // All other patterns support all cell types
                for (TensorType.Value cellType : cellTypes) {
                    String cellTypeSuffix = "_" + cellType.toString().toLowerCase(java.util.Locale.ROOT);
                    TensorType type = new TensorType(cellType, pattern.dimensions);
                    Tensor sampleTensor = createSampleTensor(type, pattern.dimensions);

                    combinations.add(new TensorTypeTestData(
                        pattern.baseName + cellTypeSuffix,
                        type,
                        sampleTensor,
                        pattern.indexedDims,
                        pattern.mappedDims
                    ));
                }
            }
        }

        return combinations;
    }

    /**
     * All combinations of 0, 1, and 2 indexed dimensions with 0, 1, and 2 mapped dimensions,
     * across all supported cell types (DOUBLE, FLOAT, BFLOAT16, INT8).
     * Scalar tensors only support DOUBLE.
     * Total: 33 combinations (1 scalar + 8 patterns × 4 cell types)
     */
    private static final List<TensorTypeTestData> ALL_TYPE_COMBINATIONS = generateAllTypeCombinations();

    /**
     * A simple mock DataSink that captures method calls for testing
     */
    private static class MockDataSink implements DataSink {
        private final StringBuilder output = new StringBuilder();
        private int indent = 0;

        private void indent() {
            if (indent < 0) throw new IllegalStateException("indent is: " + indent);
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

        @Override
        public void intValue(int v) {
            indent();
            output.append(v).append("I\n");
        }

        @Override
        public void shortValue(short v) {
            indent();
            output.append(v).append("S\n");
        }

        @Override
        public void byteValue(byte v) {
            indent();
            output.append(v).append("B\n");
        }

        @Override
        public void floatValue(float v) {
            indent();
            output.append(v).append("F\n");
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
        System.out.println("Testing " + ALL_TYPE_COMBINATIONS.size() + " combinations");

        // Verify we have the expected 33 combinations
        assertEquals("Should have 33 type combinations (1 scalar + 8 patterns × 4 cell types)",
                     33, ALL_TYPE_COMBINATIONS.size());

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

}
