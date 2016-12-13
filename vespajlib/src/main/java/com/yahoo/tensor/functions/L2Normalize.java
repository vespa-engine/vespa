package com.yahoo.tensor.functions;

import com.google.common.annotations.Beta;

import java.util.Collections;
import java.util.List;

/**
 * @author bratseth
 */
@Beta
public class L2Normalize extends CompositeTensorFunction {

    private final TensorFunction argument;
    private final String dimension;
    
    public L2Normalize(TensorFunction argument, String dimension) {
        this.argument = argument;
        this.dimension = dimension;
    }

    @Override
    public List<TensorFunction> functionArguments() { return Collections.singletonList(argument); }

    @Override
    public TensorFunction replaceArguments(List<TensorFunction> arguments) {
        if ( arguments.size() != 1)
            throw new IllegalArgumentException("L2Normalize must have 1 argument, got " + arguments.size());
        return new L2Normalize(arguments.get(0), dimension);
    }

    @Override
    public PrimitiveTensorFunction toPrimitive() {
        TensorFunction primitiveArgument = argument.toPrimitive();
        return new Join(primitiveArgument,
                        new Map(new Reduce(new Map(primitiveArgument, ScalarFunctions.square()),
                                           Reduce.Aggregator.sum,
                                           dimension),
                                ScalarFunctions.sqrt()),
                        ScalarFunctions.divide());
    }
    
    @Override
    public String toString(ToStringContext context) {
        return "l2_normalize(" + argument.toString(context) + ", " + dimension + ")";
    }

}
