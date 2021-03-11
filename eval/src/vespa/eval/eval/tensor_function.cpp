// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tensor_function.h"
#include "value.h"
#include "operation.h"
#include "visit_stuff.h"
#include "string_stuff.h"
#include "value_type_spec.h"
#include <vespa/eval/instruction/generic_cell_cast.h>
#include <vespa/eval/instruction/generic_concat.h>
#include <vespa/eval/instruction/generic_create.h>
#include <vespa/eval/instruction/generic_join.h>
#include <vespa/eval/instruction/generic_lambda.h>
#include <vespa/eval/instruction/generic_map.h>
#include <vespa/eval/instruction/generic_merge.h>
#include <vespa/eval/instruction/generic_peek.h>
#include <vespa/eval/instruction/generic_reduce.h>
#include <vespa/eval/instruction/generic_rename.h>
#include <vespa/vespalib/objects/objectdumper.h>
#include <vespa/vespalib/objects/visit.hpp>

#include <vespa/log/log.h>
LOG_SETUP(".eval.eval.tensor_function");

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

void op_load_const(State &state, uint64_t param) {
    state.stack.push_back(unwrap_param<Value>(param));
}

//-----------------------------------------------------------------------------

} // namespace vespalib::eval::tensor_function::<unnamed>

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
ConstValue::compile_self(const ValueBuilderFactory &, Stash &) const
{
    return Instruction(op_load_const, wrap_param<Value>(_value));
}

void
ConstValue::visit_self(vespalib::ObjectVisitor &visitor) const
{
    Super::visit_self(visitor);
    if (result_type().is_double()) {
        visitor.visitFloat("value", _value.as_double());
    } else {
        visitor.visitString("value", "...");
    }
}

//-----------------------------------------------------------------------------

Instruction
Inject::compile_self(const ValueBuilderFactory &, Stash &) const
{
    return Instruction::fetch_param(_param_idx);
}

void
Inject::visit_self(vespalib::ObjectVisitor &visitor) const
{
    Super::visit_self(visitor);
    visitor.visitInt("param_idx", _param_idx);
}

//-----------------------------------------------------------------------------

Instruction
Reduce::compile_self(const ValueBuilderFactory &factory, Stash &stash) const
{
    return instruction::GenericReduce::make_instruction(result_type(), child().result_type(), aggr(), dimensions(), factory, stash);
}

void
Reduce::visit_self(vespalib::ObjectVisitor &visitor) const
{
    Super::visit_self(visitor);
    ::visit(visitor, "aggr", _aggr);
    ::visit(visitor, "dimensions", visit::DimList(_dimensions));
}

//-----------------------------------------------------------------------------

Instruction
Map::compile_self(const ValueBuilderFactory &, Stash &stash) const
{
    return instruction::GenericMap::make_instruction(result_type(), child().result_type(), _function, stash);
}

void
Map::visit_self(vespalib::ObjectVisitor &visitor) const
{
    Super::visit_self(visitor);
    ::visit(visitor, "function", _function);
}

//-----------------------------------------------------------------------------

Instruction
Join::compile_self(const ValueBuilderFactory &factory, Stash &stash) const
{
    return instruction::GenericJoin::make_instruction(result_type(), lhs().result_type(), rhs().result_type(), function(), factory, stash);
}

void
Join::visit_self(vespalib::ObjectVisitor &visitor) const
{
    Super::visit_self(visitor);
    ::visit(visitor, "function", _function);
}

//-----------------------------------------------------------------------------

Instruction
Merge::compile_self(const ValueBuilderFactory &factory, Stash &stash) const
{
    return instruction::GenericMerge::make_instruction(result_type(), lhs().result_type(), rhs().result_type(), function(), factory, stash);
}

void
Merge::visit_self(vespalib::ObjectVisitor &visitor) const
{
    Super::visit_self(visitor);
    ::visit(visitor, "function", _function);
}

//-----------------------------------------------------------------------------

Instruction
Concat::compile_self(const ValueBuilderFactory &factory, Stash &stash) const
{
    return instruction::GenericConcat::make_instruction(result_type(), lhs().result_type(), rhs().result_type(), dimension(), factory, stash);
}

void
Concat::visit_self(vespalib::ObjectVisitor &visitor) const
{
    Super::visit_self(visitor);
    visitor.visitString("dimension", _dimension);
}

//-----------------------------------------------------------------------------

InterpretedFunction::Instruction
CellCast::compile_self(const ValueBuilderFactory &, Stash &stash) const
{
    return instruction::GenericCellCast::make_instruction(result_type(), child().result_type(), cell_type(), stash);
}

