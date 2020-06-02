// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_number_join_function.h"
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

using Primary = DenseNumberJoinFunction::Primary;

using op_function = eval::InterpretedFunction::op_function;
using Instruction = eval::InterpretedFunction::Instruction;
using State = eval::InterpretedFunction::State;

namespace {

struct CallFun {
    join_fun_t function;
    CallFun(join_fun_t function_in) : function(function_in) {}
    double eval(double a, double b) const { return function(a, b); }
};

struct AddFun {
    AddFun(join_fun_t) {}
    template <typename A, typename B>
    auto eval(A a, B b) const { return (a + b); }
};

struct MulFun {
    MulFun(join_fun_t) {}
    template <typename A, typename B>
    auto eval(A a, B b) const { return (a * b); }
};

// needed for asymmetric operations like Sub and Div
template <typename Fun>
struct SwapFun {
    Fun fun;
    SwapFun(join_fun_t function_in) : fun(function_in) {}
    template <typename A, typename B>
    auto eval(A a, B b) const { return fun.eval(b, a); }
};

template <typename CT, typename Fun>
void apply_fun_1_to_n(CT *dst, const CT *pri, CT sec, size_t n, const Fun &fun) {
    for (size_t i = 0; i < n; ++i) {
        dst[i] = fun.eval(pri[i], sec);
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

template <typename CT, typename Fun, bool inplace, bool swap>
void my_number_join_op(State &state, uint64_t param) {
    using OP = typename std::conditional<swap,SwapFun<Fun>,Fun>::type;
    OP my_op((join_fun_t)param);
    const Value &tensor = state.peek(swap ? 0 : 1);
    CT number = state.peek(swap ? 1 : 0).as_double();
    auto src_cells = DenseTensorView::typify_cells<CT>(tensor);
    auto dst_cells = make_dst_cells<CT, inplace>(src_cells, state.stash);
    apply_fun_1_to_n(dst_cells.begin(), src_cells.begin(), number, dst_cells.size(), my_op);
    if (inplace) {
        state.pop_pop_push(tensor);
    } else {
        state.pop_pop_push(state.stash.create<DenseTensorView>(tensor.type(), TypedCells(dst_cells)));
    }
}

//-----------------------------------------------------------------------------

template <typename Fun, bool inplace, bool swap>
struct MyNumberJoinOp {
    template <typename CT>
    static auto get_fun() { return my_number_join_op<CT,Fun,inplace,swap>; }
};

template <typename Fun, bool inplace>
op_function my_select_3(ValueType::CellType ct, Primary primary) {
    switch (primary) {
    case Primary::LHS: return select_1<MyNumberJoinOp<Fun,inplace,false>>(ct);
    case Primary::RHS: return select_1<MyNumberJoinOp<Fun,inplace,true>>(ct);
    }
    abort();
}

template <typename Fun>
op_function my_select_2(ValueType::CellType ct, Primary primary, bool inplace) {
    if (inplace) {
        return my_select_3<Fun, true>(ct, primary);
    } else {
        return my_select_3<Fun, false>(ct, primary);
    }
}

op_function my_select(ValueType::CellType ct, Primary primary, bool inplace, join_fun_t fun_hint) {
    if (fun_hint == Add::f) {
        return my_select_2<AddFun>(ct, primary, inplace);
    } else if (fun_hint == Mul::f) {
        return my_select_2<MulFun>(ct, primary, inplace);
    } else {
        return my_select_2<CallFun>(ct, primary, inplace);
    }
}

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
DenseNumberJoinFunction::compile_self(const TensorEngine &, Stash &) const
{
    auto op = my_select(result_type().cell_type(), _primary, inplace(), function());
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
