package com.yahoo.tensor.functions;

import com.google.common.annotations.Beta;

import java.util.Collections;
import java.util.List;

/**
 * @author bratseth
 */
@Beta
public class Argmax extends CompositeTensorFunction {

    private final TensorFunction argument;
    private final String dimension;
    
    public Argmax(TensorFunction argument, String dimension) {
        this.argument = argument;
        this.dimension = dimension;
    }

    @Override
    public List<TensorFunction> functionArguments() { return Collections.singletonList(argument); }

    @Override
    public TensorFunction replaceArguments(List<TensorFunction> arguments) {
        if ( arguments.size() != 1)
            throw new IllegalArgumentException("Argmax must have 1 argument, got " + arguments.size());
        return new Argmax(arguments.get(0), dimension);
    }

    @Override
    public PrimitiveTensorFunction toPrimitive() {
        TensorFunction primitiveArgument = argument.toPrimitive();
        return new Join(primitiveArgument,
                        new Reduce(primitiveArgument, Reduce.Aggregator.max, dimension),
                        ScalarFunctions.equal());
    }
    
    @Override
    public String toString(ToStringContext context) {
        return "argmax(" + argument.toString(context) + ", " + dimension + ")";
    }

}
