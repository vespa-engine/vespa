// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "dense_tensor.h"

namespace vespalib {
namespace tensor {
namespace dense {

/**
 * Returns a tensor with the given dimension(s) removed and the cell values in that dimension(s)
 * combined using the given func.
 */
template<typename Function>
std::unique_ptr<Tensor>
reduce(const DenseTensorView &tensor, const std::vector<vespalib::string> &dimensions, Function &&func);

} // namespace dense
} // namespace tensor
} // namespace vespalib
