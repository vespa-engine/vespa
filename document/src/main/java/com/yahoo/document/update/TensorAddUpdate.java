// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.update;

import com.yahoo.document.DataType;
import com.yahoo.document.TensorDataType;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.TensorFieldValue;
import com.yahoo.document.serialization.DocumentUpdateWriter;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;

import java.util.Map;
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

        Tensor oldTensor = ((TensorFieldValue) oldValue).getTensor().get();
        Map<TensorAddress, Double> oldCells = oldTensor.cells();
        Map<TensorAddress, Double> addCells = tensor.getTensor().get().cells();

        // currently, underlying implementation disallows multiple entries with the same key

        Tensor.Builder builder = Tensor.Builder.of(oldTensor.type());
        for (Map.Entry<TensorAddress, Double> oldCell : oldCells.entrySet()) {
            builder.cell(oldCell.getKey(), addCells.getOrDefault(oldCell.getKey(), oldCell.getValue()));
        }
        for (Map.Entry<TensorAddress, Double> addCell : addCells.entrySet()) {
            if ( ! oldCells.containsKey(addCell.getKey())) {
                builder.cell(addCell.getKey(), addCell.getValue());
            }
        }
        return new TensorFieldValue(builder.build());
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
