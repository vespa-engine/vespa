// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.evaluation;

import com.google.common.annotations.Beta;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

/**
 * An evaluation context which is passed down to all nested functions during evaluation.
 *
 * @author bratseth
 */
@Beta
public interface EvaluationContext {

    /**
     * Returns tye type of the tensor with this name.
     *
     * @return returns the type of the tensor which will be returned by calling getTensor(name)
     *         or null if getTensor will return null.
     */
    TensorType getTensorType(String name);

    /** Returns the tensor bound to this name, or null if none */
    Tensor getTensor(String name);

}
