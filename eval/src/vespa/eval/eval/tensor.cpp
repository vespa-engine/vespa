// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tensor.h"
#include "tensor_engine.h"

namespace vespalib {
namespace eval {

bool
operator==(const Tensor &lhs, const Tensor &rhs)
{
    return ((&lhs.engine() == &rhs.engine()) && lhs.engine().equal(lhs, rhs));
}

std::ostream &
operator<<(std::ostream &out, const Tensor &tensor)
{
    out << tensor.engine().to_string(tensor);
    return out;
}

} // namespace vespalib::eval
} // namespace vespalib
