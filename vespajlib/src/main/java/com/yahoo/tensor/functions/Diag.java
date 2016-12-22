package com.yahoo.tensor.functions;

import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.functions.CompositeTensorFunction;
import com.yahoo.tensor.functions.Generate;
import com.yahoo.tensor.functions.PrimitiveTensorFunction;
import com.yahoo.tensor.functions.ScalarFunctions;
import com.yahoo.tensor.functions.TensorFunction;
import com.yahoo.tensor.functions.ToStringContext;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A tensor generator which returns a tensor of any dimension filled with 1 in the diagonal and 0 elsewhere.
 * 
 * @author bratseth
 */
public class Diag extends CompositeTensorFunction {

    private final TensorType type;
    private final Function<List<Integer>, Double> diagFunction;
    
    public Diag(TensorType type) {
        this.type = type;
        this.diagFunction = ScalarFunctions.equalArguments(dimensionNames().collect(Collectors.toList()));
    }
    
    @Override
    public List<TensorFunction> functionArguments() { return Collections.emptyList(); }

    @Override
    public TensorFunction replaceArguments(List<TensorFunction> arguments) {
        if ( arguments.size() != 1)
            throw new IllegalArgumentException("Diag must have 0 arguments, got " + arguments.size());
        return this;
    }

    @Override
    public PrimitiveTensorFunction toPrimitive() {
        return new Generate(type, diagFunction);
    }

    @Override
    public String toString(ToStringContext context) {
        return "diag(" + dimensionNames().collect(Collectors.joining(",")) + ")" + diagFunction;
    }
    
    private Stream<String> dimensionNames() {
        return type.dimensions().stream().map(TensorType.Dimension::toString);
    }

}
