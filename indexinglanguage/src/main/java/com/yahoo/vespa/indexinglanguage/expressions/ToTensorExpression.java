// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.ArrayDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.NumericDataType;
import com.yahoo.document.TensorDataType;
import com.yahoo.document.datatypes.Array;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.NumericFieldValue;
import com.yahoo.document.datatypes.TensorFieldValue;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.util.Iterator;

/**
 * Converts an array of tensors into a single tensor where the array index becomes
 * an additional indexed dimension, whose name is given as an argument.
 * Scalars (numbers) are treated as rank-0 tensors, so an array of numbers is
 * converted into a tensor with the given dimension as its single dimension.
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
        if (inputType != null && ! (inputType instanceof AnyDataType)) {
            validateInputType(inputType);
            validateInputAndOutput(inputType, getOutputType(context));
        }
        // The size of the added dimension (the length of the input array) is unknown here,
        // so the output type can only be resolved from the required output
        return getOutputType(context);
    }

    @Override
    public DataType setOutputType(DataType outputType, TypeContext context) {
        super.setOutputType(outputType, context);
        if (outputType == null || outputType instanceof AnyDataType) return getInputType(context);
        TensorType outputTensorType = validateOutputType(outputType);
        validateInputAndOutput(getInputType(context), outputType);
        TensorType elementType = withoutDimension(outputTensorType);
        if (elementType.rank() == 0) // The input is an array of scalars or rank-0 tensors: Not uniquely determined
            return getInputType(context);
        return new ArrayDataType(new TensorDataType(elementType));
    }

    private void validateInputType(DataType inputType) {
        if ( ! (inputType instanceof ArrayDataType arrayType))
            throw new VerificationException(this, "Expected an array of tensors or numbers as input, but got " + inputType.getName());
        DataType elementType = arrayType.getNestedType();
        if (elementType instanceof TensorDataType tensorType) {
            TensorType type = tensorType.getTensorType();
            if (type != null && type.dimension(dimension).isPresent())
                throw new VerificationException(this, "The input tensors already contain the dimension '" + dimension + "'");
        }
        else if ( ! (elementType instanceof NumericDataType)) {
            throw new VerificationException(this, "Expected an array of tensors or numbers as input, but got " + inputType.getName());
        }
    }

    /** Verifies that the given input can produce the given output, where either may be unresolved (null). */
    private void validateInputAndOutput(DataType inputType, DataType outputType) {
        if (inputType == null || inputType instanceof AnyDataType) return;
        if ( ! (outputType instanceof TensorDataType tensorDataType) || tensorDataType.getTensorType() == null) return;
        if ( ! (inputType instanceof ArrayDataType arrayType)) return; // Invalid input: Reported by validateInputType
        if (tensorDataType.getTensorType().dimension(dimension).isEmpty()) return; // Invalid output: Reported by validateOutputType

        TensorType impliedElementType = withoutDimension(tensorDataType.getTensorType());
        if (arrayType.getNestedType() instanceof NumericDataType && impliedElementType.rank() > 0)
            throw new VerificationException(this, "The input is an array of numbers, so this can only produce a tensor " +
                                                  "with the single dimension '" + dimension + "', but " +
                                                  tensorDataType.getTensorType() + " is required");
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
        if (array.isEmpty()) {
            context.setCurrentValue(null);
            return;
        }

        DataType elementType = array.getDataType().getNestedType();
        if (elementType instanceof TensorDataType tensorDataType)
            context.setCurrentValue(new TensorFieldValue(fromTensors(array, tensorDataType.getTensorType())));
        else if (elementType instanceof NumericDataType numericType)
            context.setCurrentValue(new TensorFieldValue(fromScalars(array, numericType)));
        else
            throw new IllegalArgumentException("Expected an array of tensors or numbers, got " + array.getDataType().getName());
    }

    private Tensor fromTensors(Array<?> array, TensorType elementType) {
        Tensor.Builder builder = Tensor.Builder.of(withDimension(elementType, array.size()));
        for (int i = 0; i < array.size(); i++) {
            Tensor tensor = ((TensorFieldValue)array.get(i)).getTensor().orElse(null);
            if (tensor == null) continue;
            TensorType valueType = tensor.type();
            for (Iterator<Tensor.Cell> cells = tensor.cellIterator(); cells.hasNext(); ) {
                Tensor.Cell cell = cells.next();
                Tensor.Builder.CellBuilder cellBuilder = builder.cell().label(dimension, i);
                for (int d = 0; d < valueType.dimensions().size(); d++) {
                    TensorType.Dimension elementDimension = valueType.dimensions().get(d);
                    if (elementDimension.isIndexed())
                        cellBuilder.label(elementDimension.name(), cell.getKey().numericLabel(d));
                    else
                        cellBuilder.label(elementDimension.name(), cell.getKey().label(d));
                }
                cellBuilder.value(cell.getValue());
            }
        }
        return builder.build();
    }

    private Tensor fromScalars(Array<?> array, NumericDataType elementType) {
        TensorType type = new TensorType.Builder(valueTypeOf(elementType)).indexed(dimension, array.size()).build();
        Tensor.Builder builder = Tensor.Builder.of(type);
        for (int i = 0; i < array.size(); i++)
            builder.cell().label(dimension, i).value(((NumericFieldValue)array.get(i)).getNumber().doubleValue());
        return builder.build();
    }

    /** Returns the tensor value type to use for the given scalar type. */
    private TensorType.Value valueTypeOf(NumericDataType elementType) {
        if (getOutputType() instanceof TensorDataType outputType && outputType.getTensorType() != null)
            return outputType.getTensorType().valueType();
        if (elementType.equals(DataType.FLOAT)) return TensorType.Value.FLOAT;
        if (elementType.equals(DataType.BYTE)) return TensorType.Value.INT8;
        return TensorType.Value.DOUBLE;
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
