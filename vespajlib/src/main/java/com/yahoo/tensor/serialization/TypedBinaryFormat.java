// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.serialization;

import com.google.common.annotations.Beta;
import com.yahoo.io.GrowableByteBuffer;
import com.yahoo.tensor.Tensor;

/**
 * Class used by clients for serializing a Tensor object into binary format or
 * de-serializing binary data into a Tensor object.
 *
 * The actual binary format used is not a concern for the client and
 * is hidden in this class and in the binary data.
 *
 * @author geirst
 */
@Beta
public class TypedBinaryFormat {

    private static final int SPARSE_BINARY_FORMAT_TYPE = 1;

    public static byte[] encode(Tensor tensor) {
        GrowableByteBuffer buffer = new GrowableByteBuffer();
        buffer.putInt1_4Bytes(SPARSE_BINARY_FORMAT_TYPE);
        new SparseBinaryFormat().encode(buffer, tensor);
        buffer.flip();
        byte[] result = new byte[buffer.remaining()];
        buffer.get(result);
        return result;
    }

    public static Tensor decode(byte[] data) {
        GrowableByteBuffer buffer = GrowableByteBuffer.wrap(data);
        int formatType = buffer.getInt1_4Bytes();
        switch (formatType) {
            case SPARSE_BINARY_FORMAT_TYPE:
                return new SparseBinaryFormat().decode(buffer);
            default:
                throw new IllegalArgumentException("Binary format type " + formatType + " is not a known format");
        }
    }

}
