// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.ArrayDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.TensorDataType;
import com.yahoo.document.datatypes.Array;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.TensorFieldValue;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.util.Iterator;

/**
 * Converts an array of tensors into a single tensor where the array index becomes
 * an additional indexed dimension, whose name is given as an argument.
 *
 * @author bratseth
 */
public final class ToTensorExpression extends Expression {

    private final String dimension;

    public ToTensorExpression(String dimension) {
        this.dimension = dimension;
    }

    public String getDimension() { return dimension; }

    @Override
    public DataType setInputType(DataType inputType, TypeContext context) {
        super.setInputType(inputType, context);
        if (inputType != null && ! (inputType instanceof AnyDataType))
            validateInputType(inputType);
        // The size of the added dimension (the length of the input array) is unknown here,
        // so the output type can only be resolved from the required output
        return getOutputType(context);
    }

    @Override
    public DataType setOutputType(DataType outputType, TypeContext context) {
        super.setOutputType(outputType, context);
        if (outputType == null || outputType instanceof AnyDataType) return getInputType(context);
        TensorType outputTensorType = validateOutputType(outputType);
        return new ArrayDataType(new TensorDataType(withoutDimension(outputTensorType)));
    }

    private void validateInputType(DataType inputType) {
        if ( ! (inputType instanceof ArrayDataType arrayType) ||
             ! (arrayType.getNestedType() instanceof TensorDataType tensorType))
            throw new VerificationException(this, "Expected an array of tensors as input, but got " + inputType.getName());
        TensorType elementType = tensorType.getTensorType();
        if (elementType != null && elementType.dimension(dimension).isPresent())
            throw new VerificationException(this, "The input tensors already contain the dimension '" + dimension + "'");
    }

    private TensorType validateOutputType(DataType outputType) {
        if ( ! (outputType instanceof TensorDataType tensorDataType) || tensorDataType.getTensorType() == null)
            throw new VerificationException(this, "This produces a tensor, but " + outputType.getName() + " is required");
        TensorType tensorType = tensorDataType.getTensorType();
        var addedDimension = tensorType.dimension(dimension);
        if (addedDimension.isEmpty() || ! addedDimension.get().isIndexed())
            throw new VerificationException(this, "This produces a tensor containing the indexed dimension '" +
                                                  dimension + "', but " + tensorType + " is required");
        return tensorType;
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        FieldValue input = context.getCurrentValue();
        if ( ! (input instanceof Array<?> array))
            throw new IllegalArgumentException("Expected Array input, got " + input.getDataType().getName());
        if ( ! (array.getDataType().getNestedType() instanceof TensorDataType tensorDataType))
            throw new IllegalArgumentException("Expected an array of tensors, got " + array.getDataType().getName());
        if (array.isEmpty()) {
            context.setCurrentValue(null);
            return;
        }

        TensorType outputType = withDimension(tensorDataType.getTensorType(), array.size());
        Tensor.Builder builder = Tensor.Builder.of(outputType);
        for (int i = 0; i < array.size(); i++) {
            Tensor tensor = ((TensorFieldValue)array.get(i)).getTensor().orElse(null);
            if (tensor == null) continue;
            TensorType elementType = tensor.type();
            for (Iterator<Tensor.Cell> cells = tensor.cellIterator(); cells.hasNext(); ) {
                Tensor.Cell cell = cells.next();
                Tensor.Builder.CellBuilder cellBuilder = builder.cell().label(dimension, i);
                for (int d = 0; d < elementType.dimensions().size(); d++) {
                    TensorType.Dimension elementDimension = elementType.dimensions().get(d);
                    if (elementDimension.isIndexed())
                        cellBuilder.label(elementDimension.name(), cell.getKey().numericLabel(d));
                    else
                        cellBuilder.label(elementDimension.name(), cell.getKey().label(d));
                }
                cellBuilder.value(cell.getValue());
            }
        }
        context.setCurrentValue(new TensorFieldValue(builder.build()));
    }

    /** Returns the given type with the dimension this adds, bound to the given size. */
    private TensorType withDimension(TensorType type, long size) {
        var builder = new TensorType.Builder(type.valueType());
        for (var typeDimension : type.dimensions())
            builder.dimension(typeDimension);
        builder.dimension(TensorType.Dimension.indexed(dimension, size));
        return builder.build();
    }

    /** Returns the given type without the dimension this adds. */
    private TensorType withoutDimension(TensorType type) {
        var builder = new TensorType.Builder(type.valueType());
        for (var typeDimension : type.dimensions())
            if ( ! typeDimension.name().equals(dimension))
                builder.dimension(typeDimension);
        return builder.build();
    }

    @Override
    public String toString() { return "to_tensor " + dimension; }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ToTensorExpression rhs)) return false;
        return dimension.equals(rhs.dimension);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode() + dimension.hashCode();
    }

}
