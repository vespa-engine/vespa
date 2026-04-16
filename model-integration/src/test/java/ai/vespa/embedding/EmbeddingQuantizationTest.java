// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.embedding;

import ai.vespa.embedding.EmbeddingQuantization.Quantization;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.tensor.IndexedTensor;
import com.yahoo.tensor.TensorType;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for shared quantization validation and tensor decoding.
 *
 * @author bjorncs
 */
public class EmbeddingQuantizationTest {

    // ===== validateTensorType =====

    @Test
    public void testAutoQuantizationWithFloatTensor() {
        EmbeddingQuantization.validateTensorType(
                TensorType.fromSpec("tensor<float>(x[1024])"), 1024, Quantization.AUTO);
    }

    @Test
    public void testAutoQuantizationWithBfloat16Tensor() {
        EmbeddingQuantization.validateTensorType(
                TensorType.fromSpec("tensor<bfloat16>(x[1024])"), 1024, Quantization.AUTO);
    }

    @Test
    public void testAutoQuantizationWithInt8FullDimension() {
        EmbeddingQuantization.validateTensorType(
                TensorType.fromSpec("tensor<int8>(x[1024])"), 1024, Quantization.AUTO);
    }

    @Test
    public void testAutoQuantizationWithInt8BinaryDimension() {
        EmbeddingQuantization.validateTensorType(
                TensorType.fromSpec("tensor<int8>(x[128])"), 1024, Quantization.AUTO);
    }

    @Test
    public void testAutoQuantizationRejectsDimensionMismatch() {
        var exception = assertThrows(IllegalArgumentException.class, () ->
                EmbeddingQuantization.validateTensorType(
                        TensorType.fromSpec("tensor<float>(x[512])"), 1024, Quantization.AUTO));
        assertEquals("Tensor dimension 512 does not match configured dimension 1024.", exception.getMessage());
    }

    @Test
    public void testFloatQuantizationRejectsInt8() {
        var exception = assertThrows(IllegalArgumentException.class, () ->
                EmbeddingQuantization.validateTensorType(
                        TensorType.fromSpec("tensor<int8>(x[1024])"), 1024, Quantization.FLOAT));
        assertEquals("Quantization 'float' is incompatible with tensor type tensor<int8>(x[1024]).", exception.getMessage());
    }

    @Test
    public void testInt8QuantizationRejectsFloat() {
        var exception = assertThrows(IllegalArgumentException.class, () ->
                EmbeddingQuantization.validateTensorType(
                        TensorType.fromSpec("tensor<float>(x[1024])"), 1024, Quantization.INT8));
        assertEquals("Quantization 'int8' is incompatible with tensor type tensor<float>(x[1024]).", exception.getMessage());
    }

    @Test
    public void testBinaryQuantizationRequiresOnEighthDimension() {
        var exception = assertThrows(IllegalArgumentException.class, () ->
                EmbeddingQuantization.validateTensorType(
                        TensorType.fromSpec("tensor<int8>(x[1024])"), 1024, Quantization.BINARY));
        assertEquals("Tensor dimension 1024 does not match required dimension 128.", exception.getMessage());
    }

    @Test
    public void testBinaryQuantizationRejectsNonMultipleOfEightConfiguredDimension() {
        assertThrows(IllegalArgumentException.class, () ->
                EmbeddingQuantization.validateTensorType(
                        TensorType.fromSpec("tensor<int8>(x[128])"), 1025, Quantization.BINARY));
    }

    @Test
    public void testBinaryQuantizationAcceptsCorrectDimension() {
        EmbeddingQuantization.validateTensorType(
                TensorType.fromSpec("tensor<int8>(x[128])"), 1024, Quantization.BINARY);
    }

    @Test
    public void testAutoQuantizationRejectsBinaryShapeWhenConfiguredDimensionIsNotMultipleOfEight() {
        assertThrows(IllegalArgumentException.class, () ->
                EmbeddingQuantization.validateTensorType(
                        TensorType.fromSpec("tensor<int8>(x[128])"), 1025, Quantization.AUTO));
    }

    @Test
    public void testRejectsMultipleDimensions() {
        assertThrows(IllegalArgumentException.class, () ->
                EmbeddingQuantization.validateTensorType(
                        TensorType.fromSpec("tensor<float>(d0[32],d1[32])"), 1024, Quantization.AUTO));
    }

