// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "compact/compact_tensor_v2.h"
#include "compact/compact_tensor_v2_builder.h"

namespace vespalib {
namespace tensor {

struct DefaultTensor {
    using type = CompactTensorV2;
    using builder = CompactTensorV2Builder;
};

} // namespace vespalib::tensor
} // namespace vespalib
