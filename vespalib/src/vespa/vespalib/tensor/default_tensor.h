// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "sparse/sparse_tensor.h"
#include "sparse/sparse_tensor_builder.h"

namespace vespalib {
namespace tensor {

struct DefaultTensor {
    using type = SparseTensor;
    using builder = SparseTensorBuilder;
};

} // namespace vespalib::tensor
} // namespace vespalib