    // ===== resolveOutputDataType =====

    @Test
    public void testResolveOutputDataTypeAuto() {
        assertEquals("float", EmbeddingQuantization.resolveOutputDataType(
                TensorType.fromSpec("tensor<float>(x[1024])"), 1024, Quantization.AUTO));
        assertEquals("int8", EmbeddingQuantization.resolveOutputDataType(
                TensorType.fromSpec("tensor<int8>(x[1024])"), 1024, Quantization.AUTO));
        assertEquals("binary", EmbeddingQuantization.resolveOutputDataType(
                TensorType.fromSpec("tensor<int8>(x[128])"), 1024, Quantization.AUTO));
    }

    @Test
    public void testResolveOutputDataTypeExplicit() {
        assertEquals("float", EmbeddingQuantization.resolveOutputDataType(
                TensorType.fromSpec("tensor<float>(x[1024])"), 1024, Quantization.FLOAT));
        assertEquals("int8", EmbeddingQuantization.resolveOutputDataType(
                TensorType.fromSpec("tensor<int8>(x[1024])"), 1024, Quantization.INT8));
        assertEquals("binary", EmbeddingQuantization.resolveOutputDataType(
                TensorType.fromSpec("tensor<int8>(x[128])"), 1024, Quantization.BINARY));
    }

    // ===== Base64 decoding =====

    @Test
    public void testBase64FloatRoundTrip() {
        float[] expected = {1.0f, -0.5f, 0.0f, Float.MAX_VALUE};
        var buffer = ByteBuffer.allocate(expected.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float v : expected) buffer.putFloat(v);
        var base64 = Base64.getEncoder().encodeToString(buffer.array());

        var result = (IndexedTensor) EmbeddingQuantization.decodeBase64FloatTensor(base64, "x", TensorType.Value.FLOAT, expected.length);
        assertEquals(expected.length, result.size());
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], result.getFloat(i), 0.0f, "Mismatch at index " + i);
        }

        assertThrows(IllegalArgumentException.class,
                () -> EmbeddingQuantization.decodeBase64FloatTensor(base64, "x", TensorType.Value.FLOAT, expected.length + 1));
    }

    @Test
    public void testBase64Int8RoundTrip() {
        byte[] expected = {0, 1, -1, 127, -128, 42};
        var base64 = Base64.getEncoder().encodeToString(expected);

        var result = (IndexedTensor) EmbeddingQuantization.decodeBase64Int8Tensor(base64, "x", expected.length);
        assertEquals(expected.length, result.size());
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], (byte) result.getFloat(i), "Mismatch at index " + i);
        }

        assertThrows(IllegalArgumentException.class,
                () -> EmbeddingQuantization.decodeBase64Int8Tensor(base64, "x", expected.length + 1));
    }

    // ===== JSON array decoding =====

    @Test
    public void testJsonArrayFloatTensorDecode() throws Exception {
        float[] expected = {1.0f, -0.5f, 0.0f, 3.14f};
        var array = new ObjectMapper().readTree("[1.0,-0.5,0.0,3.14]");

        var result = (IndexedTensor) EmbeddingQuantization.decodeJsonArrayFloatTensor(array, "x", TensorType.Value.FLOAT, expected.length);
        assertEquals(expected.length, result.size());
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], result.getFloat(i), 0.0f, "Mismatch at index " + i);
        }

        assertThrows(IllegalArgumentException.class,
                () -> EmbeddingQuantization.decodeJsonArrayFloatTensor(array, "x", TensorType.Value.FLOAT, expected.length + 1));
    }

    @Test
    public void testJsonArrayInt8TensorDecode() throws Exception {
        byte[] expected = {0, 1, -1, 127, -128, 42};
        var array = new ObjectMapper().readTree("[0,1,-1,127,-128,42]");

        var result = (IndexedTensor) EmbeddingQuantization.decodeJsonArrayInt8Tensor(array, "x", expected.length);
        assertEquals(expected.length, result.size());
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], (byte) result.getFloat(i), "Mismatch at index " + i);
        }

        assertThrows(IllegalArgumentException.class,
                () -> EmbeddingQuantization.decodeJsonArrayInt8Tensor(array, "x", expected.length + 1));
    }
}
