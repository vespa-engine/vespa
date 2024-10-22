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
    protected void doExecute(ExecutionContext context) {
        Optional<Tensor> tensor = ((TensorFieldValue)context.getValue()).getTensor();
        if (tensor.isEmpty()) return;
        context.setValue(new TensorFieldValue(tensor.get().map(v -> v > threshold ? 1 : 0)));
    }

    @Override
    protected void doVerify(VerificationContext context) {
        type = context.getValueType();
        if (! (type instanceof TensorDataType))
            throw new IllegalArgumentException("The 'binarize' function requires a tensor, but got " + type);
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
    public boolean equals(Object o) { return o instanceof BinarizeExpression; }

}