void
CellCast::visit_self(vespalib::ObjectVisitor &visitor) const
{
    Super::visit_self(visitor);
    visitor.visitString("cell_type", value_type::cell_type_to_name(cell_type()));
}

//-----------------------------------------------------------------------------

void
Create::push_children(std::vector<Child::CREF> &children) const
{
    for (const auto &cell: _map) {
        children.emplace_back(cell.second);
    }
}

Create::Spec
Create::make_spec() const
{
    Spec generic_spec;
    size_t child_idx = 0;
    for (const auto & kv : map()) {
        generic_spec[kv.first] = child_idx++;
    }
    return generic_spec;
}

Instruction
Create::compile_self(const ValueBuilderFactory &factory, Stash &stash) const
{
    return instruction::GenericCreate::make_instruction(result_type(), make_spec(), factory, stash);
}

void
Create::visit_children(vespalib::ObjectVisitor &visitor) const
{
    for (const auto &cell: _map) {
        ::visit(visitor, ::vespalib::eval::as_string(cell.first), cell.second.get());
    }
}

//-----------------------------------------------------------------------------

namespace {

bool step_labels(std::vector<size_t> &labels, const ValueType &type) {
    for (size_t idx = labels.size(); idx-- > 0; ) {
        if (++labels[idx] < type.dimensions()[idx].size) {
            return true;
        } else {
            labels[idx] = 0;
        }
    }
    return false;
}

struct ParamProxy : public LazyParams {
    const std::vector<size_t> &labels;
    const LazyParams          &params;
    const std::vector<size_t> &bindings;
    ParamProxy(const std::vector<size_t> &labels_in, const LazyParams &params_in, const std::vector<size_t> &bindings_in)
        : labels(labels_in), params(params_in), bindings(bindings_in) {}
    const Value &resolve(size_t idx, Stash &stash) const override {
        if (idx < labels.size()) {
            return stash.create<DoubleValue>(labels[idx]);
        }
        return params.resolve(bindings[idx - labels.size()], stash);
    }
};

}

TensorSpec
Lambda::create_spec_impl(const ValueType &type, const LazyParams &params, const std::vector<size_t> &bind, const InterpretedFunction &fun)
{
    std::vector<size_t> labels(type.dimensions().size(), 0);
    ParamProxy param_proxy(labels, params, bind);
    InterpretedFunction::Context ctx(fun);
    TensorSpec spec(type.to_spec());
    do {
        TensorSpec::Address address;
        for (size_t i = 0; i < labels.size(); ++i) {
            address.emplace(type.dimensions()[i].name, labels[i]);
        }
        spec.add(std::move(address), fun.eval(ctx, param_proxy).as_double());
    } while (step_labels(labels, type));
    return spec;
}

InterpretedFunction::Instruction
Lambda::compile_self(const ValueBuilderFactory &factory, Stash &stash) const
{
    return instruction::GenericLambda::make_instruction(*this, factory, stash);
}

void
Lambda::visit_self(vespalib::ObjectVisitor &visitor) const
{
    Super::visit_self(visitor);
    ::visit(visitor, "bindings", _bindings);
}

//-----------------------------------------------------------------------------

void
Peek::push_children(std::vector<Child::CREF> &children) const
{
    children.emplace_back(_param);
    for (const auto &dim: _map) {
        std::visit(vespalib::overload
                   {
                       [&](const Child &child) {
                           children.emplace_back(child);
                       },
                       [](const TensorSpec::Label &) noexcept {}
                   }, dim.second);
    }
}

Peek::Spec
Peek::make_spec() const
{
    Spec generic_spec;
    // the value peeked is child 0, so
    // children (for label computation) in spec start at 1:
    size_t child_idx = 1;
    for (const auto & [dim_name, label_or_child] : map()) {
        std::visit(vespalib::overload {
                [&,&dim_name = dim_name](const TensorSpec::Label &label) {
                    generic_spec.emplace(dim_name, label);
                },
                [&,&dim_name = dim_name](const TensorFunction::Child &) {
                    generic_spec.emplace(dim_name, child_idx++);
                }
            }, label_or_child);
    }
    return generic_spec;
}

Instruction
Peek::compile_self(const ValueBuilderFactory &factory, Stash &stash) const
{
    return instruction::GenericPeek::make_instruction(result_type(), param_type(), make_spec(), factory, stash);
}

void
Peek::visit_children(vespalib::ObjectVisitor &visitor) const
{
    ::visit(visitor, "param", _param.get());
    for (const auto &dim: _map) {
        std::visit(vespalib::overload
                   {
                       [&](const TensorSpec::Label &label) {
                           if (label.is_mapped()) {
                               ::visit(visitor, dim.first, label.name);
                           } else {
                               ::visit(visitor, dim.first, static_cast<int64_t>(label.index));
                           }
                       },
                       [&](const Child &child) {
                           ::visit(visitor, dim.first, child.get());
                       }
                   }, dim.second);
    }
}

