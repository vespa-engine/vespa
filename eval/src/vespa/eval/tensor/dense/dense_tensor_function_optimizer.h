// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/tensor_function.h>

namespace vespalib { class Stash; }

namespace vespalib::tensor {

/**
 * Class that recognizes calculations over dense tensors (in tensor function intermediate representation)
 * and optimizes this into an explicit tensor function.
 */
struct DenseTensorFunctionOptimizer
{
    static const eval::TensorFunction &optimize(const eval::TensorFunction &expr, Stash &stash);
};

}

