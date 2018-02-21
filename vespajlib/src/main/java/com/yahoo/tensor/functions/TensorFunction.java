// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.functions;

import com.google.common.annotations.Beta;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.EvaluationContext;
import com.yahoo.tensor.evaluation.MapEvaluationContext;
import com.yahoo.tensor.evaluation.TypeContext;

import java.util.List;

/**
 * A representation of a tensor function which is able to be translated to a set of primitive
 * tensor functions if necessary.
 * All tensor functions are immutable.
 *
 * @author bratseth
 */
@Beta
public abstract class TensorFunction {

    /** Returns the function arguments of this node in the order they are applied */
    public abstract List<TensorFunction> arguments();

    /**
     * Returns a copy of this tensor function with the arguments replaced by the given list of arguments.
     *
     * @throws IllegalArgumentException if the argument list has the wrong size for this function
     */
    public abstract TensorFunction withArguments(List<TensorFunction> arguments);

    /**
     * Translate this function - and all of its arguments recursively -
     * to a tree of primitive functions only.
     *
     * @return a tree of primitive functions implementing this
     */
    public abstract PrimitiveTensorFunction toPrimitive();

    /**
     * Evaluates this tensor.
     *
     * @param context a context which must be passed to all nexted functions when evaluating
     */
    public abstract <NAMETYPE extends TypeContext.Name> Tensor evaluate(EvaluationContext<NAMETYPE>  context);

    /**
     * Returns the type of the tensor this produces given the input types in the context
     *
     * @param context a context which must be passed to all nexted functions when evaluating
     */
    public abstract <NAMETYPE extends TypeContext.Name> TensorType type(TypeContext<NAMETYPE> context);

    /** Evaluate with no context */
    public final Tensor evaluate() { return evaluate(new MapEvaluationContext()); }

    /**
     * Return a string representation of this context.
     *
     * @param context a context which must be passed to all nested functions when requesting the string value
     */
    public abstract String toString(ToStringContext context);

    @Override
    public String toString() { return toString(ToStringContext.empty()); }

}
