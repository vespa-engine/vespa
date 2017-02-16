// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/vespalib/util/stringfmt.h>
#include "simple_tensor_engine.h"
#include "simple_tensor.h"
#include "operation.h"

namespace vespalib {
namespace eval {

namespace {

struct Op2Aggr : Aggregator {
    double res = 0.0;
    const BinaryOperation &op;
    Op2Aggr(const BinaryOperation &op_in) : op(op_in) {}
    void first(double value) override { res = value; }
    void next(double value) override { res = op.eval(res, value); }
    double result() const override { return res; }    
};

const SimpleTensor &to_simple(const Tensor &tensor) {
    assert(&tensor.engine() == &SimpleTensorEngine::ref());
    return static_cast<const SimpleTensor&>(tensor);
}

const SimpleTensor &to_simple(const Value &value, Stash &stash) {
    if (value.is_double()) {
        return stash.create<SimpleTensor>(value.as_double());
    }
    if (auto tensor = value.as_tensor()) {
        return to_simple(*tensor);
    }
    return stash.create<SimpleTensor>(); // error
}

const Value &to_value(std::unique_ptr<SimpleTensor> tensor, Stash &stash) {
    if (tensor->type().is_double()) {
        assert(tensor->cells().size() == 1u);
        return stash.create<DoubleValue>(tensor->cells()[0].value);
    }
    if (tensor->type().is_tensor()) {
        return stash.create<TensorValue>(std::move(tensor));
    }
    assert(tensor->type().is_error());
    return stash.create<ErrorValue>();
}

} // namespace vespalib::eval::<unnamed>

const SimpleTensorEngine SimpleTensorEngine::_engine;

ValueType
SimpleTensorEngine::type_of(const Tensor &tensor) const
{
    return to_simple(tensor).type();
}

bool
SimpleTensorEngine::equal(const Tensor &a, const Tensor &b) const
{
    return SimpleTensor::equal(to_simple(a), to_simple(b));
}

vespalib::string
SimpleTensorEngine::to_string(const Tensor &tensor) const
{
    const SimpleTensor &simple_tensor = to_simple(tensor);
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
    const SimpleTensor &simple_tensor = to_simple(tensor);
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

std::unique_ptr<eval::Tensor>
SimpleTensorEngine::create(const TensorSpec &spec) const
{
    return SimpleTensor::create(spec);
}

//-----------------------------------------------------------------------------

const Value &
SimpleTensorEngine::reduce(const eval::Tensor &tensor, const BinaryOperation &op, const std::vector<vespalib::string> &dimensions, Stash &stash) const
{
    Op2Aggr aggr(op);
    return to_value(to_simple(tensor).reduce(aggr, dimensions), stash);
}

const Value &
SimpleTensorEngine::map(const UnaryOperation &op, const eval::Tensor &a, Stash &stash) const
{
    return to_value(to_simple(a).map([&op](double x){ return op.eval(x); }), stash);
}

const Value &
SimpleTensorEngine::apply(const BinaryOperation &op, const eval::Tensor &a, const eval::Tensor &b, Stash &stash) const
{
    return to_value(SimpleTensor::join(to_simple(a), to_simple(b), [&op](double x, double y){ return op.eval(x, y); }), stash);
}

//-----------------------------------------------------------------------------

const Value &
SimpleTensorEngine::map(const Value &a, const std::function<double(double)> &function, Stash &stash) const
{
    return to_value(to_simple(a, stash).map(function), stash);
}

const Value &
SimpleTensorEngine::join(const Value &a, const Value &b, const std::function<double(double,double)> &function, Stash &stash) const
{
    return to_value(SimpleTensor::join(to_simple(a, stash), to_simple(b, stash), function), stash);
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
