package com.yahoo.tensor.functions;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableList;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.EvaluationContext;

import java.util.List;
import java.util.Optional;

/**
 * Concatenation of two tensors along an (indexed) dimension
 * 
 * @author bratseth
 */
@Beta
public class Concat extends PrimitiveTensorFunction {

    private final TensorFunction argumentA, argumentB;
    private final String dimension;

    public Concat(TensorFunction argumentA, TensorFunction argumentB, String dimension) {
        this.argumentA = argumentA;
        this.argumentB = argumentB;
        this.dimension = dimension;
    }

    @Override
    public List<TensorFunction> functionArguments() { return ImmutableList.of(argumentA, argumentB); }

    @Override
    public TensorFunction replaceArguments(List<TensorFunction> arguments) {
        if (arguments.size() != 2)
            throw new IllegalArgumentException("Concat must have 2 arguments, got " + arguments.size());
        return new Concat(arguments.get(0), arguments.get(1), dimension);
    }

    @Override
    public PrimitiveTensorFunction toPrimitive() {
        return new Concat(argumentA.toPrimitive(), argumentB.toPrimitive(), dimension);
    }

    @Override
    public String toString(ToStringContext context) {
        return "concat(" + argumentA.toString(context) + ", " + argumentB.toString(context) + ", " + dimension + ")";
    }

    @Override
    public Tensor evaluate(EvaluationContext context) {
        Tensor a = argumentA.evaluate(context);
        Tensor b = argumentB.evaluate(context);
        Optional<TensorType.Dimension> aDimension = a.type().dimension(dimension);
        Optional<TensorType.Dimension> bDimension = a.type().dimension(dimension);
        throw new UnsupportedOperationException("Not implemented"); // TODO
    }

}
