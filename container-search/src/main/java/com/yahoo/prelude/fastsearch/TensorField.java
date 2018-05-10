// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch;

import com.yahoo.data.access.Inspector;
import com.yahoo.data.access.simple.Value;
import com.yahoo.io.GrowableByteBuffer;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.serialization.TypedBinaryFormat;

import java.nio.ByteBuffer;
import java.util.Optional;

/**
 * A tensor field. Tensors are encoded as a data field where the data (following the length)
 * is encoded in a tensor binary format defined by com.yahoo.tensor.serialization.TypedBinaryFormat
 *
 * @author bratseth
 */
public class TensorField extends DocsumField {

    public TensorField(String name) {
        super(name);
    }

    @Override
    public String toString() {
        return "field " + getName() + " type tensor";
    }

    @Override
    public Object convert(Inspector value) {
        byte[] content = value.asData(Value.empty().asData());
        if (content.length == 0) return null;
        return TypedBinaryFormat.decode(Optional.empty(), GrowableByteBuffer.wrap(content));
    }

}
