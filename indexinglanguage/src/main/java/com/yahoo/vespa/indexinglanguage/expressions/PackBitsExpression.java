// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.TensorDataType;
import com.yahoo.document.datatypes.TensorFieldValue;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.Tensors;

import java.util.Optional;

/**
 * Converts any tensor containing only ones and zeroes into one where each consecutive 8 values in the
 * dense dimension are packed into a single byte. As a consequence the output type of this is a tensor
 * where the dense dimension is 1/8th as large.
 *
 * @author bratseth
 */
public class PackBitsExpression extends Expression  {

    private TensorType outputTensorType;

    /** Creates a pack_bits expression. */
    public PackBitsExpression() {
        super(TensorDataType.any());
    }

    @Override
    public DataType setInputType(DataType inputType, VerificationContext context) {
        super.setInputType(inputType, context);
        if ( ! validType(inputType))
            throw new VerificationException(this, "Require a tensor with one dense dimension, but got " + inputType.getName());
        outputTensorType = outputType(((TensorDataType)inputType).getTensorType());
        return new TensorDataType(outputTensorType);
    }

    @Override
    public DataType setOutputType(DataType outputType, VerificationContext context) {
        super.setOutputType(outputType, context);
        if ( ! validType(outputType))
            throw new VerificationException(this, "Required to produce " + outputType.getName() +
                                                  " but this produces a tensor with one dense dimension");
        outputTensorType = ((TensorDataType)outputType).getTensorType();
        return new TensorDataType(inputType(outputTensorType));
    }

    /** Returns whether this is a valid input or output from this. */
    private boolean validType(DataType type) {
        if ( ! (type instanceof TensorDataType tensorType)) return false;
        if ( tensorType.getTensorType().indexedSubtype().dimensions().size() != 1) return false;
        return true;
    }

    @Override
    protected void doVerify(VerificationContext context) {}

    @Override
    protected void doExecute(ExecutionContext context) {
        Optional<Tensor> tensor = ((TensorFieldValue)context.getCurrentValue()).getTensor();
        if (tensor.isEmpty()) return;
        Tensor packed = Tensors.packBits(tensor.get());
        context.setCurrentValue(new TensorFieldValue(packed));
    }

    @Override
    public DataType createdOutputType() { return new TensorDataType(outputTensorType); }

    @Override
    public String toString() { return "pack_bits"; }

    @Override
    public int hashCode() { return toString().hashCode(); }

    @Override
    public boolean equals(Object o) { return o instanceof PackBitsExpression; }

    /** Returns the type this requires when producing the given output type. */
    private TensorType inputType(TensorType givenType) {
        var builder = new TensorType.Builder(TensorType.Value.DOUBLE); // Any value type is permissible
        for (var d : givenType.dimensions())
            builder.dimension(d.size().isPresent() ? d.withSize(d.size().get() * 8) : d);
        return builder.build();
    }

    /** Returns the type this produces from the given input type. */
    private TensorType outputType(TensorType givenType) {
        var builder = new TensorType.Builder(TensorType.Value.INT8);
        for (var d : givenType.dimensions())
            builder.dimension(d.size().isPresent() ? d.withSize((int) Math.ceil(d.size().get() / 8.0)) : d);
        return builder.build();
    }

}
