// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "simple_tensor_engine.h"
#include "simple_tensor.h"
#include "operation.h"

namespace vespalib {
namespace eval {

namespace {

std::vector<vespalib::string> dimension_names(const ValueType &type) {
    std::vector<vespalib::string> result;
    for (const auto &dimension: type.dimensions()) {
        result.push_back(dimension.name);
    }
    return result;
}

} // namespace vespalib::eval::<unnamed>

const SimpleTensorEngine SimpleTensorEngine::_engine;

ValueType
SimpleTensorEngine::type_of(const Tensor &tensor) const
{
    assert(&tensor.engine() == this);
    const SimpleTensor &simple_tensor = static_cast<const SimpleTensor&>(tensor);
    return simple_tensor.type();
}

std::unique_ptr<eval::Tensor>
SimpleTensorEngine::create(const TensorSpec &spec) const
{
    return SimpleTensor::create(spec);
}

bool
SimpleTensorEngine::equal(const Tensor &a, const Tensor &b) const
{
    assert(&a.engine() == this);
    assert(&b.engine() == this);
    const SimpleTensor &simple_a = static_cast<const SimpleTensor&>(a);
    const SimpleTensor &simple_b = static_cast<const SimpleTensor&>(b);
    return SimpleTensor::equal(simple_a, simple_b);
}

const Value &
SimpleTensorEngine::reduce(const eval::Tensor &tensor, const BinaryOperation &op, Stash &stash) const
{
    assert(&tensor.engine() == this);
    const SimpleTensor &simple_tensor = static_cast<const SimpleTensor&>(tensor);
    std::vector<vespalib::string> dimensions = dimension_names(simple_tensor.type());
    auto result = simple_tensor.reduce(op, dimensions);
    assert(result->type().is_double());
    assert(result->cells().size() == 1u);
    return stash.create<DoubleValue>(result->cells()[0].value);
}

const Value &
SimpleTensorEngine::reduce(const eval::Tensor &tensor, const BinaryOperation &op, const std::vector<vespalib::string> &dimensions, Stash &stash) const
{
    assert(&tensor.engine() == this);
    const SimpleTensor &simple_tensor = static_cast<const SimpleTensor&>(tensor);
    auto result = simple_tensor.reduce(op, dimensions);
    if (result->type().is_double()) {
        assert(result->cells().size() == 1u);
        return stash.create<DoubleValue>(result->cells()[0].value);
    }
    return stash.create<TensorValue>(std::move(result));
}

const Value &
SimpleTensorEngine::perform(const UnaryOperation &op, const eval::Tensor &a, Stash &stash) const
{
    assert(&a.engine() == this);
    const SimpleTensor &simple_a = static_cast<const SimpleTensor&>(a);    
    auto result = SimpleTensor::perform(op, simple_a);
    return stash.create<TensorValue>(std::move(result));
}

const Value &
SimpleTensorEngine::perform(const BinaryOperation &op, const eval::Tensor &a, const eval::Tensor &b, Stash &stash) const
{
    assert(&a.engine() == this);
    assert(&b.engine() == this);
    const SimpleTensor &simple_a = static_cast<const SimpleTensor&>(a);
    const SimpleTensor &simple_b = static_cast<const SimpleTensor&>(b);
    auto result = SimpleTensor::perform(op, simple_a, simple_b);
    return stash.create<TensorValue>(std::move(result));
}

} // namespace vespalib::eval
} // namespace vespalib
