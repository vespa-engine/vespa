// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/tensor_function.h>

namespace vespalib::eval {

/**
 * Tensor lambda optimizer for creating a new dense tensor based on
 * peeking cells of a single existing tensor. This can represent a
 * wide area of operations (reshape, gather, slice).
 **/
struct DenseLambdaPeekOptimizer {
    static const TensorFunction &optimize(const TensorFunction &expr, Stash &stash);
};

}
