// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "mixed_simple_join_function.h"
#include <vespa/vespalib/objects/objectvisitor.h>
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/operation.h>
#include <vespa/eval/eval/inline_operation.h>
#include <vespa/vespalib/util/typify.h>
#include <optional>
#include <algorithm>

namespace vespalib::eval {

using vespalib::ArrayRef;

using namespace operation;
using namespace tensor_function;

using Primary = MixedSimpleJoinFunction::Primary;
using Overlap = MixedSimpleJoinFunction::Overlap;

using op_function = InterpretedFunction::op_function;
using Instruction = InterpretedFunction::Instruction;
using State = InterpretedFunction::State;

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
    size_t subspace_size;
    join_fun_t function;
    JoinParams(const ValueType &result_type_in, size_t factor_in, join_fun_t function_in)
        : result_type(result_type_in), factor(factor_in), subspace_size(result_type.dense_subspace_size()), function(function_in) {}
};

template <typename OCT, bool pri_mut, typename PCT>
ArrayRef<OCT> make_dst_cells(ConstArrayRef<PCT> pri_cells, Stash &stash) {
    if constexpr (pri_mut && std::is_same<PCT,OCT>::value) {
        return unconstify(pri_cells);
    } else {
        return stash.create_uninitialized_array<OCT>(pri_cells.size());
    }
}

template <typename LCT, typename RCT, typename OCT, typename Fun, bool swap, Overlap overlap, bool pri_mut>
void my_simple_join_op(State &state, uint64_t param) {
    using PCT = typename std::conditional<swap,RCT,LCT>::type;
    using SCT = typename std::conditional<swap,LCT,RCT>::type;
    using OP = typename std::conditional<swap,SwapArgs2<Fun>,Fun>::type;
    const JoinParams &params = unwrap_param<JoinParams>(param);
    OP my_op(params.function);
    auto pri_cells = state.peek(swap ? 0 : 1).cells().typify<PCT>();
    auto sec_cells = state.peek(swap ? 1 : 0).cells().typify<SCT>();
    auto dst_cells = make_dst_cells<OCT, pri_mut>(pri_cells, state.stash);
    const auto &index = state.peek(swap ? 0 : 1).index();
    size_t subspace_size = params.subspace_size;
    size_t offset = 0;
    while (offset < pri_cells.size()) {
        if constexpr (overlap == Overlap::FULL) {
            apply_op2_vec_vec(&dst_cells[offset], &pri_cells[offset], sec_cells.begin(), subspace_size, my_op);
            offset += subspace_size;
        } else if constexpr (overlap == Overlap::OUTER) {
            size_t factor = params.factor;
            for (SCT cell: sec_cells) {
                apply_op2_vec_num(&dst_cells[offset], &pri_cells[offset], cell, factor, my_op);
                offset += factor;
            }
        } else {
            static_assert(overlap == Overlap::INNER);
            size_t factor = params.factor;
            for (size_t i = 0; i < factor; ++i) {
                apply_op2_vec_vec(&dst_cells[offset], &pri_cells[offset], sec_cells.begin(), sec_cells.size(), my_op);
                offset += sec_cells.size();
            }
        }
    }
    assert(offset == pri_cells.size());
    state.pop_pop_push(state.stash.create<ValueView>(params.result_type, index, TypedCells(dst_cells)));
}

//-----------------------------------------------------------------------------

struct SelectMixedSimpleJoin {
    template<typename LCM, typename RCM, typename R3, typename R4, typename R5, typename R6>
    static auto invoke() {
        constexpr CellMeta ocm = CellMeta::join(LCM::value, RCM::value);
        using LCT = CellValueType<LCM::value.cell_type>;
        using RCT = CellValueType<RCM::value.cell_type>;
        using OCT = CellValueType<ocm.cell_type>;
        return my_simple_join_op<LCT, RCT, OCT, R3, R4::value, R5::value, R6::value>;
    }
};

using MyTypify = TypifyValue<TypifyCellMeta,TypifyOp2,TypifyBool,TypifyOverlap>;

//-----------------------------------------------------------------------------

bool can_use_as_output(const TensorFunction &fun, CellType result_cell_type) {
    return (fun.result_is_mutable() && (fun.result_type().cell_type() == result_cell_type));
}

Primary select_primary(const TensorFunction &lhs, const TensorFunction &rhs, CellType result_cell_type) {
    if (!lhs.result_type().is_dense()) {
        return Primary::LHS;
    } else if (!rhs.result_type().is_dense()) {
        return Primary::RHS;
    }
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
    assert(secondary.result_type().is_dense());
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

} // namespace vespalib::eval::<unnamed>

//-----------------------------------------------------------------------------

MixedSimpleJoinFunction::MixedSimpleJoinFunction(const ValueType &result_type,
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

MixedSimpleJoinFunction::~MixedSimpleJoinFunction() = default;

bool
MixedSimpleJoinFunction::primary_is_mutable() const
{
    if (_primary == Primary::LHS) {
        return lhs().result_is_mutable();
    } else {
        return rhs().result_is_mutable();
    }
}

size_t
MixedSimpleJoinFunction::factor() const
{
    const TensorFunction &p = (_primary == Primary::LHS) ? lhs() : rhs();
    const TensorFunction &s = (_primary == Primary::LHS) ? rhs() : lhs();
    size_t a = p.result_type().dense_subspace_size();
    size_t b = s.result_type().dense_subspace_size();
    assert((a % b) == 0);
    return (a / b);
}

Instruction
MixedSimpleJoinFunction::compile_self(const ValueBuilderFactory &, Stash &stash) const
{
    const JoinParams &params = stash.create<JoinParams>(result_type(), factor(), function());
    auto op = typify_invoke<6,MyTypify,SelectMixedSimpleJoin>(lhs().result_type().cell_meta().not_scalar(),
                                                              rhs().result_type().cell_meta().not_scalar(),
                                                              function(), (_primary == Primary::RHS),
                                                              _overlap, primary_is_mutable());
    return Instruction(op, wrap_param<JoinParams>(params));
}

const TensorFunction &
MixedSimpleJoinFunction::optimize(const TensorFunction &expr, Stash &stash)
{
    if (auto join = as<Join>(expr)) {
        const TensorFunction &lhs = join->lhs();
        const TensorFunction &rhs = join->rhs();
        if (lhs.result_type().is_dense() || rhs.result_type().is_dense()) {
            Primary primary = select_primary(lhs, rhs, join->result_type().cell_type());
            std::optional<Overlap> overlap = detect_overlap(lhs, rhs, primary);
            if (overlap.has_value()) {
                const TensorFunction &ptf = (primary == Primary::LHS) ? lhs : rhs;
                assert(ptf.result_type().dense_subspace_size() == join->result_type().dense_subspace_size());
                return stash.create<MixedSimpleJoinFunction>(join->result_type(), lhs, rhs, join->function(),
                        primary, overlap.value());
            }
        }
    }
    return expr;
}

} // namespace vespalib::eval
