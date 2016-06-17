// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.datatypes;

import com.yahoo.document.DataType;
import com.yahoo.document.Field;
import com.yahoo.document.PrimitiveDataType;
import com.yahoo.document.serialization.FieldReader;
import com.yahoo.document.serialization.FieldWriter;
import com.yahoo.document.serialization.XmlStream;
import com.yahoo.tensor.Tensor;

import java.util.Objects;
import java.util.Optional;

/**
 * Field value class that wraps a tensor.
 *
 * @author geirst
 */
public class TensorFieldValue extends FieldValue {

    private Optional<Tensor> tensor;

    public TensorFieldValue() {
        tensor = Optional.empty();
    }

    public TensorFieldValue(Tensor tensor) {
        this.tensor = Optional.of(tensor);
    }

    public Optional<Tensor> getTensor() {
        return tensor;
    }

    @Override
    public DataType getDataType() {
        return DataType.TENSOR;
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
            tensor = Optional.empty();
        } else if (o instanceof Tensor) {
            tensor = Optional.of((Tensor)o);
        } else if (o instanceof TensorFieldValue) {
            tensor = ((TensorFieldValue)o).getTensor();
        } else {
            throw new IllegalArgumentException("Expected class '" + getClass().getName() + "', got '" +
                    o.getClass().getName() + "'.");
        }
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
        if (this == o) {
            return true;
        }
        if (!(o instanceof TensorFieldValue)) {
            return false;
        }
        TensorFieldValue rhs = (TensorFieldValue)o;
        if (!Objects.equals(tensor, rhs.tensor)) {
            return false;
        }
        return true;
    }

    public static PrimitiveDataType.Factory getFactory() {
        return new PrimitiveDataType.Factory() {

            @Override
            public FieldValue create() {
                return new TensorFieldValue();
            }
        };
    }
}

