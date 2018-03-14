// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tensor_function.h"
#include "value.h"
#include "operation.h"
#include "tensor.h"
#include "tensor_engine.h"
#include "simple_tensor_engine.h"
#include "visit_stuff.h"
#include <vespa/vespalib/objects/objectdumper.h>

namespace vespalib {
namespace eval {

vespalib::string
TensorFunction::as_string() const
{
    ObjectDumper dumper;
    ::visit(dumper, "", *this);
    return dumper.toString();
}

void
TensorFunction::visit_self(vespalib::ObjectVisitor &visitor) const
{
    visitor.visitString("result_type", result_type().to_spec());
    visitor.visitBool("result_is_mutable", result_is_mutable());
}

void
TensorFunction::visit_children(vespalib::ObjectVisitor &visitor) const
{
    std::vector<vespalib::eval::TensorFunction::Child::CREF> children;
    push_children(children);
    for (size_t i = 0; i < children.size(); ++i) {
        vespalib::string name = vespalib::make_string("children[%zu]", i);
        ::visit(visitor, name, children[i].get().get());
    }
}

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

//-----------------------------------------------------------------------------

void op_double_map(State &state, uint64_t param) {
    state.pop_push(state.stash.create<DoubleValue>(to_map_fun(param)(state.peek(0).as_double())));
}

void op_double_mul(State &state, uint64_t) {
    state.pop_pop_push(state.stash.create<DoubleValue>(state.peek(1).as_double() * state.peek(0).as_double()));
}

void op_double_add(State &state, uint64_t) {
    state.pop_pop_push(state.stash.create<DoubleValue>(state.peek(1).as_double() + state.peek(0).as_double()));
}

void op_double_join(State &state, uint64_t param) {
    state.pop_pop_push(state.stash.create<DoubleValue>(to_join_fun(param)(state.peek(1).as_double(), state.peek(0).as_double())));
}

//-----------------------------------------------------------------------------

void op_tensor_map(State &state, uint64_t param) {
    state.pop_push(state.engine.map(state.peek(0), to_map_fun(param), state.stash));
}

void op_tensor_join(State &state, uint64_t param) {
    state.pop_pop_push(state.engine.join(state.peek(1), state.peek(0), to_join_fun(param), state.stash));
}

using ReduceParams = std::pair<Aggr,std::vector<vespalib::string>>;
void op_tensor_reduce(State &state, uint64_t param) {
    const ReduceParams &params = unwrap_param<ReduceParams>(param);
    state.pop_push(state.engine.reduce(state.peek(0), params.first, params.second, state.stash));
}

using RenameParams = std::pair<std::vector<vespalib::string>,std::vector<vespalib::string>>;
void op_tensor_rename(State &state, uint64_t param) {
    const RenameParams &params = unwrap_param<RenameParams>(param);
    state.pop_push(state.engine.rename(state.peek(0), params.first, params.second, state.stash));
}

void op_tensor_concat(State &state, uint64_t param) {
    const vespalib::string &dimension = unwrap_param<vespalib::string>(param);
    state.pop_pop_push(state.engine.concat(state.peek(1), state.peek(0), dimension, state.stash));
}

} // namespace vespalib::eval::tensor_function

//-----------------------------------------------------------------------------

void
Leaf::push_children(std::vector<Child::CREF> &) const
{
}

//-----------------------------------------------------------------------------

void
Op1::push_children(std::vector<Child::CREF> &children) const
{
    children.emplace_back(_child);
}

void
Op1::visit_children(vespalib::ObjectVisitor &visitor) const
{
    ::visit(visitor, "child", _child.get());
}

//-----------------------------------------------------------------------------

void
Op2::push_children(std::vector<Child::CREF> &children) const
{
    children.emplace_back(_lhs);
    children.emplace_back(_rhs);
}

void
Op2::visit_children(vespalib::ObjectVisitor &visitor) const
{
    ::visit(visitor, "lhs", _lhs.get());
    ::visit(visitor, "rhs", _rhs.get());
}

//-----------------------------------------------------------------------------

Instruction
ConstValue::compile_self(Stash &) const
{
    return Instruction(op_load_const, wrap_param<Value>(_value));
}

void
ConstValue::visit_self(vespalib::ObjectVisitor &visitor) const
{
    Leaf::visit_self(visitor);
    if (result_type().is_double()) {
        visitor.visitFloat("value", _value.as_double());
    } else {
        visitor.visitString("value", "...");
    }
}

//-----------------------------------------------------------------------------

Instruction
Inject::compile_self(Stash &) const
{
    return Instruction::fetch_param(_param_idx);
}

void
Inject::visit_self(vespalib::ObjectVisitor &visitor) const
{
    Leaf::visit_self(visitor);
    visitor.visitInt("param_idx", _param_idx);
}

//-----------------------------------------------------------------------------

Instruction
Reduce::compile_self(Stash &stash) const
{
    ReduceParams &params = stash.create<ReduceParams>(_aggr, _dimensions);
    return Instruction(op_tensor_reduce, wrap_param<ReduceParams>(params));
}

void
Reduce::visit_self(vespalib::ObjectVisitor &visitor) const
{
    Op1::visit_self(visitor);
    ::visit(visitor, "aggr", _aggr);
    ::visit(visitor, "dimensions", visit::DimList(_dimensions));
}

//-----------------------------------------------------------------------------

Instruction
Map::compile_self(Stash &) const
{
    if (result_type().is_double()) {
        return Instruction(op_double_map, to_param(_function));
    }
    return Instruction(op_tensor_map, to_param(_function));
}

void
Map::visit_self(vespalib::ObjectVisitor &visitor) const
{
    Op1::visit_self(visitor);
    ::visit(visitor, "function", _function);
}

//-----------------------------------------------------------------------------

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

void
Join::visit_self(vespalib::ObjectVisitor &visitor) const
{
    Op2::visit_self(visitor);
    ::visit(visitor, "function", _function);
}

//-----------------------------------------------------------------------------

Instruction
Concat::compile_self(Stash &) const
{
    return Instruction(op_tensor_concat, wrap_param<vespalib::string>(_dimension));
}

void
Concat::visit_self(vespalib::ObjectVisitor &visitor) const
{
    Op2::visit_self(visitor);
    visitor.visitString("dimension", _dimension);
}

//-----------------------------------------------------------------------------

Instruction
Rename::compile_self(Stash &stash) const
{
    RenameParams &params = stash.create<RenameParams>(_from, _to);
    return Instruction(op_tensor_rename, wrap_param<RenameParams>(params));
}

void
Rename::visit_self(vespalib::ObjectVisitor &visitor) const
{
    Op1::visit_self(visitor);
    ::visit(visitor, "from_to", visit::FromTo(_from, _to));
}

//-----------------------------------------------------------------------------

void
If::push_children(std::vector<Child::CREF> &children) const
{
    children.emplace_back(_cond);
    children.emplace_back(_true_child);
    children.emplace_back(_false_child);
}

Instruction
If::compile_self(Stash &) const
{
    // 'if' is handled directly by compile_tensor_function to enable
    // lazy-evaluation of true/false sub-expressions.
    abort();
}

void
If::visit_children(vespalib::ObjectVisitor &visitor) const
{
    ::visit(visitor, "cond", _cond.get());
    ::visit(visitor, "true_child", _true_child.get());
    ::visit(visitor, "false_child", _false_child.get());
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
