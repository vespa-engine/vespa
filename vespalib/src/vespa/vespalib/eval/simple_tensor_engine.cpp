// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/vespalib/util/stringfmt.h>
#include "simple_tensor_engine.h"
#include "simple_tensor.h"
#include "operation.h"

namespace vespalib {
namespace eval {

const SimpleTensorEngine SimpleTensorEngine::_engine;

ValueType
SimpleTensorEngine::type_of(const Tensor &tensor) const
{
    assert(&tensor.engine() == this);
    const SimpleTensor &simple_tensor = static_cast<const SimpleTensor&>(tensor);
    return simple_tensor.type();
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

vespalib::string
SimpleTensorEngine::to_string(const Tensor &tensor) const
{
    assert(&tensor.engine() == this);
    const SimpleTensor &simple_tensor = static_cast<const SimpleTensor&>(tensor);
    vespalib::string out = vespalib::make_string("simple(%s) {\n", simple_tensor.type().to_spec().c_str());
    for (const auto &cell: simple_tensor.cells()) {
        size_t n = 0;
        out.append("  [");
        for (const auto &label: cell.address) {
            if (n++) {
                out.append(",");
            }
            if (label.is_mapped()) {
                out.append(label.name);
            } else {
                out.append(vespalib::make_string("%zu", label.index));
            }
        }
        out.append(vespalib::make_string("]: %g\n", cell.value));
    }
    out.append("}");
    return out;
}

TensorSpec
SimpleTensorEngine::to_spec(const Tensor &tensor) const
{
    assert(&tensor.engine() == this);
    const SimpleTensor &simple_tensor = static_cast<const SimpleTensor&>(tensor);
    ValueType type = simple_tensor.type(); 
    const auto &dimensions = type.dimensions();
    TensorSpec spec(type.to_spec());
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
    return spec;
}

const SimpleTensor &to_simple(const Value &value, Stash &stash) {
    auto tensor = value.as_tensor();
    if (tensor) {
        assert(&tensor->engine() == &SimpleTensorEngine::ref());
        return static_cast<const SimpleTensor &>(*tensor);
    }
    return stash.create<SimpleTensor>(value.as_double());
}

std::unique_ptr<eval::Tensor>
SimpleTensorEngine::create(const TensorSpec &spec) const
{
    return SimpleTensor::create(spec);
}

const Value &
SimpleTensorEngine::reduce(const eval::Tensor &tensor, const BinaryOperation &op, const std::vector<vespalib::string> &dimensions, Stash &stash) const
{
    assert(&tensor.engine() == this);
    const SimpleTensor &simple_tensor = static_cast<const SimpleTensor&>(tensor);
    auto result = simple_tensor.reduce(op, dimensions.empty() ? simple_tensor.type().dimension_names() : dimensions);
    if (result->type().is_double()) {
        assert(result->cells().size() == 1u);
        return stash.create<DoubleValue>(result->cells()[0].value);
    }
    return stash.create<TensorValue>(std::move(result));
}

const Value &
SimpleTensorEngine::map(const UnaryOperation &op, const eval::Tensor &a, Stash &stash) const
{
    assert(&a.engine() == this);
    const SimpleTensor &simple_a = static_cast<const SimpleTensor&>(a);    
    auto result = SimpleTensor::map(op, simple_a);
    return stash.create<TensorValue>(std::move(result));
}

const Value &
SimpleTensorEngine::apply(const BinaryOperation &op, const eval::Tensor &a, const eval::Tensor &b, Stash &stash) const
{
    assert(&a.engine() == this);
    assert(&b.engine() == this);
    const SimpleTensor &simple_a = static_cast<const SimpleTensor&>(a);
    const SimpleTensor &simple_b = static_cast<const SimpleTensor&>(b);
    auto result = SimpleTensor::join(op, simple_a, simple_b);
    return stash.create<TensorValue>(std::move(result));
}

const Value &
SimpleTensorEngine::concat(const Value &a, const Value &b, const vespalib::string &dimension, Stash &stash) const
{
    const SimpleTensor &simple_a = to_simple(a, stash);
    const SimpleTensor &simple_b = to_simple(b, stash);
    auto result = SimpleTensor::concat(simple_a, simple_b, dimension);
    return stash.create<TensorValue>(std::move(result));
}

} // namespace vespalib::eval
} // namespace vespalib
