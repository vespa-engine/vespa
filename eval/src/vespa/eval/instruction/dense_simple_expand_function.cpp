// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_simple_expand_function.h"
#include <vespa/vespalib/objects/objectvisitor.h>
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/operation.h>
#include <vespa/eval/eval/inline_operation.h>
#include <vespa/vespalib/util/typify.h>
#include <optional>
#include <algorithm>

namespace vespalib::eval{

using vespalib::ArrayRef;

using namespace operation;
using namespace tensor_function;

using Inner = DenseSimpleExpandFunction::Inner;

using op_function = InterpretedFunction::op_function;
using Instruction = InterpretedFunction::Instruction;
using State = InterpretedFunction::State;

namespace {

struct ExpandParams {
    const ValueType &result_type;
    size_t result_size;
    join_fun_t function;
    ExpandParams(const ValueType &result_type_in, size_t result_size_in, join_fun_t function_in)
        : result_type(result_type_in), result_size(result_size_in), function(function_in) {}
};

template <typename LCT, typename RCT, typename DCT, typename Fun, bool rhs_inner>
void my_simple_expand_op(State &state, uint64_t param) {
    using ICT = typename std::conditional<rhs_inner,RCT,LCT>::type;
    using OCT = typename std::conditional<rhs_inner,LCT,RCT>::type;
    using OP = typename std::conditional<rhs_inner,SwapArgs2<Fun>,Fun>::type;
    const ExpandParams &params = unwrap_param<ExpandParams>(param);
    OP my_op(params.function);
    auto inner_cells = state.peek(rhs_inner ? 0 : 1).cells().typify<ICT>();
    auto outer_cells = state.peek(rhs_inner ? 1 : 0).cells().typify<OCT>();
    auto dst_cells = state.stash.create_array<DCT>(params.result_size);
    DCT *dst = dst_cells.begin();
    for (OCT outer_cell: outer_cells) {
        apply_op2_vec_num(dst, inner_cells.begin(), outer_cell, inner_cells.size(), my_op);
        dst += inner_cells.size();
    }
    state.pop_pop_push(state.stash.create<DenseValueView>(params.result_type, TypedCells(dst_cells)));
}

//-----------------------------------------------------------------------------

struct SelectDenseSimpleExpand {
    template<typename LCM, typename RCM, typename Fun, typename RhsInner>
    static auto invoke() {
        constexpr CellMeta ocm = CellMeta::join(LCM::value, RCM::value);
        using LCT = CellValueType<LCM::value.cell_type>;
        using RCT = CellValueType<RCM::value.cell_type>;
        using OCT = CellValueType<ocm.cell_type>;
        return my_simple_expand_op<LCT, RCT, OCT, Fun, RhsInner::value>;
    }
};

using MyTypify = TypifyValue<TypifyCellMeta,TypifyOp2,TypifyBool>;

//-----------------------------------------------------------------------------

std::optional<Inner> detect_simple_expand(const TensorFunction &lhs, const TensorFunction &rhs) {
    std::vector<ValueType::Dimension> a = lhs.result_type().nontrivial_indexed_dimensions();
    std::vector<ValueType::Dimension> b = rhs.result_type().nontrivial_indexed_dimensions();
    if (a.empty() || b.empty()) {
        return std::nullopt;
    } else if (a.back().name < b.front().name) {
        return Inner::RHS;
    } else if (b.back().name < a.front().name) {
        return Inner::LHS;
    } else {
        return std::nullopt;
    }
}

} // namespace <unnamed>

//-----------------------------------------------------------------------------

DenseSimpleExpandFunction::DenseSimpleExpandFunction(const ValueType &result_type,
                                                     const TensorFunction &lhs,
                                                     const TensorFunction &rhs,
                                                     join_fun_t function_in,
                                                     Inner inner_in)
    : Join(result_type, lhs, rhs, function_in),
      _inner(inner_in)
{
}

DenseSimpleExpandFunction::~DenseSimpleExpandFunction() = default;

Instruction
DenseSimpleExpandFunction::compile_self(const ValueBuilderFactory &, Stash &stash) const
{
    size_t result_size = result_type().dense_subspace_size();
    const ExpandParams &params = stash.create<ExpandParams>(result_type(), result_size, function());
    auto op = typify_invoke<4,MyTypify,SelectDenseSimpleExpand>(lhs().result_type().cell_meta().not_scalar(),
                                                                rhs().result_type().cell_meta().not_scalar(),
                                                                function(), (_inner == Inner::RHS));
    return Instruction(op, wrap_param<ExpandParams>(params));
}

const TensorFunction &
DenseSimpleExpandFunction::optimize(const TensorFunction &expr, Stash &stash)
{
    if (auto join = as<Join>(expr)) {
        const TensorFunction &lhs = join->lhs();
        const TensorFunction &rhs = join->rhs();
        if (lhs.result_type().is_dense() && rhs.result_type().is_dense()) {
            if (std::optional<Inner> inner = detect_simple_expand(lhs, rhs)) {
                assert(expr.result_type().dense_subspace_size() ==
                       (lhs.result_type().dense_subspace_size() *
                        rhs.result_type().dense_subspace_size()));
                return stash.create<DenseSimpleExpandFunction>(join->result_type(), lhs, rhs, join->function(), inner.value());
            }
        }
    }
    return expr;
}

} // namespace
