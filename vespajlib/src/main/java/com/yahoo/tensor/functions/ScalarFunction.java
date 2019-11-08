// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.functions;

import com.yahoo.tensor.evaluation.EvaluationContext;

import java.util.function.Function;

/**
 * A function which returns a scalar
 *
 * @author bratseth
 */
public interface ScalarFunction extends Function<EvaluationContext<?>, Double> {

    @Override
    Double apply(EvaluationContext<?> context);

    default String toString(ToStringContext context) {
        return toString();
    }

}
