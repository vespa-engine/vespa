// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "tensor_type.h"

namespace vespalib {
namespace tensor {
namespace tensor_type {

TensorType fromSpec(const vespalib::string &str);
vespalib::string toSpec(const TensorType &type);

} // namespace vespalib::tensor::tensor_type
} // namespace vespalib::tensor
} // namespace vespalib
