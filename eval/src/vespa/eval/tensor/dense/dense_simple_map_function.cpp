// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_simple_map_function.h"
#include "dense_tensor_view.h"
#include <vespa/vespalib/util/typify.h>
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/operation.h>
#include <vespa/eval/eval/inline_operation.h>

namespace vespalib::tensor {

using vespalib::ArrayRef;

using eval::Value;
using eval::ValueType;
using eval::TensorFunction;
using eval::TensorEngine;
using eval::TypifyCellType;
using eval::as;

using namespace eval::operation;
using namespace eval::tensor_function;

using op_function = eval::InterpretedFunction::op_function;
using Instruction = eval::InterpretedFunction::Instruction;
using State = eval::InterpretedFunction::State;

namespace {

template <typename CT, bool inplace>
ArrayRef<CT> make_dst_cells(ConstArrayRef<CT> src_cells, Stash &stash) {
    if (inplace) {
        return unconstify(src_cells);
    } else {
        return stash.create_uninitialized_array<CT>(src_cells.size());
    }
}

template <typename CT, typename Fun, bool inplace>
void my_simple_map_op(State &state, uint64_t param) {
    Fun my_fun((map_fun_t)param);
    auto const &child = state.peek(0);
    auto src_cells = child.cells().typify<CT>();
    auto dst_cells = make_dst_cells<CT, inplace>(src_cells, state.stash);
    apply_op1_vec(dst_cells.begin(), src_cells.begin(), dst_cells.size(), my_fun);
    if (!inplace) {
        state.pop_push(state.stash.create<DenseTensorView>(child.type(), TypedCells(dst_cells)));
    }
}

//-----------------------------------------------------------------------------

struct MyGetFun {
    template <typename R1, typename R2, typename R3> static auto invoke() {
        return my_simple_map_op<R1, R2, R3::value>;
    }
};

using MyTypify = TypifyValue<TypifyCellType,TypifyOp1,TypifyBool>;

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
DenseSimpleMapFunction::compile_self(const ValueBuilderFactory &, Stash &) const
{
    auto op = typify_invoke<3,MyTypify,MyGetFun>(result_type().cell_type(), function(), inplace());
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
