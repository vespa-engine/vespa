// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.serialization;

import com.yahoo.io.GrowableByteBuffer;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.util.Optional;

/**
 * Representation of a specific binary format with functions for serializing a Tensor object into
 * this format or de-serializing binary data into a Tensor object.
 *
 * @author geirst
 */
interface BinaryFormat {

    /**
     * Serialize the given tensor into binary format.
     */
    void encode(GrowableByteBuffer buffer, Tensor tensor);

    /**
     * Deserialize the given binary data into a Tensor object.
     *
     * @param type the expected abstract type of the tensor to serialize, or empty to use type information from the data
     * @param buffer the buffer containing the tensor binary data
     */
    Tensor decode(Optional<TensorType> type, GrowableByteBuffer buffer);

}
