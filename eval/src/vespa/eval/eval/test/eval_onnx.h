// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/onnx/onnx_wrapper.h>
#include <vector>

namespace vespalib::eval::test {

std::vector<TensorSpec> eval_onnx(const Onnx &model, const std::vector<TensorSpec> &params);

} // namespace
