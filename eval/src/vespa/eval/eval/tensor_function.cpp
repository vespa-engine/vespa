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

namespace {

using State = InterpretedFunction::State;
using Instruction = InterpretedFunction::Instruction;

//-----------------------------------------------------------------------------

template <typename T, typename IN>
uint64_t wrap_param(const IN &value_in) {
    const T &value = value_in;
    return (uint64_t)&value;
}

template <typename T>
const T &unwrap_param(uint64_t param) { return *((const T *)param); }

//-----------------------------------------------------------------------------

uint64_t to_param(map_fun_t value) { return (uint64_t)value; }
uint64_t to_param(join_fun_t value) { return (uint64_t)value; }
map_fun_t to_map_fun(uint64_t param) { return (map_fun_t)param; }
join_fun_t to_join_fun(uint64_t param) { return (join_fun_t)param; }

//-----------------------------------------------------------------------------

void op_load_const(State &state, uint64_t param) {
    state.stack.push_back(unwrap_param<Value>(param));
}

void op_load_param(State &state, uint64_t param) {
    state.stack.push_back(state.params->resolve(param, state.stash));
}

//-----------------------------------------------------------------------------

void op_double_map(State &state, uint64_t param) {
    state.replace(1, state.stash.create<DoubleValue>(to_map_fun(param)(state.peek(0).as_double())));
}

void op_double_mul(State &state, uint64_t) {
    state.replace(2, state.stash.create<DoubleValue>(state.peek(1).as_double() * state.peek(0).as_double()));
}

void op_double_add(State &state, uint64_t) {
    state.replace(2, state.stash.create<DoubleValue>(state.peek(1).as_double() + state.peek(0).as_double()));
}

void op_double_join(State &state, uint64_t param) {
    state.replace(2, state.stash.create<DoubleValue>(to_join_fun(param)(state.peek(1).as_double(), state.peek(0).as_double())));
}

//-----------------------------------------------------------------------------

void op_tensor_map(State &state, uint64_t param) {
    state.replace(1, state.engine.map(state.peek(0), to_map_fun(param), state.stash));
}

void op_tensor_join(State &state, uint64_t param) {
    state.replace(2, state.engine.join(state.peek(1), state.peek(0), to_join_fun(param), state.stash));
}

using ReduceParams = std::pair<Aggr,std::vector<vespalib::string>>;
void op_tensor_reduce(State &state, uint64_t param) {
    const ReduceParams &params = unwrap_param<ReduceParams>(param);
    state.replace(1, state.engine.reduce(state.peek(0), params.first, params.second, state.stash));
}

using RenameParams = std::pair<std::vector<vespalib::string>,std::vector<vespalib::string>>;
void op_tensor_rename(State &state, uint64_t param) {
    const RenameParams &params = unwrap_param<RenameParams>(param);
    state.replace(1, state.engine.rename(state.peek(0), params.first, params.second, state.stash));
}

void op_tensor_concat(State &state, uint64_t param) {
    const vespalib::string &dimension = unwrap_param<vespalib::string>(param);
    state.replace(2, state.engine.concat(state.peek(1), state.peek(0), dimension, state.stash));
}

} // namespace vespalib::eval::tensor_function

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
ConstValue::eval(const TensorEngine &, const LazyParams &, Stash &) const
{
    return _value;
}

Instruction
ConstValue::compile_self(Stash &) const
{
    return Instruction(op_load_const, wrap_param<Value>(_value));
}

//-----------------------------------------------------------------------------

const Value &
Inject::eval(const TensorEngine &, const LazyParams &params, Stash &stash) const
{
    return params.resolve(_param_idx, stash);
}

Instruction
Inject::compile_self(Stash &) const
{
    return Instruction(op_load_param, _param_idx);
}

//-----------------------------------------------------------------------------

const Value &
Reduce::eval(const TensorEngine &engine, const LazyParams &params, Stash &stash) const
{
    const Value &a = child().eval(engine, params, stash);
    return engine.reduce(a, _aggr, _dimensions, stash);
}

Instruction
Reduce::compile_self(Stash &stash) const
{
    ReduceParams &params = stash.create<ReduceParams>(_aggr, _dimensions);
    return Instruction(op_tensor_reduce, wrap_param<ReduceParams>(params));
}

//-----------------------------------------------------------------------------

const Value &
Map::eval(const TensorEngine &engine, const LazyParams &params, Stash &stash) const
{
    const Value &a = child().eval(engine, params, stash);
    return engine.map(a, _function, stash);
}

Instruction
Map::compile_self(Stash &) const
{
    if (result_type().is_double()) {
        return Instruction(op_double_map, to_param(_function));
    }
    return Instruction(op_tensor_map, to_param(_function));
}

//-----------------------------------------------------------------------------

const Value &
Join::eval(const TensorEngine &engine, const LazyParams &params, Stash &stash) const
{
    const Value &a = lhs().eval(engine, params, stash);
    const Value &b = rhs().eval(engine, params, stash);
    return engine.join(a, b, _function, stash);
}

Instruction
Join::compile_self(Stash &) const
{
    if (result_type().is_double()) {
        if (_function == operation::Mul::f) {
            return Instruction(op_double_mul);
        }
        if (_function == operation::Add::f) {
            return Instruction(op_double_add);
        }
        return Instruction(op_double_join, to_param(_function));
    }
    return Instruction(op_tensor_join, to_param(_function));
}

//-----------------------------------------------------------------------------

const Value &
Concat::eval(const TensorEngine &engine, const LazyParams &params, Stash &stash) const
{
    const Value &a = lhs().eval(engine, params, stash);
    const Value &b = rhs().eval(engine, params, stash);
    return engine.concat(a, b, _dimension, stash);
}

Instruction
Concat::compile_self(Stash &) const
{
    return Instruction(op_tensor_concat, wrap_param<vespalib::string>(_dimension));
}

//-----------------------------------------------------------------------------

const Value &
Rename::eval(const TensorEngine &engine, const LazyParams &params, Stash &stash) const
{
    const Value &a = child().eval(engine, params, stash);
    return engine.rename(a, _from, _to, stash);
}

Instruction
Rename::compile_self(Stash &stash) const
{
    RenameParams &params = stash.create<RenameParams>(_from, _to);
    return Instruction(op_tensor_rename, wrap_param<RenameParams>(params));
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
If::eval(const TensorEngine &engine, const LazyParams &params, Stash &stash) const
{
    return (cond().eval(engine, params, stash).as_bool()
            ? true_child().eval(engine, params, stash)
            : false_child().eval(engine, params, stash));
}

Instruction
If::compile_self(Stash &) const
{
    // 'if' is handled directly by compile_tensor_function to enable
    // lazy-evaluation of true/false sub-expressions.
    abort();
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
    ValueType result_type = ValueType::either(true_child.result_type(), false_child.result_type());
    return stash.create<If>(result_type, cond, true_child, false_child);
}

} // namespace vespalib::eval::tensor_function
} // namespace vespalib::eval
} // namespace vespalib
