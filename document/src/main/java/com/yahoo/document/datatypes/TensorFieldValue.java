// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.datatypes;

import com.yahoo.document.Field;
import com.yahoo.document.TensorDataType;
import com.yahoo.document.serialization.FieldReader;
import com.yahoo.document.serialization.FieldWriter;
import com.yahoo.document.serialization.XmlStream;
import com.yahoo.io.GrowableByteBuffer;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.serialization.TypedBinaryFormat;

import java.util.Optional;

/**
 * Field value class that wraps a tensor.
 *
 * @author geirst
 */
public class TensorFieldValue extends FieldValue {

    private Optional<Tensor> tensor;

    private Optional<byte[]> serializedTensor;

    private Optional<TensorDataType> dataType;

    /**
     * Create an empty tensor field value where the tensor type is not yet known.
     *
     * The tensor (and tensor type) can later be assigned with assignTensor().
     */
    public TensorFieldValue() {
        this.dataType = Optional.empty();
        this.serializedTensor = Optional.empty();
        this.tensor = Optional.empty();
    }

    /** Create an empty tensor field value for the given tensor type */
    public TensorFieldValue(TensorType type) {
        this.dataType = Optional.of(new TensorDataType(type));
        this.serializedTensor = Optional.empty();
        this.tensor = Optional.empty();
    }

    /** Create a tensor field value containing the given tensor */
    public TensorFieldValue(Tensor tensor) {
        this.dataType = Optional.of(new TensorDataType(tensor.type()));
        this.serializedTensor = Optional.empty();
        this.tensor = Optional.of(tensor);
    }

    private void lazyDeserialize() {
        if (tensor.isEmpty() && serializedTensor.isPresent()) {
            Tensor t = TypedBinaryFormat.decode(Optional.empty(), GrowableByteBuffer.wrap(serializedTensor.get()));
            if (dataType.isEmpty()) {
                this.dataType = Optional.of(new TensorDataType(t.type()));
                this.tensor = Optional.of(t);
            } else {
                if (t.type().isAssignableTo(dataType.get().getTensorType())) {
                    this.tensor = Optional.of(t);
                } else {
                    throw new IllegalArgumentException("Type mismatch: Cannot assign tensor of type " + t.type() +
                                                       " to field of type " + dataType.get());
                }
            }
        }
    }

    public Optional<Tensor> getTensor() {
        lazyDeserialize();
        return tensor;
    }

    public Optional<TensorType> getTensorType() {
        lazyDeserialize();
        return dataType.isPresent() ? Optional.of(dataType.get().getTensorType()) : Optional.empty();
    }

    @Override
    public TensorDataType getDataType() {
        return dataType.get();
    }

    @Override
    public String toString() {
        var t = getTensor();
        if (t.isPresent()) {
            return t.get().toString();
        } else {
            return "null";
        }
    }

    @Override
    public void printXml(XmlStream xml) {
        // TODO (geirst)
    }

    @Override
    public void clear() {
        tensor = Optional.empty();
        serializedTensor = Optional.empty();
    }

    @Override
    public void assign(Object o) {
        if (o == null) {
            assignTensor(Optional.empty());
        } else if (o instanceof Tensor) {
            assignTensor(Optional.of((Tensor)o));
        } else if (o instanceof TensorFieldValue) {
            assignTensor(((TensorFieldValue)o).getTensor());
        } else {
            throw new IllegalArgumentException("Expected class '" + getClass().getName() + "', got '" +
                                               o.getClass().getName() + "'.");
        }
    }

    public void assignSerializedTensor(byte[] data) {
        serializedTensor = Optional.of(data);
        tensor = Optional.empty();
    }

    public Optional<byte[]> getSerializedTensor() {
        if (serializedTensor.isPresent()) {
            return serializedTensor;
        } else if (tensor.isPresent()) {
            serializedTensor = Optional.of(TypedBinaryFormat.encode(tensor.get()));
            assert(serializedTensor.isPresent());
        }
        return serializedTensor;
    }

    /**
     * Assigns the given tensor to this field value.
     *
     * The tensor type is also set from the given tensor if it was not set before.
     */
    public void assignTensor(Optional<Tensor> tensor) {
        this.serializedTensor = Optional.empty();
        if (tensor.isPresent()) {
            if (getTensorType().isPresent() &&
                    !tensor.get().type().isAssignableTo(getTensorType().get())) {
                throw new IllegalArgumentException("Type mismatch: Cannot assign tensor of type " + tensor.get().type() +
                        " to field of type " + getTensorType().get());
            }
            if (getTensorType().isEmpty()) {
                this.dataType = Optional.of(new TensorDataType(tensor.get().type()));
            }
        }
        this.tensor = tensor;
    }

    @Override
    public void serialize(Field field, FieldWriter writer) {
        writer.write(field, this);
    }

    @Override
    public void deserialize(Field field, FieldReader reader) {
        reader.read(field, this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if ( ! (o instanceof TensorFieldValue)) return false;

        TensorFieldValue other = (TensorFieldValue)o;
        if ( ! getTensorType().equals(other.getTensorType())) return false;
        if ( ! getTensor().equals(other.getTensor())) return false;
        return true;
    }

    @Override
    public Object getWrappedValue() {
        return getTensor().orElse(null);
    }

}
