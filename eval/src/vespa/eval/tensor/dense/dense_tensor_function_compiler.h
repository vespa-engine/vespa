// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/tensor_function.h>

namespace vespalib { class Stash; }

namespace vespalib::tensor {

/**
 * Class that recognizes calculations over dense tensors (in tensor function intermediate representation)
 * and compiles this into an explicit tensor function.
 */
struct DenseTensorFunctionCompiler
{
    static const eval::TensorFunction &compile(const eval::tensor_function::Node &expr, Stash &stash);
};

}

