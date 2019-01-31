// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.update;

import com.yahoo.document.DataType;
import com.yahoo.document.TensorDataType;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.TensorFieldValue;
import com.yahoo.document.serialization.DocumentUpdateWriter;

import java.util.Objects;

/*
 *  An update for the subset of the cells in a tensor.
 *  The tensor is a mapped (aka sparse) tensor.
 */
public class TensorModifyUpdate extends ValueUpdate<TensorFieldValue> {
    protected Operation operation;
    protected TensorFieldValue tensor;

    public TensorModifyUpdate(Operation operation, TensorFieldValue tensor) {
        super(ValueUpdateClassID.TENSORMODIFY);
        this.operation = operation;
        this.tensor = tensor;
    }


    public Operation getOperation() { return operation; }

    public TensorFieldValue getValue() { return tensor; }
    public void setValue(TensorFieldValue value) { tensor = value; }


    @Override
    public FieldValue applyTo(FieldValue oldValue) {
        if (oldValue instanceof TensorFieldValue) {
        } else {
            throw new IllegalStateException("Cannot use tensor modify update on non-tensor datatype "+oldValue.getClass().getName());
        }
        return oldValue;
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
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        TensorModifyUpdate that = (TensorModifyUpdate) o;
        return operation == that.operation &&
                tensor.equals(that.tensor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), operation, tensor);
    }

    @Override
    public String toString() {
        return super.toString() + " " + operation.name + " " + tensor;
    }

    /**
     * Lists valid operations that can be performed by a TensorModifyUpdate.
     */
    public enum Operation {
        /**
         * Replace tensor cell values with values from matching update tensor cells.
         */
        REPLACE(0, "replace"),
        /**
         * Add values from matching update tensor cells to target tensor cells.
         */
        ADD(1, "add"),
        /**
         * Multiply values from matching update tensor cells with target tensor cells.
         */
        MULTIPLY(2, "multiply");

        /**
         * The numeric ID of the operator, used for serialization.
         */
        public final int id;
        /**
         * The name of the operator, mainly used in toString() methods.
         */
        public final String name;

        Operation(int id, String name) {
            this.id = id;
            this.name = name;
        }

        public static Operation getOperation(int id) {
            for (Operation operation : Operation.values()) {
                if (operation.id == id) {
                    return operation;
                }
            }
            return null;
        }
    }

}
