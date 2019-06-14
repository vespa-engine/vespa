// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tensor.h"
#include "default_tensor_engine.h"
#include <sstream>

namespace vespalib::tensor {

Tensor::Tensor()
    : eval::Tensor(DefaultTensorEngine::ref())
{
}

bool
Tensor::supported(TypeList types)
{
    bool sparse = false;
    bool dense = false;
    for (const eval::ValueType &type: types) {
        dense = (dense || type.is_double());
        for (const auto &dim: type.dimensions()) {
            dense = (dense || dim.is_indexed());
            sparse = (sparse || dim.is_mapped());
        }
    }
    return (dense != sparse);
}

std::ostream &
operator<<(std::ostream &out, const Tensor &value)
{
    out << value.toSpec().to_string();
    return out;
}

}
