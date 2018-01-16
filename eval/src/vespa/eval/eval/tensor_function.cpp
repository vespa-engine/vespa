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

const Value &
Inject::eval(ConstArrayRef<Value::CREF> params, Stash &) const
{
    return params[tensor_id];
}

void
Inject::push_children(std::vector<Child::CREF> &) const
{
}

//-----------------------------------------------------------------------------

const Value &
Reduce::eval(ConstArrayRef<Value::CREF> params, Stash &stash) const 
{
    const Value &a = tensor.get().eval(params, stash);
    const TensorEngine &engine = infer_engine({a});
    return engine.reduce(a, aggr, dimensions, stash);
}

void
Reduce::push_children(std::vector<Child::CREF> &children) const
{
    children.emplace_back(tensor);
}

//-----------------------------------------------------------------------------

const Value &
Map::eval(ConstArrayRef<Value::CREF> params, Stash &stash) const
{
    const Value &a = tensor.get().eval(params, stash);
    const TensorEngine &engine = infer_engine({a});
    return engine.map(a, function, stash);
}

void
Map::push_children(std::vector<Child::CREF> &children) const
{
    children.emplace_back(tensor);
}

//-----------------------------------------------------------------------------

const Value &
Join::eval(ConstArrayRef<Value::CREF> params, Stash &stash) const
{
    const Value &a = lhs_tensor.get().eval(params, stash);
    const Value &b = rhs_tensor.get().eval(params, stash);
    const TensorEngine &engine = infer_engine({a,b});
    return engine.join(a, b, function, stash);
}

void
Join::push_children(std::vector<Child::CREF> &children) const
{
    children.emplace_back(lhs_tensor);
    children.emplace_back(rhs_tensor);
}

//-----------------------------------------------------------------------------

const Node &inject(const ValueType &type, size_t tensor_id, Stash &stash) {
    return stash.create<Inject>(type, tensor_id);
}

const Node &reduce(const Node &tensor, Aggr aggr, const std::vector<vespalib::string> &dimensions, Stash &stash) {
    ValueType result_type = tensor.result_type.reduce(dimensions);
    return stash.create<Reduce>(result_type, tensor, aggr, dimensions);
}

const Node &map(const Node &tensor, map_fun_t function, Stash &stash) {
    ValueType result_type = tensor.result_type;
    return stash.create<Map>(result_type, tensor, function);
}

const Node &join(const Node &lhs_tensor, const Node &rhs_tensor, join_fun_t function, Stash &stash) {
    ValueType result_type = ValueType::join(lhs_tensor.result_type, rhs_tensor.result_type);
    return stash.create<Join>(result_type, lhs_tensor, rhs_tensor, function);
}

} // namespace vespalib::eval::tensor_function
} // namespace vespalib::eval
} // namespace vespalib
