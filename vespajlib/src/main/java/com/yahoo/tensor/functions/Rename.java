package com.yahoo.tensor.functions;

import com.google.common.collect.ImmutableList;
import com.yahoo.tensor.Tensor;

import java.util.List;
import java.util.Objects;

/**
 * The <i>rename</i> tensor function returns a tensor where some dimensions are assigned new names.
 * 
 * @author bratseth
 */
public class Rename extends PrimitiveTensorFunction {

    private final TensorFunction argument;
    private final List<String> fromDimensions;
    private final List<String> toDimensions;

    public Rename(TensorFunction argument, List<String> fromDimensions, List<String> toDimensions) {
        Objects.requireNonNull(argument, "The argument tensor cannot be null");
        Objects.requireNonNull(fromDimensions, "The 'from' dimensions cannot be null");
        Objects.requireNonNull(toDimensions, "The 'to' dimensions cannot be null");
        this.argument = argument;
        this.fromDimensions = ImmutableList.copyOf(fromDimensions);
        this.toDimensions = ImmutableList.copyOf(toDimensions);
    }

    @Override
    public PrimitiveTensorFunction toPrimitive() { return this; }
    
    @Override
    public String toString() { 
        return "rename(" + argument + ", " + 
                       toVectorString(fromDimensions) + ", " + toVectorString(toDimensions) + ")";
    }
    
    private String toVectorString(List<String> elements) {
        if (elements.size() == 1)
            return elements.get(0);
        StringBuilder b = new StringBuilder("[");
        for (String element : elements)
            b.append(element).append(", ");
        b.setLength(b.length() - 2);
        return b.toString();
    }

}
