// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.yahoo.tensor.IndexedTensor;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.text.Text;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;

/**
 * Shared quantization validation, output type resolution, and base64 tensor decoding for cloud-based embedding providers.
 *
 * @author bjorncs
 */
class EmbeddingQuantization {

    enum Quantization { AUTO, FLOAT, INT8, BINARY }

    private EmbeddingQuantization() {}

    static void validateTensorType(TensorType targetType, int configuredDimensions, Quantization quantization) {
        if (targetType.dimensions().size() != 1)
            throw new IllegalArgumentException(
                    "Error in embedding to type '%s': should only have one dimension.".formatted(targetType));
        var dim = targetType.dimensions().get(0);
        if (!dim.isIndexed())
            throw new IllegalArgumentException(
                    "Error in embedding to type '%s': dimension should be indexed.".formatted(targetType));
        var valueType = targetType.valueType();
        long tensorDim = dim.size().orElseThrow();

        switch (quantization) {
            case AUTO -> {
                if (valueType == TensorType.Value.FLOAT || valueType == TensorType.Value.BFLOAT16) {
                    if (tensorDim != configuredDimensions)
                        throw new IllegalArgumentException(
                                Text.format("Tensor dimension %d does not match configured dimension %d.", tensorDim, configuredDimensions));
                } else if (valueType == TensorType.Value.INT8) {
                    long packedBinaryDimensions = configuredDimensions / 8;
                    if (tensorDim == packedBinaryDimensions && configuredDimensions % 8 != 0)
                        throw new IllegalArgumentException(
                                Text.format("Configured dimension %d must be divisible by 8 to allow packed-binary tensor dimension %d in quantization 'auto'.",
                                            configuredDimensions, packedBinaryDimensions));
                    if (tensorDim != configuredDimensions && tensorDim != packedBinaryDimensions)
                        throw new IllegalArgumentException(
                                Text.format("Tensor dimension %d does not match configured dimension. Expected %d or %d.", tensorDim, configuredDimensions, packedBinaryDimensions));
                } else {
                    throw new IllegalArgumentException(
                            "Quantization 'auto' is incompatible with tensor type " + targetType + ".");
                }
            }
            case FLOAT -> {
                if (valueType != TensorType.Value.FLOAT && valueType != TensorType.Value.BFLOAT16)
                    throw new IllegalArgumentException(
                            "Quantization 'float' is incompatible with tensor type " + targetType + ".");
                if (tensorDim != configuredDimensions)
                    throw new IllegalArgumentException(
                            Text.format("Tensor dimension %d does not match configured dimension %d.", tensorDim, configuredDimensions));
            }
            case INT8 -> {
                if (valueType != TensorType.Value.INT8)
                    throw new IllegalArgumentException(
                            "Quantization 'int8' is incompatible with tensor type " + targetType + ".");
                if (tensorDim != configuredDimensions)
                    throw new IllegalArgumentException(
                            Text.format("Tensor dimension %d does not match configured dimension %d.", tensorDim, configuredDimensions));
            }
            case BINARY -> {
                if (valueType != TensorType.Value.INT8)
                    throw new IllegalArgumentException(
                            "Quantization 'binary' is incompatible with tensor type " + targetType + ".");
                if (configuredDimensions % 8 != 0)
                    throw new IllegalArgumentException(
                            Text.format("Configured dimension %d must be divisible by 8 for quantization 'binary'.", configuredDimensions));
                if (tensorDim != configuredDimensions / 8)
                    throw new IllegalArgumentException(
                            Text.format("Tensor dimension %d does not match required dimension %d.", tensorDim, configuredDimensions / 8));
            }
        }
    }

    static String resolveOutputDataType(TensorType targetType, int configuredDimensions, Quantization quantization) {
        return switch (quantization) {
            case AUTO -> {
                if (targetType.valueType() == TensorType.Value.FLOAT || targetType.valueType() == TensorType.Value.BFLOAT16) {
                    yield "float";
                } else if (targetType.valueType() == TensorType.Value.INT8) {
                    long tensorDim = targetType.dimensions().get(0).size().orElseThrow();
                    yield (tensorDim == configuredDimensions) ? "int8" : "binary";
                } else {
                    throw new IllegalStateException();
                }
            }
            case FLOAT -> "float";
            case INT8 -> "int8";
            case BINARY -> "binary";
        };
    }

    static Tensor decodeBase64FloatTensor(String base64, String dimensionName, TensorType.Value valueType) {
        var buffer = ByteBuffer.wrap(Base64.getDecoder().decode(base64)).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer();
        var values = new float[buffer.remaining()];
        buffer.get(values);
        var type = new TensorType.Builder(valueType).indexed(dimensionName, values.length).build();
        return IndexedTensor.Builder.of(type, values).build();
    }

    static Tensor decodeBase64Int8Tensor(String base64, String dimensionName) {
        var bytes = Base64.getDecoder().decode(base64);
        var type = new TensorType.Builder(TensorType.Value.INT8).indexed(dimensionName, bytes.length).build();
        var builder = IndexedTensor.Builder.of(type);
        for (int i = 0; i < bytes.length; i++) {
            builder.cell(bytes[i], i);
        }
        return builder.build();
    }

    static Tensor decodeJsonArrayFloatTensor(JsonNode array, String dimensionName, TensorType.Value valueType) {
        var values = new float[array.size()];
        for (int i = 0; i < array.size(); i++) values[i] = array.get(i).floatValue();
        var type = new TensorType.Builder(valueType).indexed(dimensionName, values.length).build();
        return IndexedTensor.Builder.of(type, values).build();
    }

    static Tensor decodeJsonArrayInt8Tensor(JsonNode array, String dimensionName) {
        var type = new TensorType.Builder(TensorType.Value.INT8).indexed(dimensionName, array.size()).build();
        var builder = IndexedTensor.Builder.of(type);
        for (int i = 0; i < array.size(); i++) builder.cell((byte) array.get(i).intValue(), i);
        return builder.build();
    }

}
