// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_tensor_create_function.h"
#include "dense_tensor_view.h"
#include <vespa/eval/eval/value.h>
#include <vespa/eval/tensor/tensor.h>

namespace vespalib::tensor {

using eval::Value;
using eval::DoubleValue;
using eval::ValueType;
using eval::TensorSpec;
using eval::TensorFunction;
using Child = eval::TensorFunction::Child;
using eval::as;
using namespace eval::tensor_function;

namespace {

template <typename CT>
void my_tensor_create_op(eval::InterpretedFunction::State &state, uint64_t param) {
    const auto *self = (const DenseTensorCreateFunction::Self *)(param);
    size_t pending_cells = self->result_size;
    ArrayRef<CT> cells = state.stash.create_array<CT>(pending_cells);
    while (pending_cells-- > 0) {
        cells[pending_cells] = (CT) state.peek(0).as_double();
        state.stack.pop_back();
    }
    const Value &result = state.stash.create<DenseTensorView>(self->result_type, TypedCells(cells)); 
    state.stack.emplace_back(result);
}

struct MyTensorCreateOp {
    template <typename CT>
    static auto get_fun() { return my_tensor_create_op<CT>; }
};

size_t get_index(const TensorSpec::Address &addr, const ValueType &type) {
    size_t cell_idx = 0;
    for (const auto &binding: addr) {
        size_t dim_idx = type.dimension_index(binding.first);
        assert(dim_idx != ValueType::Dimension::npos);
        assert(binding.second.is_indexed());
        cell_idx *= type.dimensions()[dim_idx].size;
        cell_idx += binding.second.index;
    }
    return cell_idx;
}

} // namespace vespalib::tensor::<unnamed>

DenseTensorCreateFunction::DenseTensorCreateFunction(const ValueType &res_type, std::vector<Child> children)
    : TensorFunction(),
      _self(res_type, children.size()),
      _children(std::move(children))
{
}

DenseTensorCreateFunction::~DenseTensorCreateFunction() = default;

void
DenseTensorCreateFunction::push_children(std::vector<Child::CREF> &target) const
{
    for (const Child &c : _children) {
        target.emplace_back(c);
    }
}

eval::InterpretedFunction::Instruction
DenseTensorCreateFunction::compile_self(Stash &) const
{
    static_assert(sizeof(uint64_t) == sizeof(&_self));
    auto op = select_1<MyTensorCreateOp>(result_type().cell_type());
    return eval::InterpretedFunction::Instruction(op, (uint64_t)&_self);
}

const TensorFunction &
DenseTensorCreateFunction::optimize(const eval::TensorFunction &expr, Stash &stash)
{
    if (auto create = as<Create>(expr)) {
        if (expr.result_type().is_dense()) {
            size_t num_cells = expr.result_type().dense_subspace_size();
            const auto &zero_value = stash.create<DoubleValue>(0.0);
            const auto &zero_node = const_value(zero_value, stash);
            std::vector<Child> children(num_cells, zero_node);
            for (const auto &cell: create->spec()) {
                size_t cell_idx = get_index(cell.first, expr.result_type());
                children[cell_idx] = cell.second;
            }
            return stash.create<DenseTensorCreateFunction>(expr.result_type(), std::move(children));
        }
    }
    return expr;
}

} // namespace vespalib::tensor
