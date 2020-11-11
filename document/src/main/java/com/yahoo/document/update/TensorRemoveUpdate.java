// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.update;

import com.yahoo.document.DataType;
import com.yahoo.document.TensorDataType;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.TensorFieldValue;
import com.yahoo.document.serialization.DocumentUpdateWriter;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.util.Objects;

/**
 * An update used to remove cells from a sparse tensor or dense sub-spaces from a mixed tensor.
 *
 * The specification of which cells to remove contains addresses using a subset or all of the sparse dimensions of the tensor type.
 * This is represented as a sparse tensor where cell values are set to 1.0.
 */
public class TensorRemoveUpdate extends ValueUpdate<TensorFieldValue> {

    private TensorFieldValue tensor;

    public TensorRemoveUpdate(TensorFieldValue value) {
        super(ValueUpdateClassID.TENSORREMOVE);
        this.tensor = value;
        if (!tensor.getTensor().isPresent()) {
            throw new IllegalArgumentException("Tensor must be present in remove update");
        }
        verifyCompatibleType(tensor.getTensorType().get());
    }

    public void verifyCompatibleType(TensorType originalType) {
        TensorType sparseType = extractSparseDimensions(originalType);
        TensorType thisType = tensor.getTensorType().get();
        for (var dim : thisType.dimensions()) {
            if (sparseType.dimension(dim.name()).isEmpty()) {
                throw new IllegalArgumentException("Unexpected type '" + thisType + "' in remove update. "
                        + "Expected dimensions to be a subset of '" + sparseType + "'");
            }
        }
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
            throw new IllegalStateException("Cannot use tensor remove update on non-tensor datatype " + oldValue.getClass().getName());
        }
        if ( ! ((TensorFieldValue) oldValue).getTensor().isPresent()) {
            throw new IllegalArgumentException("No existing tensor to apply update to");
        }
        if ( ! tensor.getTensor().isPresent()) {
            return oldValue;
        }

        Tensor old = ((TensorFieldValue) oldValue).getTensor().get();
        Tensor update = tensor.getTensor().get();
        // TODO: handle the case where this tensor only contains a subset of the sparse dimensions of the input tensor.
        Tensor result = old.remove(update.cells().keySet());
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

    public static TensorType extractSparseDimensions(TensorType type) {
        TensorType.Builder builder = new TensorType.Builder(type.valueType());
        type.dimensions().stream().filter(dim -> ! dim.isIndexed()).forEach(dim -> builder.mapped(dim.name()));
        return builder.build();
    }

}
