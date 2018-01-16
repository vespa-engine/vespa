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
Leaf::push_children(std::vector<Child::CREF> &) const
{
}

void
Op1::push_children(std::vector<Child::CREF> &children) const
{
    children.emplace_back(_child);
}

void
Op2::push_children(std::vector<Child::CREF> &children) const
{
    children.emplace_back(_lhs);
    children.emplace_back(_rhs);
}

//-----------------------------------------------------------------------------

const Value &
ConstValue::eval(const LazyParams &, Stash &) const
{
    return _value;
}

//-----------------------------------------------------------------------------

const Value &
Inject::eval(const LazyParams &params, Stash &stash) const
{
    return params.resolve(_param_idx, stash);
}

//-----------------------------------------------------------------------------

const Value &
Reduce::eval(const LazyParams &params, Stash &stash) const
{
    const Value &a = child().eval(params, stash);
    const TensorEngine &engine = infer_engine({a});
    return engine.reduce(a, _aggr, _dimensions, stash);
}

//-----------------------------------------------------------------------------

const Value &
Map::eval(const LazyParams &params, Stash &stash) const
{
    const Value &a = child().eval(params, stash);
    const TensorEngine &engine = infer_engine({a});
    return engine.map(a, _function, stash);
}

//-----------------------------------------------------------------------------

const Value &
Join::eval(const LazyParams &params, Stash &stash) const
{
    const Value &a = lhs().eval(params, stash);
    const Value &b = rhs().eval(params, stash);
    const TensorEngine &engine = infer_engine({a,b});
    return engine.join(a, b, _function, stash);
}

//-----------------------------------------------------------------------------

const Value &
Concat::eval(const LazyParams &params, Stash &stash) const
{
    const Value &a = lhs().eval(params, stash);
    const Value &b = rhs().eval(params, stash);
    const TensorEngine &engine = infer_engine({a,b});
    return engine.concat(a, b, _dimension, stash);
}

//-----------------------------------------------------------------------------

const Value &
Rename::eval(const LazyParams &params, Stash &stash) const
{
    const Value &a = child().eval(params, stash);
    const TensorEngine &engine = infer_engine({a});
    return engine.rename(a, _from, _to, stash);
}

//-----------------------------------------------------------------------------

void
If::push_children(std::vector<Child::CREF> &children) const
{
    children.emplace_back(_cond);
    children.emplace_back(_true_child);
    children.emplace_back(_false_child);
}

const Value &
If::eval(const LazyParams &params, Stash &stash) const
{
    return (cond().eval(params, stash).as_bool()
            ? true_child().eval(params, stash)
            : false_child().eval(params, stash));
}

//-----------------------------------------------------------------------------

const Node &const_value(const Value &value, Stash &stash) {
    return stash.create<ConstValue>(value);
}

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

const Node &concat(const Node &lhs, const Node &rhs, const vespalib::string &dimension, Stash &stash) {
    ValueType result_type = ValueType::concat(lhs.result_type(), rhs.result_type(), dimension);
    return stash.create<Concat>(result_type, lhs, rhs, dimension);
}

const Node &rename(const Node &child, const std::vector<vespalib::string> &from, const std::vector<vespalib::string> &to, Stash &stash) {
    ValueType result_type = child.result_type().rename(from, to);
    return stash.create<Rename>(result_type, child, from, to);
}

const Node &if_node(const Node &cond, const Node &true_child, const Node &false_child, Stash &stash) {
    ValueType result_type = true_child.result_type();
    if (result_type != false_child.result_type()) {
        result_type = ValueType::any_type();
    }
    return stash.create<If>(result_type, cond, true_child, false_child);
}

} // namespace vespalib::eval::tensor_function
} // namespace vespalib::eval
} // namespace vespalib
