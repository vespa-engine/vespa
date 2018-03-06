// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "vector_from_doubles_function.h"
#include "dense_tensor.h"
#include "dense_tensor_view.h"
#include <vespa/eval/eval/operation.h>
#include <vespa/eval/eval/value.h>
#include <vespa/eval/tensor/tensor.h>

namespace vespalib::tensor {

using CellsRef = DenseTensorView::CellsRef;
using eval::Value;
using eval::ValueType;
using eval::TensorFunction;
using Child = eval::TensorFunction::Child;
using eval::as;
using namespace eval::tensor_function;
using namespace eval::operation;

namespace {

void my_vector_from_doubles_op(eval::InterpretedFunction::State &state, uint64_t param) {
    const auto *self = (const VectorFromDoublesFunction::Self *)(param);
    ArrayRef<double> outputCells = state.stash.create_array<double>(self->resultSize);
    for (size_t i = self->resultSize; i-- > 0; ) {
        outputCells[i] = state.peek(0).as_double();
        state.stack.pop_back();
    }
    const Value &result = state.stash.create<DenseTensorView>(self->resultType, outputCells);
    state.stack.push_back(result);
}

size_t vector_size(const TensorFunction &child, const vespalib::string &dimension) {
    if (child.result_type().is_double()) {
        return 1;
    }
    if (auto vfd = as<VectorFromDoublesFunction>(child)) {
        if (vfd->dimension() == dimension) {
            return vfd->size();
        }
    }
    return 0;
}

void flatten_into(const TensorFunction &child, std::vector<Child> &vec) {
    if (child.result_type().is_double()) {
        vec.push_back(child);
    } else {
        std::vector<Child::CREF> tmp;
        child.push_children(tmp);
        for (const Child &c : tmp) {
            assert(c.get().result_type().is_double());
            vec.push_back(c);
        }
    }
}

std::vector<Child> flatten(const TensorFunction &lhs, const TensorFunction &rhs) {
    std::vector<Child> vec;
    flatten_into(lhs, vec);
    flatten_into(rhs, vec);
    return vec;
}

} // namespace vespalib::tensor::<unnamed>


VectorFromDoublesFunction::VectorFromDoublesFunction(std::vector<Child> children, const ValueType &res_type)
    : TensorFunction(),
      _self(res_type, children.size()),
      _children(std::move(children))
{
}

VectorFromDoublesFunction::~VectorFromDoublesFunction()
{
}

void
VectorFromDoublesFunction::push_children(std::vector<Child::CREF> &target) const
{
    for (const Child &c : _children) {
        target.push_back(c);
    }
}

eval::InterpretedFunction::Instruction
VectorFromDoublesFunction::compile_self(Stash &) const
{
    return eval::InterpretedFunction::Instruction(my_vector_from_doubles_op, (uint64_t)&_self);
}

void
VectorFromDoublesFunction::dump_tree(eval::DumpTarget &target) const
{
    target.node("VectorFromDoubles");
    target.arg("size").value(size());
    target.arg("dimension").value(dimension());
    for (const auto & child : _children) {
        target.child("child", child.get());
    }
}


const TensorFunction &
VectorFromDoublesFunction::optimize(const eval::TensorFunction &expr, Stash &stash)
{
    if (auto concat = as<Concat>(expr)) {
        const vespalib::string &dimension = concat->dimension();
        size_t a_size = vector_size(concat->lhs(), dimension);
        size_t b_size = vector_size(concat->rhs(), dimension);
        if ((a_size > 0) && (b_size > 0)) {
            auto children = flatten(concat->lhs(), concat->rhs());
            assert(children.size() == (a_size + b_size));
            return stash.create<VectorFromDoublesFunction>(std::move(children), expr.result_type());
        }
    }
    return expr;
}

} // namespace vespalib::tensor
