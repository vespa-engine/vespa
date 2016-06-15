// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "compact/compact_tensor.h"
#include "compact/compact_tensor_builder.h"

namespace vespalib {
namespace tensor {

struct DefaultTensor {
    using type = CompactTensor;
    using builder = CompactTensorBuilder;
};

} // namespace vespalib::tensor
} // namespace vespalib
