// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_number_join_function.h"
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

using Primary = DenseNumberJoinFunction::Primary;

using op_function = eval::InterpretedFunction::op_function;
using Instruction = eval::InterpretedFunction::Instruction;
using State = eval::InterpretedFunction::State;

namespace {

template <typename CT, bool inplace>
ArrayRef<CT> make_dst_cells(ConstArrayRef<CT> src_cells, Stash &stash) {
    if (inplace) {
        return unconstify(src_cells);
    } else {
        return stash.create_array<CT>(src_cells.size());
    }
}

template <typename CT, typename Fun, bool inplace, bool swap>
void my_number_join_op(State &state, uint64_t param) {
    using OP = typename std::conditional<swap,SwapArgs2<Fun>,Fun>::type;
    OP my_op((join_fun_t)param);
    const Value &tensor = state.peek(swap ? 0 : 1);
    CT number = state.peek(swap ? 1 : 0).as_double();
    auto src_cells = tensor.cells().typify<CT>();
    auto dst_cells = make_dst_cells<CT, inplace>(src_cells, state.stash);
    apply_op2_vec_num(dst_cells.begin(), src_cells.begin(), number, dst_cells.size(), my_op);
    if (inplace) {
        state.pop_pop_push(tensor);
    } else {
        state.pop_pop_push(state.stash.create<DenseTensorView>(tensor.type(), TypedCells(dst_cells)));
    }
}

//-----------------------------------------------------------------------------

struct MyGetFun {
    template <typename R1, typename R2, typename R3, typename R4> static auto invoke() {
        return my_number_join_op<R1, R2, R3::value, R4::value>;
    }
};

using MyTypify = TypifyValue<TypifyCellType,TypifyOp2,TypifyBool>;

bool is_dense(const TensorFunction &tf) { return tf.result_type().is_dense(); }
bool is_double(const TensorFunction &tf) { return tf.result_type().is_double(); }
ValueType::CellType cell_type(const TensorFunction &tf) { return tf.result_type().cell_type(); }

} // namespace vespalib::tensor::<unnamed>

//-----------------------------------------------------------------------------

DenseNumberJoinFunction::DenseNumberJoinFunction(const ValueType &result_type,
                                                 const TensorFunction &lhs,
                                                 const TensorFunction &rhs,
                                                 join_fun_t function_in,
                                                 Primary primary_in)
    : Join(result_type, lhs, rhs, function_in),
      _primary(primary_in)
{
}

DenseNumberJoinFunction::~DenseNumberJoinFunction() = default;

bool
DenseNumberJoinFunction::inplace() const
{
    if (_primary == Primary::LHS) {
        return lhs().result_is_mutable();
    } else {
        return rhs().result_is_mutable();
    }
}

Instruction
DenseNumberJoinFunction::compile_self(eval::EngineOrFactory, Stash &) const
{
    auto op = typify_invoke<4,MyTypify,MyGetFun>(result_type().cell_type(), function(),
                                                 inplace(), (_primary == Primary::RHS));
    static_assert(sizeof(uint64_t) == sizeof(function()));
    return Instruction(op, (uint64_t)(function()));
}

const TensorFunction &
DenseNumberJoinFunction::optimize(const TensorFunction &expr, Stash &stash)
{
    if (auto join = as<Join>(expr)) {
        const TensorFunction &lhs = join->lhs();
        const TensorFunction &rhs = join->rhs();
        if (is_dense(lhs) && is_double(rhs)) {
            assert(cell_type(expr) == cell_type(lhs));
            return stash.create<DenseNumberJoinFunction>(join->result_type(), lhs, rhs, join->function(), Primary::LHS);
        } else if (is_double(lhs) && is_dense(rhs)) {
            assert(cell_type(expr) == cell_type(rhs));
            return stash.create<DenseNumberJoinFunction>(join->result_type(), lhs, rhs, join->function(), Primary::RHS);
        }
    }
    return expr;
}

} // namespace vespalib::tensor
