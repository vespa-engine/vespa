// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "value.h"
#include "operation_visitor.h"
#include "tensor_engine.h"

namespace vespalib {
namespace eval {

const Value &
Value::apply(const UnaryOperation &, Stash &stash) const
{
    return stash.create<ErrorValue>();
}

const Value &
Value::apply(const BinaryOperation &, const Value &, Stash &stash) const
{
    return stash.create<ErrorValue>();
}

bool
TensorValue::equal(const Value &rhs) const
{
    return (rhs.is_tensor() && _tensor->engine().equal(*_tensor, *rhs.as_tensor()));
}

const Value &
TensorValue::apply(const UnaryOperation &op, Stash &stash) const
{
    return _tensor->engine().map(op, *_tensor, stash);
}

const Value &
TensorValue::apply(const BinaryOperation &op, const Value &rhs, Stash &stash) const
{
    const Tensor *other = rhs.as_tensor();
    if ((other == nullptr) || (&other->engine() != &_tensor->engine())) {
        return stash.create<ErrorValue>();
    }
    return _tensor->engine().apply(op, *_tensor, *other, stash);
}

ValueType
TensorValue::type() const
{
    return _tensor->engine().type_of(*_tensor);
}

} // namespace vespalib::eval
} // namespace vespalib
