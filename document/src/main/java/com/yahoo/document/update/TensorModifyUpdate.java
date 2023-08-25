// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.update;

import com.yahoo.document.DataType;
import com.yahoo.document.TensorDataType;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.TensorFieldValue;
import com.yahoo.document.serialization.DocumentUpdateWriter;
import com.yahoo.tensor.MixedTensor;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;
import com.yahoo.tensor.TensorType;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.function.DoubleBinaryOperator;

/*
 *  An update for the subset of the cells in a tensor.
 *
 *  The cells to update are contained as entries of the sparse dimension(s) of the tensor.
 */
public class TensorModifyUpdate extends ValueUpdate<TensorFieldValue> {

    protected Operation operation;
    protected TensorFieldValue tensor;
    protected boolean createNonExistingCells;

    public TensorModifyUpdate(Operation operation, TensorFieldValue tensor) {
        this(operation, tensor, false);
    }

    public TensorModifyUpdate(Operation operation, TensorFieldValue tensor, boolean createNonExistingCells) {
        super(ValueUpdateClassID.TENSORMODIFY);
        this.operation = operation;
        this.tensor = tensor;
        this.createNonExistingCells = createNonExistingCells;
        verifyCompatibleType(tensor.getDataType().getTensorType());
    }

    private void verifyCompatibleType(TensorType type) {
        if (type.dimensions().stream().anyMatch(dim -> dim.isIndexed()) ) {
            throw new IllegalArgumentException("Tensor type '" + type + "' is not compatible as it has no mapped dimensions");
        }
    }

    /**
     * Converts the given tensor type to a type that is compatible for being used in this update (has only mapped dimensions).
     */
    public static TensorType convertDimensionsToMapped(TensorType type) {
        TensorType.Builder builder = new TensorType.Builder(type.valueType());
        type.dimensions().stream().forEach(dim -> builder.mapped(dim.name()));
        return builder.build();
    }

    public Operation getOperation() { return operation; }
    public TensorFieldValue getValue() { return tensor; }
    public boolean getCreateNonExistingCells() { return createNonExistingCells; }
    public double getDefaultCellValue() {
        return (operation == Operation.MULTIPLY) ? 1.0 : 0.0;
    }
    public void setValue(TensorFieldValue value) { tensor = value; }

    @Override
    public FieldValue applyTo(FieldValue oldValue) {
        if (oldValue instanceof TensorFieldValue) {
            Tensor oldTensor = ((TensorFieldValue)oldValue).getTensor().orElseThrow(
                    () -> new IllegalArgumentException("No existing tensor to apply update on"));
            if (tensor.getTensor().isPresent()) {
                DoubleBinaryOperator modifier;
                switch (operation) {
                    case REPLACE:  modifier = (left, right) -> right; break;
                    case ADD:      modifier = (left, right) -> left + right; break;
                    case MULTIPLY: modifier = (left, right) -> left * right; break;
                    default:
                        throw new UnsupportedOperationException("Unknown operation: " + operation);
                }
                if (createNonExistingCells && hasMappedSubtype(oldTensor.type())) {
                    var subspaces = findSubspacesNotInInput(oldTensor, tensor.getTensor().get());
                    if (!subspaces.isEmpty()) {
                        oldTensor = insertSubspaces(oldTensor, subspaces, getDefaultCellValue());
                    }
                }
                Tensor modified = oldTensor.modify(modifier, tensor.getTensor().get().cells());
                return new TensorFieldValue(modified);
            }
        } else {
            throw new IllegalStateException("Cannot use tensor modify update on non-tensor datatype "+oldValue.getClass().getName());
        }
        return oldValue;
    }

    private static boolean hasMappedSubtype(TensorType type) {
        return !type.mappedSubtype().equals(TensorType.empty);
    }

    private static boolean hasIndexedSubtype(TensorType type) {
        return !type.indexedSubtype().equals(TensorType.empty);
    }

    private static HashSet<TensorAddress> findSubspacesNotInInput(Tensor input, Tensor modifier) {
        var subspaces = new HashSet<TensorAddress>();
        var inputCells = input.cells();
        var type = input.type();
        for (var itr = modifier.cellIterator(); itr.hasNext(); ) {
            Tensor.Cell cell = itr.next();
            TensorAddress address = cell.getKey();
            if (!inputCells.containsKey(address)) {
                subspaces.add(createSparsePartAddress(address, type));
            }
        }
        return subspaces;
    }

    private static TensorAddress createSparsePartAddress(TensorAddress address, TensorType type) {
        var builder = new TensorAddress.Builder(type.mappedSubtype());
        for (int i = 0; i < type.dimensions().size(); ++i) {
            var dim = type.dimensions().get(i);
            if (dim.isMapped()) {
                builder.add(dim.name(), address.label(i));
            }
        }
        return builder.build();
    }

    private static Tensor insertSubspaces(Tensor input, HashSet<TensorAddress> subspaces, double defaultCellValue) {
        var type = input.type();
        boolean mixed = hasMappedSubtype(type) && hasIndexedSubtype(type);
        Tensor.Builder builder;
        if (mixed) {
            var boundBuilder = MixedTensor.BoundBuilder.of(type);
            var values = new double[(int) boundBuilder.denseSubspaceSize()];
            Arrays.fill(values, defaultCellValue);
            for (var subspace : subspaces) {
                boundBuilder.block(subspace, values);
            }
            builder = boundBuilder;
        } else {
            builder = Tensor.Builder.of(type);
            for (var subspace : subspaces) {
                builder.cell(subspace, defaultCellValue);
            }
        }
        for (var itr = input.cellIterator(); itr.hasNext(); ) {
            builder.cell(itr.next());
        }
        return builder.build();
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
                tensor.equals(that.tensor) &&
                createNonExistingCells == that.createNonExistingCells;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), operation, tensor, createNonExistingCells);
    }

    @Override
    public String toString() {
        return super.toString() + " " + operation.name + " " + tensor + " " + createNonExistingCells;
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
