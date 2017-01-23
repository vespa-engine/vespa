// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/eval/tensor_function.h>

namespace vespalib {
namespace tensor {

/**
 * Class that recognizes calculations over dense tensors (in tensor function intermediate representation)
 * and compiles this into an explicit tensor function.
 */
struct DenseTensorFunctionCompiler
{
    static eval::TensorFunction::UP compile(eval::tensor_function::Node_UP expr);
};

} // namespace tensor
} // namespace vespalib
