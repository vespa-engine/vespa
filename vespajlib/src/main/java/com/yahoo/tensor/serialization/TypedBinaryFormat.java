// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.serialization;

import com.yahoo.io.GrowableByteBuffer;
import com.yahoo.tensor.MixedTensor;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.util.Optional;

/**
 * Class used by clients for serializing a Tensor object into binary format or
 * de-serializing binary data into a Tensor object.
 *
 * The actual binary format used is not a concern for the client and
 * is hidden in this class and in the binary data.
 *
 * @author geirst
 */
public class TypedBinaryFormat {

    private static final int SPARSE_BINARY_FORMAT_TYPE = 1;
    private static final int DENSE_BINARY_FORMAT_TYPE = 2;
    private static final int MIXED_BINARY_FORMAT_TYPE = 3;
    private static final int SPARSE_BINARY_FORMAT_WITH_CELLTYPE = 5;
    private static final int DENSE_BINARY_FORMAT_WITH_CELLTYPE = 6;
    private static final int MIXED_BINARY_FORMAT_WITH_CELLTYPE = 7;

    private static final int DOUBLE_VALUE_TYPE = 0; // Not encoded as it is default, and you know the type when deserializing
    private static final int FLOAT_VALUE_TYPE = 1;
    private static final int BFLOAT16_VALUE_TYPE = 2;
    private static final int INT8_VALUE_TYPE = 3;

    public static byte[] encode(Tensor tensor) {
        GrowableByteBuffer buffer = new GrowableByteBuffer();
        return asByteArray(encode(tensor, buffer));
    }
    public static GrowableByteBuffer encode(Tensor tensor, GrowableByteBuffer buffer) {
        BinaryFormat encoder = getFormatEncoder(buffer, tensor);
        encoder.encode(buffer, tensor);
        return buffer;
    }

    /**
     * Decode some data to a tensor
     *
     * @param type the type to decode and validate to, or empty to use the type given in the data
     * @param buffer the buffer containing the data, use GrowableByteByffer.wrap(byte[]) if you have a byte array
     * @return the resulting tensor
     * @throws IllegalArgumentException if the tensor data was invalid
     */
    public static Tensor decode(Optional<TensorType> type, GrowableByteBuffer buffer) {
        BinaryFormat decoder = getFormatDecoder(buffer);
        return decoder.decode(type, buffer);
    }

    private static BinaryFormat getFormatEncoder(GrowableByteBuffer buffer, Tensor tensor) {
        boolean hasMappedDimensions = tensor.type().dimensions().stream().anyMatch(TensorType.Dimension::isMapped);
        boolean hasIndexedDimensions = tensor.type().dimensions().stream().anyMatch(TensorType.Dimension::isIndexed);
        boolean isMixed = hasMappedDimensions && hasIndexedDimensions;

        // TODO: Encoding as indexed if the implementation is mixed is not yet supported so use mixed format instead
        if (tensor instanceof MixedTensor && ! isMixed && hasIndexedDimensions)
            isMixed = true;

        if (isMixed && tensor.type().valueType() == TensorType.Value.DOUBLE) {
            encodeFormatType(buffer, MIXED_BINARY_FORMAT_TYPE);
            return new MixedBinaryFormat();
        }
        else if (isMixed) {
            encodeFormatType(buffer, MIXED_BINARY_FORMAT_WITH_CELLTYPE);
            encodeValueType(buffer, tensor.type().valueType());
            return new MixedBinaryFormat(tensor.type().valueType());
        }
        else if (hasIndexedDimensions && tensor.type().valueType() == TensorType.Value.DOUBLE) {
            encodeFormatType(buffer, DENSE_BINARY_FORMAT_TYPE);
            return new DenseBinaryFormat();
        }
        else if (hasIndexedDimensions) {
            encodeFormatType(buffer, DENSE_BINARY_FORMAT_WITH_CELLTYPE);
            encodeValueType(buffer, tensor.type().valueType());
            return new DenseBinaryFormat(tensor.type().valueType());
        }
        else if (tensor.type().valueType() == TensorType.Value.DOUBLE) {
            encodeFormatType(buffer, SPARSE_BINARY_FORMAT_TYPE);
            return new SparseBinaryFormat();
        }
        else {
            encodeFormatType(buffer, SPARSE_BINARY_FORMAT_WITH_CELLTYPE);
            encodeValueType(buffer, tensor.type().valueType());
            return new SparseBinaryFormat(tensor.type().valueType());
        }
    }

    private static BinaryFormat getFormatDecoder(GrowableByteBuffer buffer) {
        int formatType = decodeFormatType(buffer);
        switch (formatType) {
            case SPARSE_BINARY_FORMAT_TYPE: return new SparseBinaryFormat();
            case DENSE_BINARY_FORMAT_TYPE: return new DenseBinaryFormat();
            case MIXED_BINARY_FORMAT_TYPE: return new MixedBinaryFormat();
            case SPARSE_BINARY_FORMAT_WITH_CELLTYPE: return new SparseBinaryFormat(decodeValueType(buffer));
            case DENSE_BINARY_FORMAT_WITH_CELLTYPE: return new DenseBinaryFormat(decodeValueType(buffer));
            case MIXED_BINARY_FORMAT_WITH_CELLTYPE: return new MixedBinaryFormat(decodeValueType(buffer));
        }
        throw new IllegalArgumentException("Binary format type " + formatType + " is unknown");
    }

    private static void encodeFormatType(GrowableByteBuffer buffer, int formatType) {
        buffer.putInt1_4Bytes(formatType);
    }

    private static int decodeFormatType(GrowableByteBuffer buffer) {
        return buffer.getInt1_4Bytes();
    }

    private static void encodeValueType(GrowableByteBuffer buffer, TensorType.Value valueType) {
        switch (valueType) {
            case DOUBLE -> buffer.putInt1_4Bytes(DOUBLE_VALUE_TYPE);
            case FLOAT -> buffer.putInt1_4Bytes(FLOAT_VALUE_TYPE);
            case BFLOAT16 -> buffer.putInt1_4Bytes(BFLOAT16_VALUE_TYPE);
            case INT8 -> buffer.putInt1_4Bytes(INT8_VALUE_TYPE);
            default -> throw new IllegalArgumentException("Attempt to encode unknown tensor value type: " + valueType);
        }
    }

    private static TensorType.Value decodeValueType(GrowableByteBuffer buffer) {
        int valueType = buffer.getInt1_4Bytes();
        switch (valueType) {
            case DOUBLE_VALUE_TYPE: return TensorType.Value.DOUBLE;
            case FLOAT_VALUE_TYPE: return TensorType.Value.FLOAT;
            case BFLOAT16_VALUE_TYPE: return TensorType.Value.BFLOAT16;
            case INT8_VALUE_TYPE: return TensorType.Value.INT8;
        }
        throw new IllegalArgumentException("Received tensor value type '" + valueType + "'. " +
                "Only 0(double), 1(float), 2(bfloat16), or 3(int8) is legal.");
    }

    private static byte[] asByteArray(GrowableByteBuffer buffer) {
        buffer.flip();
        byte[] result = new byte[buffer.remaining()];
        buffer.get(result);
        return result;
    }

    static short bFloat16BitsFromFloat(float val) {
        return (short) (Float.floatToRawIntBits(val) >>> 16);
    }

    static float floatFromBFloat16Bits(short bits) {
        return Float.intBitsToFloat(bits << 16);
    }

}
