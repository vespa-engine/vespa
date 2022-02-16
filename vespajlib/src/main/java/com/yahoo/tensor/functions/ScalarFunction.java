// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.functions;

import com.yahoo.tensor.evaluation.EvaluationContext;
import com.yahoo.tensor.evaluation.Name;

import java.util.Optional;
import java.util.function.Function;

/**
 * A function which returns a scalar
 *
 * @author bratseth
 */
public interface ScalarFunction<NAMETYPE extends Name> extends Function<EvaluationContext<NAMETYPE>, Double> {

    @Override
    Double apply(EvaluationContext<NAMETYPE> context);

    /** Returns this as a tensor function, or empty if it cannot be represented as a tensor function */
    default Optional<TensorFunction<NAMETYPE>> asTensorFunction() { return Optional.empty(); }

    default String toString(ToStringContext<NAMETYPE> context) { return toString(); }

}
