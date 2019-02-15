// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.update;

import com.yahoo.document.DataType;
import com.yahoo.document.TensorDataType;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.TensorFieldValue;
import com.yahoo.document.serialization.DocumentUpdateWriter;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;

import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

/**
 *  An update used to remove cells from a sparse tensor (has only mapped dimensions).
 *
 *  The cells to remove are contained in a sparse tensor where cell values are set to 1.0
 */
public class TensorRemoveUpdate extends ValueUpdate<TensorFieldValue> {

    private TensorFieldValue tensor;

    public TensorRemoveUpdate(TensorFieldValue value) {
        super(ValueUpdateClassID.TENSORREMOVE);
        this.tensor = value;
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
        if ( ! (oldValue instanceof TensorFieldValue)) {
            throw new IllegalStateException("Cannot use tensor remove update on non-tensor datatype " + oldValue.getClass().getName());
        }
        if ( ! ((TensorFieldValue) oldValue).getTensor().isPresent()) {
            throw new IllegalArgumentException("No existing tensor to apply update to");
        }
        if ( ! tensor.getTensor().isPresent()) {
            return oldValue;
        }

        Tensor oldTensor = ((TensorFieldValue) oldValue).getTensor().get();
        Map<TensorAddress, Double> cellsToRemove = tensor.getTensor().get().cells();
        Tensor.Builder builder = Tensor.Builder.of(oldTensor.type());
        for (Iterator<Tensor.Cell> i = oldTensor.cellIterator(); i.hasNext(); ) {
            Tensor.Cell cell = i.next();
            TensorAddress address = cell.getKey();
            if ( ! cellsToRemove.containsKey(address)) {
                builder.cell(address, cell.getValue());
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
        TensorRemoveUpdate that = (TensorRemoveUpdate) o;
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
