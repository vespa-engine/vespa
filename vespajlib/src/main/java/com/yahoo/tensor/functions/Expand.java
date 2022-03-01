// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.functions;

import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.Name;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The <i>expand</i> tensor function returns a tensor with a new dimension of
 * size 1 is added, equivalent to "tensor * tensor(dim_name[1])(1)".
 *
 * @author lesters
 */
public class Expand<NAMETYPE extends Name> extends CompositeTensorFunction<NAMETYPE> {

    private final TensorFunction<NAMETYPE> argument;
    private final String dimensionName;

    public Expand(TensorFunction<NAMETYPE> argument, String dimension) {
        this.argument = argument;
        this.dimensionName = dimension;
    }

    @Override
    public List<TensorFunction<NAMETYPE>> arguments() { return Collections.singletonList(argument); }

    @Override
    public TensorFunction<NAMETYPE> withArguments(List<TensorFunction<NAMETYPE>> arguments) {
        if (arguments.size() != 1)
            throw new IllegalArgumentException("Expand must have 1 argument, got " + arguments.size());
        return new Expand<>(arguments.get(0), dimensionName);
    }

    @Override
    public PrimitiveTensorFunction<NAMETYPE> toPrimitive() {
        TensorType type = new TensorType.Builder(TensorType.Value.INT8).indexed(dimensionName, 1).build();
        Generate<NAMETYPE> expansion = new Generate<>(type, ScalarFunctions.constant(1.0));
        return new Join<>(expansion, argument, ScalarFunctions.multiply());
    }

    @Override
    public String toString(ToStringContext<NAMETYPE> context) {
        return "expand(" + argument.toString(context) + ", " + dimensionName + ")";
    }

    @Override
    public int hashCode() { return Objects.hash("expand", argument, dimensionName); }

}
