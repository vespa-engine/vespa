// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "l2_distance.h"
#include <vespa/eval/eval/operation.h>
#include <vespa/eval/eval/value.h>
#include <vespa/vespalib/hwaccelrated/iaccelrated.h>
#include <vespa/vespalib/util/require.h>

#include <vespa/log/log.h>
LOG_SETUP(".eval.instruction.l2_distance");

namespace vespalib::eval {

using namespace tensor_function;

namespace {

static const auto &hw = hwaccelrated::IAccelrated::getAccelerator();

template <typename T>
double sq_l2(const Value &lhs, const Value &rhs, size_t len) {
    return hw.squaredEuclideanDistance((const T *)lhs.cells().data, (const T *)rhs.cells().data, len);
}

template <>
double sq_l2<Int8Float>(const Value &lhs, const Value &rhs, size_t len) {
    return sq_l2<int8_t>(lhs, rhs, len);
}

template <typename CT>
void my_squared_l2_distance_op(InterpretedFunction::State &state, uint64_t vector_size) {
    double result = sq_l2<CT>(state.peek(1), state.peek(0), vector_size);
    state.pop_pop_push(state.stash.create<DoubleValue>(result));
}

struct SelectOp {
    template <typename CT>
    static InterpretedFunction::op_function invoke() {
        constexpr bool is_bfloat16 = std::is_same_v<CT, BFloat16>;
        if constexpr (!is_bfloat16) {
            return my_squared_l2_distance_op<CT>;
        } else {
            abort();
        }
    }
};

bool compatible_cell_types(CellType lhs, CellType rhs) {
    return ((lhs == rhs) && ((lhs == CellType::INT8) ||
                             (lhs == CellType::FLOAT) ||
                             (lhs == CellType::DOUBLE)));
}

bool compatible_types(const ValueType &lhs, const ValueType &rhs) {
    return (compatible_cell_types(lhs.cell_type(), rhs.cell_type()) &&
            lhs.is_dense() && rhs.is_dense() &&
            (lhs.nontrivial_indexed_dimensions() == rhs.nontrivial_indexed_dimensions()));
}

} // namespace <unnamed>

L2Distance::L2Distance(const TensorFunction &lhs_in, const TensorFunction &rhs_in)
  : tensor_function::Op2(ValueType::double_type(), lhs_in, rhs_in)
{
}

InterpretedFunction::Instruction
L2Distance::compile_self(const ValueBuilderFactory &, Stash &) const
{
    auto lhs_t = lhs().result_type();
    auto rhs_t = rhs().result_type();
    REQUIRE_EQ(lhs_t.cell_type(), rhs_t.cell_type());
    REQUIRE_EQ(lhs_t.dense_subspace_size(), rhs_t.dense_subspace_size());
    auto op = typify_invoke<1, TypifyCellType, SelectOp>(lhs_t.cell_type());
    return InterpretedFunction::Instruction(op, lhs_t.dense_subspace_size());
}

const TensorFunction &
L2Distance::optimize(const TensorFunction &expr, Stash &stash)
{
    auto reduce = as<Reduce>(expr);
    if (reduce && (reduce->aggr() == Aggr::SUM) && expr.result_type().is_double()) {
        auto map = as<Map>(reduce->child());
        if (map && (map->function() == operation::Square::f)) {
            auto join = as<Join>(map->child());
            if (join && (join->function() == operation::Sub::f)) {
                if (compatible_types(join->lhs().result_type(), join->rhs().result_type())) {
                    return stash.create<L2Distance>(join->lhs(), join->rhs());
                }
            }
        }
    }
    return expr;
}

} // namespace
