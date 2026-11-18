// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.ranking;

import com.yahoo.tensor.Tensor;

import java.time.Instant;

interface Evaluator {
    Evaluator bind(String name, Tensor value);

    /** Sets the time by which the ONNX models of this evaluator must be evaluated. */
    Evaluator setDeadline(Instant deadline);

    double evaluateScore();
}
