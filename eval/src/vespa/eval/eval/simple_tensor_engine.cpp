// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "simple_tensor_engine.h"
#include "simple_tensor.h"
#include <vespa/vespalib/util/stringfmt.h>
#include <cassert>

namespace vespalib {
namespace eval {

namespace {

const SimpleTensor &as_simple(const Tensor &tensor) {
    assert(&tensor.engine() == &SimpleTensorEngine::ref());
    return static_cast<const SimpleTensor&>(tensor);
}

const SimpleTensor &to_simple(const Value &value, Stash &stash) {
    if (value.is_double()) {
        return stash.create<SimpleTensor>(value.as_double());
    }
    if (auto tensor = value.as_tensor()) {
        return as_simple(*tensor);
    }
    return stash.create<SimpleTensor>(); // error
}

template <typename F>
void with_simple(const Value &value, const F &f) {
    if (value.is_double()) {
        f(SimpleTensor(value.as_double()));
    } else if (auto tensor = value.as_tensor()) {
        f(as_simple(*tensor));
    } else {
        f(SimpleTensor());
    }
}

const Value &to_value(std::unique_ptr<SimpleTensor> tensor, Stash &stash) {
    if (tensor->type().is_tensor()) {
        return *stash.create<Value::UP>(std::move(tensor));
    }
    return stash.create<DoubleValue>(tensor->as_double());
}

Value::UP to_value(std::unique_ptr<SimpleTensor> tensor) {
    if (tensor->type().is_tensor()) {
        return tensor;
    }
    return std::make_unique<DoubleValue>(tensor->as_double());
}

} // namespace vespalib::eval::<unnamed>

const SimpleTensorEngine SimpleTensorEngine::_engine;

//-----------------------------------------------------------------------------

TensorSpec
SimpleTensorEngine::to_spec(const Value &value) const
{
    TensorSpec spec(value.type().to_spec());
    const auto &dimensions = value.type().dimensions();
    with_simple(value, [&spec,&dimensions](const SimpleTensor &simple_tensor)
                {
                    for (const auto &cell: simple_tensor.cells()) {
                        TensorSpec::Address addr;
                        assert(cell.address.size() == dimensions.size());
                        for (size_t i = 0; i < cell.address.size(); ++i) {
                            const auto &label = cell.address[i];
                            if (label.is_mapped()) {
                                addr.emplace(dimensions[i].name, TensorSpec::Label(label.name));
                            } else {
                                addr.emplace(dimensions[i].name, TensorSpec::Label(label.index));
                            }
                        }
                        spec.add(addr, cell.value);
                    }
                });
    return spec;
}

Value::UP
SimpleTensorEngine::from_spec(const TensorSpec &spec) const
{
    return to_value(SimpleTensor::create(spec));
}

//-----------------------------------------------------------------------------

void
SimpleTensorEngine::encode(const Value &value, nbostream &output) const
{
    with_simple(value, [&output](const SimpleTensor &tensor) { SimpleTensor::encode(tensor, output); });
}

Value::UP
SimpleTensorEngine::decode(nbostream &input) const
{
    return to_value(SimpleTensor::decode(input));
}

//-----------------------------------------------------------------------------

const Value &
SimpleTensorEngine::map(const Value &a, map_fun_t function, Stash &stash) const
{
    if (a.is_double()) {
        return stash.create<DoubleValue>(function(a.as_double()));
    }
    return to_value(to_simple(a, stash).map(function), stash);
}

const Value &
SimpleTensorEngine::join(const Value &a, const Value &b, join_fun_t function, Stash &stash) const
{
    if (a.is_double() && b.is_double()) {
        return stash.create<DoubleValue>(function(a.as_double(), b.as_double()));
    }
    return to_value(SimpleTensor::join(to_simple(a, stash), to_simple(b, stash), function), stash);
}

const Value &
SimpleTensorEngine::merge(const Value &a, const Value &b, join_fun_t function, Stash &stash) const
{
    return to_value(SimpleTensor::merge(to_simple(a, stash), to_simple(b, stash), function), stash);
}

const Value &
SimpleTensorEngine::reduce(const Value &a, Aggr aggr, const std::vector<vespalib::string> &dimensions, Stash &stash) const
{
    return to_value(to_simple(a, stash).reduce(Aggregator::create(aggr, stash), dimensions), stash);
}

const Value &
SimpleTensorEngine::concat(const Value &a, const Value &b, const vespalib::string &dimension, Stash &stash) const
{
    return to_value(SimpleTensor::concat(to_simple(a, stash), to_simple(b, stash), dimension), stash);
}

const Value &
SimpleTensorEngine::rename(const Value &a, const std::vector<vespalib::string> &from, const std::vector<vespalib::string> &to, Stash &stash) const
{
    return to_value(to_simple(a, stash).rename(from, to), stash);
}

//-----------------------------------------------------------------------------

} // namespace vespalib::eval
} // namespace vespalib
