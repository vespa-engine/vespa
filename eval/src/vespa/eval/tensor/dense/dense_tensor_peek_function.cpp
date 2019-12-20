// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_tensor_peek_function.h"
#include "dense_tensor_view.h"
#include <vespa/eval/eval/operation.h>
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
using namespace eval::operation;

namespace {

template <typename CT>
void my_tensor_peek_op(eval::InterpretedFunction::State &state, uint64_t param) {
    const auto *spec = (const std::vector<std::pair<int64_t,size_t>> *)(param);
    size_t idx = 0;
    size_t factor = 1;
    bool valid = true;
    for (const auto &dim: *spec) {
        if (dim.first >= 0) {
            idx += (dim.first * factor);
        } else {
            size_t dim_idx(round(state.peek(0).as_double()));
            state.stack.pop_back();
            valid &= (dim_idx < dim.second);
            idx += (dim_idx * factor);
        }
        factor *= dim.second;
    }
    auto cells = DenseTensorView::typify_cells<CT>(state.peek(0));
    state.stack.pop_back();
    const Value &result = state.stash.create<DoubleValue>(valid ? cells[idx] : 0.0);
    state.stack.push_back(result);
}

struct MyTensorPeekOp {
    template <typename CT>
    static auto get_fun() { return my_tensor_peek_op<CT>; }
};

} // namespace vespalib::tensor::<unnamed>

DenseTensorPeekFunction::DenseTensorPeekFunction(std::vector<Child> children,
                                                 const std::vector<std::pair<int64_t,size_t>> &spec)
    : TensorFunction(),
      _children(std::move(children)),
      _spec(spec)
{
}

DenseTensorPeekFunction::~DenseTensorPeekFunction() = default;

void
DenseTensorPeekFunction::push_children(std::vector<Child::CREF> &target) const
{
    for (const Child &c: _children) {
        target.push_back(c);
    }
}

eval::InterpretedFunction::Instruction
DenseTensorPeekFunction::compile_self(Stash &) const
{
    static_assert(sizeof(uint64_t) == sizeof(&_spec));
    auto op = select_1<MyTensorPeekOp>(_children[0].get().result_type().cell_type());
    return eval::InterpretedFunction::Instruction(op, (uint64_t)&_spec);
}

const TensorFunction &
DenseTensorPeekFunction::optimize(const eval::TensorFunction &expr, Stash &stash)
{
    if (auto peek = as<Peek>(expr)) {
        const ValueType &peek_type = peek->param_type();
        if (expr.result_type().is_double() && peek_type.is_dense()) {
            std::vector<std::pair<int64_t,size_t>> spec;
            assert(peek_type.dimensions().size() == peek->spec().size());
            for (auto dim = peek_type.dimensions().rbegin(); dim != peek_type.dimensions().rend(); ++dim) {
                auto dim_spec = peek->spec().find(dim->name);
                assert(dim_spec != peek->spec().end());
                if (std::holds_alternative<TensorSpec::Label>(dim_spec->second)) {
                    const auto &label = std::get<TensorSpec::Label>(dim_spec->second);
                    assert(label.is_indexed());
                    spec.emplace_back(label.index, dim->size);
                } else {
                    assert(std::holds_alternative<TensorFunction::Child>(dim_spec->second));
                    spec.emplace_back(-1, dim->size);
                }
            }
            return stash.create<DenseTensorPeekFunction>(peek->copy_children(), spec);
        }
    }
    return expr;
}

} // namespace vespalib::tensor
