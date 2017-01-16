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
public class TensorField extends DocsumField implements VariableLengthField {

    public TensorField(String name) {
        super(name);
    }

    @Override
    public Tensor decode(ByteBuffer buffer) {
        int length = ((int) buffer.getShort()) & 0xffff;
        ByteBuffer contentBuffer = ByteBuffer.wrap(buffer.array(), buffer.arrayOffset() + buffer.position(), length);
        Tensor tensor = TypedBinaryFormat.decode(Optional.empty(), new GrowableByteBuffer(contentBuffer));
        buffer.position(buffer.position() + length);
        return tensor;
    }

    @Override
    public Tensor decode(ByteBuffer b, FastHit hit) {
        Tensor tensor = decode(b);
        hit.setField(name, tensor);
        return tensor;
    }

    @Override
    public String toString() {
        return "field " + getName() + " type tensor";
    }

    @Override
    public int getLength(ByteBuffer b) {
        int offset = b.position();
        int len = ((int) b.getShort()) & 0xffff;
        b.position(offset + len + (Short.SIZE >> 3));
        return len + (Short.SIZE >> 3);
    }

    @Override
    public int sizeOfLength() {
        return Short.SIZE >> 3;
    }

    @Override
    public Object convert(Inspector value) {
        byte[] content = value.asData(Value.empty().asData());
        if (content.length == 0) return null;
        return TypedBinaryFormat.decode(Optional.empty(), GrowableByteBuffer.wrap(content));
    }

}