//-----------------------------------------------------------------------------

Instruction
Rename::compile_self(const ValueBuilderFactory &factory, Stash &stash) const
{
    return instruction::GenericRename::make_instruction(result_type(), child().result_type(), from(), to(), factory, stash);
}

void
Rename::visit_self(vespalib::ObjectVisitor &visitor) const
{
    Super::visit_self(visitor);
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
If::compile_self(const ValueBuilderFactory &, Stash &) const
{
    // 'if' is handled directly by compile_tensor_function to enable
    // lazy-evaluation of true/false sub-expressions.
    LOG_ABORT("should not be reached");
}

void
If::visit_children(vespalib::ObjectVisitor &visitor) const
{
    ::visit(visitor, "cond", _cond.get());
    ::visit(visitor, "true_child", _true_child.get());
    ::visit(visitor, "false_child", _false_child.get());
}

//-----------------------------------------------------------------------------

const TensorFunction &const_value(const Value &value, Stash &stash) {
    return stash.create<ConstValue>(value);
}

const TensorFunction &inject(const ValueType &type, size_t param_idx, Stash &stash) {
    return stash.create<Inject>(type, param_idx);
}

const TensorFunction &reduce(const TensorFunction &child, Aggr aggr, const std::vector<vespalib::string> &dimensions, Stash &stash) {
    ValueType result_type = child.result_type().reduce(dimensions);
    return stash.create<Reduce>(result_type, child, aggr, dimensions);
}

const TensorFunction &map(const TensorFunction &child, map_fun_t function, Stash &stash) {
    ValueType result_type = child.result_type().map();
    return stash.create<Map>(result_type, child, function);
}

const TensorFunction &join(const TensorFunction &lhs, const TensorFunction &rhs, join_fun_t function, Stash &stash) {
    ValueType result_type = ValueType::join(lhs.result_type(), rhs.result_type());
    return stash.create<Join>(result_type, lhs, rhs, function);
}

const TensorFunction &merge(const TensorFunction &lhs, const TensorFunction &rhs, join_fun_t function, Stash &stash) {
    ValueType result_type = ValueType::merge(lhs.result_type(), rhs.result_type());
    return stash.create<Merge>(result_type, lhs, rhs, function);
}

const TensorFunction &concat(const TensorFunction &lhs, const TensorFunction &rhs, const vespalib::string &dimension, Stash &stash) {
    ValueType result_type = ValueType::concat(lhs.result_type(), rhs.result_type(), dimension);
    return stash.create<Concat>(result_type, lhs, rhs, dimension);
}

const TensorFunction &create(const ValueType &type, const std::map<TensorSpec::Address,TensorFunction::CREF> &spec, Stash &stash) {
    return stash.create<Create>(type, spec);
}

const TensorFunction &lambda(const ValueType &type, const std::vector<size_t> &bindings, const Function &function, NodeTypes node_types, Stash &stash) {
    return stash.create<Lambda>(type, bindings, function, std::move(node_types));
}

const TensorFunction &cell_cast(const TensorFunction &child, CellType cell_type, Stash &stash) {
    ValueType result_type = child.result_type().cell_cast(cell_type);
    return stash.create<CellCast>(result_type, child, cell_type);
}

const TensorFunction &peek(const TensorFunction &param, const std::map<vespalib::string, std::variant<TensorSpec::Label, TensorFunction::CREF>> &spec, Stash &stash) {
    std::vector<vespalib::string> dimensions;
    for (const auto &dim_spec: spec) {
        dimensions.push_back(dim_spec.first);
    }
    assert(!dimensions.empty());
    ValueType result_type = param.result_type().peek(dimensions);
    return stash.create<Peek>(result_type, param, spec);
}

const TensorFunction &rename(const TensorFunction &child, const std::vector<vespalib::string> &from, const std::vector<vespalib::string> &to, Stash &stash) {
    ValueType result_type = child.result_type().rename(from, to);
    return stash.create<Rename>(result_type, child, from, to);
}

const TensorFunction &if_node(const TensorFunction &cond, const TensorFunction &true_child, const TensorFunction &false_child, Stash &stash) {
    ValueType result_type = ValueType::either(true_child.result_type(), false_child.result_type());
    return stash.create<If>(result_type, cond, true_child, false_child);
}

} // namespace vespalib::eval::tensor_function
} // namespace vespalib::eval
} // namespace vespalib
