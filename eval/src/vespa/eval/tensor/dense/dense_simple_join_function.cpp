// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_simple_join_function.h"
#include "dense_tensor_view.h"
#include <vespa/vespalib/objects/objectvisitor.h>
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/operation.h>
#include <optional>
#include <algorithm>

namespace vespalib::tensor {

using vespalib::ArrayRef;

using eval::Value;
using eval::ValueType;
using eval::TensorFunction;
using eval::TensorEngine;
using eval::as;

using namespace eval::operation;
using namespace eval::tensor_function;

using Primary = DenseSimpleJoinFunction::Primary;
using Overlap = DenseSimpleJoinFunction::Overlap;

using op_function = eval::InterpretedFunction::op_function;
using Instruction = eval::InterpretedFunction::Instruction;
using State = eval::InterpretedFunction::State;

namespace {

struct JoinParams {
    const ValueType &result_type;
    size_t factor;
    join_fun_t function;
    JoinParams(const ValueType &result_type_in, size_t factor_in, join_fun_t function_in)
        : result_type(result_type_in), factor(factor_in), function(function_in) {}
};

struct CallFun {
    join_fun_t function;
    CallFun(const JoinParams &params) : function(params.function) {}
    double eval(double a, double b) const { return function(a, b); }
};

struct AddFun {
    AddFun(const JoinParams &) {}
    template <typename A, typename B>
    auto eval(A a, B b) const { return (a + b); }
};

struct MulFun {
    MulFun(const JoinParams &) {}
    template <typename A, typename B>
    auto eval(A a, B b) const { return (a * b); }
};

// needed for asymmetric operations like Sub and Div
template <typename Fun>
struct SwapFun {
    Fun fun;
    SwapFun(const JoinParams &params) : fun(params) {}
    template <typename A, typename B>
    auto eval(A a, B b) const { return fun.eval(b, a); }
};

template <typename OCT, typename PCT, typename SCT, typename Fun>
void apply_fun_1_to_n(OCT *dst, const PCT *pri, SCT sec, size_t n, const Fun &fun) {
    for (size_t i = 0; i < n; ++i) {
        dst[i] = fun.eval(pri[i], sec);
    }
}

template <typename OCT, typename PCT, typename SCT, typename Fun>
void apply_fun_n_to_n(OCT *dst, const PCT *pri, const SCT *sec, size_t n, const Fun &fun) {
    for (size_t i = 0; i < n; ++i) {
        dst[i] = fun.eval(pri[i], sec[i]);
    }
}

template <typename OCT, bool pri_mut, typename PCT>
ArrayRef<OCT> make_dst_cells(ConstArrayRef<PCT> pri_cells, Stash &stash) {
    if constexpr (pri_mut && std::is_same<PCT,OCT>::value) {
        return unconstify(pri_cells);
    } else {
        return stash.create_array<OCT>(pri_cells.size());
    }
}

template <typename LCT, typename RCT, typename Fun, bool swap, Overlap overlap, bool pri_mut>
void my_simple_join_op(State &state, uint64_t param) {
    using PCT = typename std::conditional<swap,RCT,LCT>::type;
    using SCT = typename std::conditional<swap,LCT,RCT>::type;
    using OCT = typename eval::UnifyCellTypes<PCT,SCT>::type;
    using OP = typename std::conditional<swap,SwapFun<Fun>,Fun>::type;
    const JoinParams &params = *(JoinParams*)param;
    OP my_op(params);
    auto pri_cells = DenseTensorView::typify_cells<PCT>(state.peek(swap ? 0 : 1));
    auto sec_cells = DenseTensorView::typify_cells<SCT>(state.peek(swap ? 1 : 0));
    auto dst_cells = make_dst_cells<OCT, pri_mut>(pri_cells, state.stash);
    if (overlap == Overlap::FULL) {
        apply_fun_n_to_n(dst_cells.begin(), pri_cells.begin(), sec_cells.begin(), dst_cells.size(), my_op);
    } else if (overlap == Overlap::OUTER) {
        size_t offset = 0;
        size_t factor = params.factor;
        for (SCT cell: sec_cells) {
            apply_fun_1_to_n(dst_cells.begin() + offset, pri_cells.begin() + offset, cell, factor, my_op);
            offset += factor;
        }
    } else {
        assert(overlap == Overlap::INNER);
        size_t offset = 0;
        size_t factor = params.factor;
        for (size_t i = 0; i < factor; ++i) {
            apply_fun_n_to_n(dst_cells.begin() + offset, pri_cells.begin() + offset, sec_cells.begin(), sec_cells.size(), my_op);
            offset += sec_cells.size();
        }
    }
    state.pop_pop_push(state.stash.create<DenseTensorView>(params.result_type, TypedCells(dst_cells)));
}

//-----------------------------------------------------------------------------

template <typename Fun, bool swap, Overlap overlap, bool pri_mut>
struct MySimpleJoinOp {
    template <typename LCT, typename RCT>
    static auto get_fun() { return my_simple_join_op<LCT,RCT,Fun,swap,overlap,pri_mut>; }
};

template <bool swap, Overlap overlap, bool pri_mut>
op_function my_select_4(ValueType::CellType lct,
                        ValueType::CellType rct,
                        join_fun_t fun_hint)
{
    if (fun_hint == Add::f) {
        return select_2<MySimpleJoinOp<AddFun,swap,overlap,pri_mut>>(lct, rct);
    } else if (fun_hint == Mul::f) {
        return select_2<MySimpleJoinOp<MulFun,swap,overlap,pri_mut>>(lct, rct);
    } else {
        return select_2<MySimpleJoinOp<CallFun,swap,overlap,pri_mut>>(lct, rct);
    }
}

template <bool swap, Overlap overlap>
op_function my_select_3(ValueType::CellType lct,
                        ValueType::CellType rct,
                        bool pri_mut,
                        join_fun_t fun_hint)
{
    if (pri_mut) {
        return my_select_4<swap, overlap, true>(lct, rct, fun_hint);
    } else {
        return my_select_4<swap, overlap, false>(lct, rct, fun_hint);
    }
}

template <bool swap>
op_function my_select_2(ValueType::CellType lct,
                        ValueType::CellType rct,
                        Overlap overlap,
                        bool pri_mut,
                        join_fun_t fun_hint)
{
    switch (overlap) {
    case Overlap::INNER: return my_select_3<swap, Overlap::INNER>(lct, rct, pri_mut, fun_hint);
    case Overlap::OUTER: return my_select_3<swap, Overlap::OUTER>(lct, rct, pri_mut, fun_hint);
    case Overlap::FULL: return my_select_3<swap, Overlap::FULL>(lct, rct, pri_mut, fun_hint);
    }
    abort();
}

op_function my_select(ValueType::CellType lct,
                      ValueType::CellType rct,
                      Primary primary,
                      Overlap overlap,
                      bool pri_mut,
                      join_fun_t fun_hint)
{
    switch (primary) {
    case Primary::LHS: return my_select_2<false>(lct, rct, overlap, pri_mut, fun_hint);
    case Primary::RHS: return my_select_2<true>(lct, rct, overlap, pri_mut, fun_hint);
    }
    abort();
}

//-----------------------------------------------------------------------------

bool can_use_as_output(const TensorFunction &fun, ValueType::CellType result_cell_type) {
    return (fun.result_is_mutable() && (fun.result_type().cell_type() == result_cell_type));
}

Primary select_primary(const TensorFunction &lhs, const TensorFunction &rhs, ValueType::CellType result_cell_type) {
    size_t lhs_size = lhs.result_type().dense_subspace_size();
    size_t rhs_size = rhs.result_type().dense_subspace_size();
    if (lhs_size > rhs_size) {
        return Primary::LHS;
    } else if (rhs_size > lhs_size) {
        return Primary::RHS;
    } else {
        bool can_write_lhs = can_use_as_output(lhs, result_cell_type);
        bool can_write_rhs = can_use_as_output(rhs, result_cell_type);
        if (can_write_lhs && !can_write_rhs) {
            return Primary::LHS;
        } else {
            // prefer using rhs as output due to write recency
            return Primary::RHS;
        }
    }
}

std::vector<ValueType::Dimension> strip_trivial(const std::vector<ValueType::Dimension> &dim_list) {
    std::vector<ValueType::Dimension> result;
    std::copy_if(dim_list.begin(), dim_list.end(), std::back_inserter(result),
                 [](const auto &dim){ return (dim.size != 1); });
    return result;
}

std::optional<Overlap> detect_overlap(const TensorFunction &primary, const TensorFunction &secondary) {
    std::vector<ValueType::Dimension> a = strip_trivial(primary.result_type().dimensions());
    std::vector<ValueType::Dimension> b = strip_trivial(secondary.result_type().dimensions());
    if (b.size() > a.size()) {
        return std::nullopt;
    } else if (b == a) {
        return Overlap::FULL;
    } else if (std::equal(b.begin(), b.end(), a.begin())) {
        // prefer OUTER to INNER (for empty b) due to loop nesting
        return Overlap::OUTER;
    } else if (std::equal(b.rbegin(), b.rend(), a.rbegin())) {
        return Overlap::INNER;
    } else {
        return std::nullopt;
    }
}

std::optional<Overlap> detect_overlap(const TensorFunction &lhs, const TensorFunction &rhs, Primary primary) {
    return (primary == Primary::LHS) ? detect_overlap(lhs, rhs) : detect_overlap(rhs, lhs);
}

} // namespace vespalib::tensor::<unnamed>

//-----------------------------------------------------------------------------

DenseSimpleJoinFunction::DenseSimpleJoinFunction(const ValueType &result_type,
                                                 const TensorFunction &lhs,
                                                 const TensorFunction &rhs,
                                                 join_fun_t function_in,
                                                 Primary primary_in,
                                                 Overlap overlap_in)
    : Join(result_type, lhs, rhs, function_in),
      _primary(primary_in),
      _overlap(overlap_in)
{
}

DenseSimpleJoinFunction::~DenseSimpleJoinFunction() = default;

bool
DenseSimpleJoinFunction::primary_is_mutable() const
{
    if (_primary == Primary::LHS) {
        return lhs().result_is_mutable();
    } else {
        return rhs().result_is_mutable();
    }
}

size_t
DenseSimpleJoinFunction::factor() const
{
    const TensorFunction &p = (_primary == Primary::LHS) ? lhs() : rhs();
    const TensorFunction &s = (_primary == Primary::LHS) ? rhs() : lhs();
    size_t a = p.result_type().dense_subspace_size();
    size_t b = s.result_type().dense_subspace_size();
    assert((a % b) == 0);
    return (a / b);
}

Instruction
DenseSimpleJoinFunction::compile_self(const TensorEngine &, Stash &stash) const
{
    const JoinParams &params = stash.create<JoinParams>(result_type(), factor(), function());
    auto op = my_select(lhs().result_type().cell_type(),
                        rhs().result_type().cell_type(),
                        _primary, _overlap,
                        primary_is_mutable(),
                        function());
    static_assert(sizeof(uint64_t) == sizeof(&params));
    return Instruction(op, (uint64_t)(&params));
}

const TensorFunction &
DenseSimpleJoinFunction::optimize(const TensorFunction &expr, Stash &stash)
{
    if (auto join = as<Join>(expr)) {
        const TensorFunction &lhs = join->lhs();
        const TensorFunction &rhs = join->rhs();
        if (lhs.result_type().is_dense() && rhs.result_type().is_dense()) {
            Primary primary = select_primary(lhs, rhs, join->result_type().cell_type());
            std::optional<Overlap> overlap = detect_overlap(lhs, rhs, primary);
            if (overlap.has_value()) {
                const TensorFunction &ptf = (primary == Primary::LHS) ? lhs : rhs;
                assert(ptf.result_type().dense_subspace_size() == join->result_type().dense_subspace_size());
                return stash.create<DenseSimpleJoinFunction>(join->result_type(), lhs, rhs, join->function(),
                        primary, overlap.value());
            }
        }
    }
    return expr;
}

} // namespace vespalib::tensor
