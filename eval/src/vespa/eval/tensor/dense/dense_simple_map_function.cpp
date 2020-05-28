// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_simple_map_function.h"
#include "dense_tensor_view.h"
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/operation.h>

namespace vespalib::tensor {

using vespalib::ArrayRef;

using eval::Value;
using eval::ValueType;
using eval::TensorFunction;
using eval::TensorEngine;
using eval::as;

using namespace eval::operation;
using namespace eval::tensor_function;

using op_function = eval::InterpretedFunction::op_function;
using Instruction = eval::InterpretedFunction::Instruction;
using State = eval::InterpretedFunction::State;

namespace {

struct CallFun {
    map_fun_t function;
    CallFun(map_fun_t function_in) : function(function_in) {}
    double eval(double a) const { return function(a); }
};

template <typename CT, typename Fun>
void apply_fun_to_n(CT *dst, const CT *src, size_t n, const Fun &fun) {
    for (size_t i = 0; i < n; ++i) {
        dst[i] = fun.eval(src[i]);
    }
}

template <typename CT, bool inplace>
ArrayRef<CT> make_dst_cells(ConstArrayRef<CT> src_cells, Stash &stash) {
    if (inplace) {
        return unconstify(src_cells);
    } else {
        return stash.create_array<CT>(src_cells.size());
    }
}

template <typename CT, typename Fun, bool inplace>
void my_simple_map_op(State &state, uint64_t param) {
    Fun my_fun((map_fun_t)param);
    auto const &child = state.peek(0);
    auto src_cells = DenseTensorView::typify_cells<CT>(child);
    auto dst_cells = make_dst_cells<CT, inplace>(src_cells, state.stash);
    apply_fun_to_n(dst_cells.begin(), src_cells.begin(), dst_cells.size(), my_fun);
    if (!inplace) {
        state.pop_push(state.stash.create<DenseTensorView>(child.type(), TypedCells(dst_cells)));
    }
}

//-----------------------------------------------------------------------------

template <typename Fun, bool inplace>
struct MySimpleMapOp {
    template <typename CT>
    static auto get_fun() { return my_simple_map_op<CT,Fun,inplace>; }
};

template <typename Fun>
op_function my_select_2(ValueType::CellType ct, bool inplace) {
    if (inplace) {
        return select_1<MySimpleMapOp<Fun,true>>(ct);
    } else {
        return select_1<MySimpleMapOp<Fun,false>>(ct);
    }
}

op_function my_select(ValueType::CellType ct, bool inplace, map_fun_t fun_hint) {
    (void) fun_hint; // ready for function inlining
    return my_select_2<CallFun>(ct, inplace);
}

} // namespace vespalib::tensor::<unnamed>

//-----------------------------------------------------------------------------

DenseSimpleMapFunction::DenseSimpleMapFunction(const ValueType &result_type,
                                               const TensorFunction &child,
                                               map_fun_t function_in)
    : Map(result_type, child, function_in)
{
}

DenseSimpleMapFunction::~DenseSimpleMapFunction() = default;

Instruction
DenseSimpleMapFunction::compile_self(const TensorEngine &, Stash &) const
{
    auto op = my_select(result_type().cell_type(), inplace(), function());
    static_assert(sizeof(uint64_t) == sizeof(function()));
    return Instruction(op, (uint64_t)(function()));
}

const TensorFunction &
DenseSimpleMapFunction::optimize(const TensorFunction &expr, Stash &stash)
{
    if (auto map = as<Map>(expr)) {
        if (map->child().result_type().is_dense()) {
            return stash.create<DenseSimpleMapFunction>(map->result_type(), map->child(), map->function());
        }
    }
    return expr;
}

} // namespace vespalib::tensor
