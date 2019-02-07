// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.update;

import com.yahoo.document.DataType;
import com.yahoo.document.TensorDataType;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.TensorFieldValue;
import com.yahoo.document.serialization.DocumentUpdateWriter;

import java.util.Objects;

/**
 *  An update used to add cells to a sparse tensor (has only mapped dimensions).
 *
 *  The cells to add are contained in a sparse tensor as well.
 */
public class TensorAddUpdate extends ValueUpdate<TensorFieldValue> {

    private TensorFieldValue tensor;

    public TensorAddUpdate(TensorFieldValue tensor) {
        super(ValueUpdateClassID.TENSORADD);
        this.tensor = tensor;
    }

    @Override
    protected void checkCompatibility(DataType fieldType) {
        if (!(fieldType instanceof TensorDataType)) {
            throw new UnsupportedOperationException("Expected tensor type, got " + fieldType.getName() + ".");
        }
    }

    @Override
    public void serialize(DocumentUpdateWriter data, DataType superType) {
        // TODO: implement
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public FieldValue applyTo(FieldValue oldValue) {
        // TODO: implement
        return null;
    }

    @Override
    public TensorFieldValue getValue() {
        return tensor;
    }

    @Override
    public void setValue(TensorFieldValue value) {
        tensor = value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        TensorAddUpdate that = (TensorAddUpdate) o;
        return tensor.equals(that.tensor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), tensor);
    }
}
