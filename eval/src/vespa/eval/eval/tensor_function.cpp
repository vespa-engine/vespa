// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tensor_function.h"
#include "value.h"
#include "operation.h"
#include "tensor.h"
#include "tensor_engine.h"
#include "simple_tensor_engine.h"

namespace vespalib {
namespace eval {
namespace tensor_function {

const TensorEngine &infer_engine(const std::initializer_list<Value::CREF> &values) {
    for (const Value &value: values) {
        if (auto tensor = value.as_tensor()) {
            return tensor->engine();
        }
    }
    return SimpleTensorEngine::ref();
}

//-----------------------------------------------------------------------------

void
Inject::push_children(std::vector<Child::CREF> &) const
{
}

const Value &
Inject::eval(const LazyParams &params, Stash &stash) const
{
    return params.resolve(_param_idx, stash);
}

//-----------------------------------------------------------------------------

void
Reduce::push_children(std::vector<Child::CREF> &children) const
{
    children.emplace_back(_child);
}

const Value &
Reduce::eval(const LazyParams &params, Stash &stash) const
{
    const Value &a = child().eval(params, stash);
    const TensorEngine &engine = infer_engine({a});
    return engine.reduce(a, _aggr, _dimensions, stash);
}

//-----------------------------------------------------------------------------

void
Map::push_children(std::vector<Child::CREF> &children) const
{
    children.emplace_back(_child);
}

const Value &
Map::eval(const LazyParams &params, Stash &stash) const
{
    const Value &a = child().eval(params, stash);
    const TensorEngine &engine = infer_engine({a});
    return engine.map(a, _function, stash);
}

//-----------------------------------------------------------------------------

void
Join::push_children(std::vector<Child::CREF> &children) const
{
    children.emplace_back(_lhs);
    children.emplace_back(_rhs);
}

const Value &
Join::eval(const LazyParams &params, Stash &stash) const
{
    const Value &a = lhs().eval(params, stash);
    const Value &b = rhs().eval(params, stash);
    const TensorEngine &engine = infer_engine({a,b});
    return engine.join(a, b, _function, stash);
}

//-----------------------------------------------------------------------------

const Node &inject(const ValueType &type, size_t param_idx, Stash &stash) {
    return stash.create<Inject>(type, param_idx);
}

const Node &reduce(const Node &child, Aggr aggr, const std::vector<vespalib::string> &dimensions, Stash &stash) {
    ValueType result_type = child.result_type().reduce(dimensions);
    return stash.create<Reduce>(result_type, child, aggr, dimensions);
}

const Node &map(const Node &child, map_fun_t function, Stash &stash) {
    ValueType result_type = child.result_type();
    return stash.create<Map>(result_type, child, function);
}

const Node &join(const Node &lhs, const Node &rhs, join_fun_t function, Stash &stash) {
    ValueType result_type = ValueType::join(lhs.result_type(), rhs.result_type());
    return stash.create<Join>(result_type, lhs, rhs, function);
}

} // namespace vespalib::eval::tensor_function
} // namespace vespalib::eval
} // namespace vespalib
