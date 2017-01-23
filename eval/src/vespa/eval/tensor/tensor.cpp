// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "tensor.h"
#include <sstream>
#include "default_tensor_engine.h"

namespace vespalib {
namespace tensor {

Tensor::Tensor()
    : eval::Tensor(DefaultTensorEngine::ref())
{
}

std::ostream &
operator<<(std::ostream &out, const Tensor &value)
{
    value.print(out);
    return out;
}

} // namespace vespalib::tensor
} // namespace vespalib
