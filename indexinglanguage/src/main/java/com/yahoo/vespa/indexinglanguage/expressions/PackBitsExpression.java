// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;
import com.yahoo.document.Field;
import com.yahoo.document.TensorDataType;
import com.yahoo.document.datatypes.TensorFieldValue;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.util.Optional;

/**
 * Converts any tensor containing only ones and zeroes into one where each consecutive 8 values in the
 * same dense dimension are packed into a single byte. As a consequence the output type of this is a tensor
 * where each dense dimension is 1/8th as large.
 *
 * @author bratseth
 */
public class PackBitsExpression extends Expression  {

    private TensorType inputType;
    private TensorType outputType;

    /** Creates a pack_bits expression. */
    public PackBitsExpression() {
        super(TensorDataType.any());
    }

    @Override
    public void setStatementOutput(DocumentType documentType, Field field) {
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        Optional<Tensor> tensor = ((TensorFieldValue)context.getValue()).getTensor();
        if (tensor.isEmpty()) return;
        Tensor.Builder builder = Tensor.Builder.of(outputType);
        context.setValue(new TensorFieldValue(builder.build()));
    }

    @Override
    protected void doVerify(VerificationContext context) {
        if (! (context.getValueType() instanceof TensorDataType tensorInputType))
            throw new IllegalArgumentException("The 'pack_bits' function requires a tensor, but got " + context.getValueType());
        inputType = tensorInputType.getTensorType();

        var builder = new TensorType.Builder(TensorType.Value.INT8);
        for (var d : inputType.dimensions())
            builder.dimension(d.size().isPresent() ? d.withSize((int)Math.ceil(d.size().get() / 8.0)) : d);
        outputType = builder.build();
    }

    @Override
    public DataType createdOutputType() { return new TensorDataType(outputType); }

    @Override
    public String toString() { return "pack_bits"; }

    @Override
    public int hashCode() { return toString().hashCode(); }

    @Override
    public boolean equals(Object o) { return o instanceof PackBitsExpression; }

}
