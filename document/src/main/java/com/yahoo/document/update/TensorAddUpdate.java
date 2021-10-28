// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.update;

import com.yahoo.document.DataType;
import com.yahoo.document.TensorDataType;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.TensorFieldValue;
import com.yahoo.document.serialization.DocumentUpdateWriter;
import com.yahoo.tensor.Tensor;

import java.util.Objects;

/**
 *  An update used to add cells to a sparse or mixed tensor (has at least one mapped dimension).
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
        data.write(this);
    }

    @Override
    public FieldValue applyTo(FieldValue oldValue) {
        if ( ! (oldValue instanceof TensorFieldValue)) {
            throw new IllegalStateException("Cannot use tensor add update on non-tensor datatype " + oldValue.getClass().getName());
        }
        if ( ! ((TensorFieldValue) oldValue).getTensor().isPresent()) {
            throw new IllegalArgumentException("No existing tensor to apply update to");
        }
        if ( ! tensor.getTensor().isPresent()) {
            return oldValue;
        }

        Tensor old = ((TensorFieldValue) oldValue).getTensor().get();
        Tensor update = tensor.getTensor().get();
        Tensor result = old.merge(update, (left, right) -> right);  // note this might be slow for large mixed tensor updates
        return new TensorFieldValue(result);
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

    @Override
    public String toString() {
        return super.toString() + " " + tensor;
    }
}
