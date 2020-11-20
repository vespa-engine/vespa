// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_simple_join_function.h"
#include "dense_tensor_view.h"
#include <vespa/vespalib/objects/objectvisitor.h>
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/operation.h>
#include <vespa/eval/eval/inline_operation.h>
#include <vespa/vespalib/util/typify.h>
#include <optional>
#include <algorithm>

namespace vespalib::tensor {

using vespalib::ArrayRef;

using eval::CellType;
using eval::Value;
using eval::ValueType;
using eval::TensorFunction;
using eval::TensorEngine;
using eval::TypifyCellType;
using eval::as;

using namespace eval::operation;
using namespace eval::tensor_function;

using Primary = DenseSimpleJoinFunction::Primary;
using Overlap = DenseSimpleJoinFunction::Overlap;

using op_function = eval::InterpretedFunction::op_function;
using Instruction = eval::InterpretedFunction::Instruction;
using State = eval::InterpretedFunction::State;

namespace {

struct TypifyOverlap {
    template <Overlap VALUE> using Result = TypifyResultValue<Overlap, VALUE>;
    template <typename F> static decltype(auto) resolve(Overlap value, F &&f) {
        switch (value) {
        case Overlap::INNER: return f(Result<Overlap::INNER>());
        case Overlap::OUTER: return f(Result<Overlap::OUTER>());
        case Overlap::FULL:  return f(Result<Overlap::FULL>());
        }
        abort();
    }
};

struct JoinParams {
    const ValueType &result_type;
    size_t factor;
    join_fun_t function;
    JoinParams(const ValueType &result_type_in, size_t factor_in, join_fun_t function_in)
        : result_type(result_type_in), factor(factor_in), function(function_in) {}
};

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
    using OP = typename std::conditional<swap,SwapArgs2<Fun>,Fun>::type;
    const JoinParams &params = unwrap_param<JoinParams>(param);
    OP my_op(params.function);
    auto pri_cells = state.peek(swap ? 0 : 1).cells().typify<PCT>();
    auto sec_cells = state.peek(swap ? 1 : 0).cells().typify<SCT>();
    auto dst_cells = make_dst_cells<OCT, pri_mut>(pri_cells, state.stash);
    if (overlap == Overlap::FULL) {
        apply_op2_vec_vec(dst_cells.begin(), pri_cells.begin(), sec_cells.begin(), dst_cells.size(), my_op);
    } else if (overlap == Overlap::OUTER) {
        size_t offset = 0;
        size_t factor = params.factor;
        for (SCT cell: sec_cells) {
            apply_op2_vec_num(dst_cells.begin() + offset, pri_cells.begin() + offset, cell, factor, my_op);
            offset += factor;
        }
    } else {
        assert(overlap == Overlap::INNER);
        size_t offset = 0;
        size_t factor = params.factor;
        for (size_t i = 0; i < factor; ++i) {
            apply_op2_vec_vec(dst_cells.begin() + offset, pri_cells.begin() + offset, sec_cells.begin(), sec_cells.size(), my_op);
            offset += sec_cells.size();
        }
    }
    state.pop_pop_push(state.stash.create<DenseTensorView>(params.result_type, TypedCells(dst_cells)));
}

//-----------------------------------------------------------------------------

struct MyGetFun {
    template <typename R1, typename R2, typename R3, typename R4, typename R5, typename R6> static auto invoke() {
        return my_simple_join_op<R1, R2, R3, R4::value, R5::value, R6::value>;
    }
};

using MyTypify = TypifyValue<TypifyCellType,TypifyOp2,TypifyBool,TypifyOverlap>;

//-----------------------------------------------------------------------------

bool can_use_as_output(const TensorFunction &fun, CellType result_cell_type) {
    return (fun.result_is_mutable() && (fun.result_type().cell_type() == result_cell_type));
}

Primary select_primary(const TensorFunction &lhs, const TensorFunction &rhs, CellType result_cell_type) {
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

std::optional<Overlap> detect_overlap(const TensorFunction &primary, const TensorFunction &secondary) {
    std::vector<ValueType::Dimension> a = primary.result_type().nontrivial_indexed_dimensions();
    std::vector<ValueType::Dimension> b = secondary.result_type().nontrivial_indexed_dimensions();
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
DenseSimpleJoinFunction::compile_self(eval::EngineOrFactory, Stash &stash) const
{
    const JoinParams &params = stash.create<JoinParams>(result_type(), factor(), function());
    auto op = typify_invoke<6,MyTypify,MyGetFun>(lhs().result_type().cell_type(),
                                                 rhs().result_type().cell_type(),
                                                 function(), (_primary == Primary::RHS),
                                                 _overlap, primary_is_mutable());
    return Instruction(op, wrap_param<JoinParams>(params));
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
