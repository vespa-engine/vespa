// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.TensorDataType;
import com.yahoo.document.datatypes.TensorFieldValue;
import com.yahoo.tensor.Tensor;

import java.util.Objects;
import java.util.Optional;

/**
 * Converts a tensor of any input type into a binarized tensor: Each value is replaced by either 0 or 1.
 *
 * @author bratseth
 */
public class BinarizeExpression extends Expression  {

    private final double threshold;

    /** The type this consumes and produces. */
    private DataType type;

    /**
     * Creates a binarize expression.
     *
     * @param threshold the value which the tensor cell value must be larger than to be set to 1 and not 0.
     */
    public BinarizeExpression(double threshold) {
        super(TensorDataType.any());
        this.threshold = threshold;
    }

    @Override
    public DataType setInputType(DataType inputType, VerificationContext context) {
        return super.setInputType(inputType, TensorDataType.any(), context);
    }

    @Override
    public DataType setOutputType(DataType outputType, VerificationContext context) {
        return super.setOutputType(outputType, TensorDataType.any(), context);
    }

    @Override
    protected void doVerify(VerificationContext context) {
        type = context.getCurrentType();
        if (! (type instanceof TensorDataType))
            throw new VerificationException(this, "Require a tensor, but got " + type);
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        Optional<Tensor> tensor = ((TensorFieldValue)context.getCurrentValue()).getTensor();
        if (tensor.isEmpty()) return;
        context.setCurrentValue(new TensorFieldValue(tensor.get().map(v -> v > threshold ? 1 : 0)));
    }

    @Override
    public DataType createdOutputType() { return type; }

    @Override
    public String toString() {
        return "binarize" + (threshold == 0 ? "" : " " + threshold);
    }

    @Override
    public int hashCode() { return Objects.hash(threshold, toString().hashCode()); }

    @Override
    public boolean equals(Object o) {
        if ( ! (o instanceof BinarizeExpression other)) return false;
        return this.threshold == other.threshold;
    }

}
