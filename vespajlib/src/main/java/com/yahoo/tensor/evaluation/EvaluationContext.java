// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.evaluation;

import com.google.common.annotations.Beta;
import com.yahoo.tensor.Tensor;

/**
 * An evaluation context which is passed down to all nested functions during evaluation.
 *
 * @author bratseth
 */
@Beta
public interface EvaluationContext<NAMETYPE extends TypeContext.Name> extends TypeContext<NAMETYPE> {

    /** Returns the tensor bound to this name, or null if none */
    Tensor getTensor(String name);

}
