// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "value.h"
#include "tensor_engine.h"

namespace vespalib {
namespace eval {

ErrorValue ErrorValue::instance;

bool
TensorValue::equal(const Value &rhs) const
{
    return (rhs.is_tensor() && _tensor->engine().equal(*_tensor, *rhs.as_tensor()));
}

ValueType
TensorValue::type() const
{
    return _tensor->engine().type_of(*_tensor);
}

} // namespace vespalib::eval
} // namespace vespalib
