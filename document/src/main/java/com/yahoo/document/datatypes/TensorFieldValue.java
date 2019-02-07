// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.datatypes;

import com.yahoo.document.Field;
import com.yahoo.document.TensorDataType;
import com.yahoo.document.serialization.FieldReader;
import com.yahoo.document.serialization.FieldWriter;
import com.yahoo.document.serialization.XmlStream;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.util.Optional;

/**
 * Field value class that wraps a tensor.
 *
 * @author geirst
 */
public class TensorFieldValue extends FieldValue {

    private Optional<Tensor> tensor;

    private final TensorDataType dataType;

    /** Create an empty tensor field value */
    public TensorFieldValue(TensorType type) {
        this.dataType = new TensorDataType(type);
        this.tensor = Optional.empty();
    }

    /** Create a tensor field value containing the given tensor */
    public TensorFieldValue(Tensor tensor) {
        this.dataType = new TensorDataType(tensor.type());
        this.tensor = Optional.of(tensor);
    }

    public Optional<Tensor> getTensor() {
        return tensor;
    }

    @Override
    public TensorDataType getDataType() {
        return dataType;
    }

    @Override
    public String toString() {
        if (tensor.isPresent()) {
            return tensor.get().toString();
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

    public void assignTensor(Optional<Tensor> tensor) {
        if (tensor.isPresent() && ! tensor.get().type().isAssignableTo(dataType.getTensorType()))
            throw new IllegalArgumentException("Type mismatch: Cannot assign tensor of type " + tensor.get().type() +
                                               " to field of type " + dataType.getTensorType());
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
        if ( ! dataType.getTensorType().equals(other.dataType.getTensorType())) return false;
        if ( ! tensor.equals(other.tensor)) return false;
        return true;
    }

    @Override
    public Object getWrappedValue() {
        return tensor.orElse(null);
    }

}

