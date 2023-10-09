// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_hamming_distance.h"
#include <vespa/eval/eval/operation.h>
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/hamming_distance.h>
#include <vespa/vespalib/util/binary_hamming_distance.h>

#include <vespa/log/log.h>
LOG_SETUP(".eval.instruction.dense_hamming_distance");

namespace vespalib::eval {

using namespace tensor_function;

namespace {

void int8_hamming_to_double_op(InterpretedFunction::State &state, uint64_t vector_size) {
    const auto &lhs = state.peek(1);
    const auto &rhs = state.peek(0);
    auto a = lhs.cells();
    auto b = rhs.cells();
    double result = binary_hamming_distance(a.data, b.data, vector_size);
    state.pop_pop_push(state.stash.create<DoubleValue>(result));
}

bool compatible_types(const ValueType &lhs, const ValueType &rhs) {
    return ((lhs.cell_type() == CellType::INT8) &&
            (rhs.cell_type() == CellType::INT8) &&
            lhs.is_dense() &&
            rhs.is_dense() &&
            (lhs.nontrivial_indexed_dimensions() == rhs.nontrivial_indexed_dimensions()));
}

} // namespace <unnamed>

DenseHammingDistance::DenseHammingDistance(const TensorFunction &lhs_child,
                                           const TensorFunction &rhs_child)
    : tensor_function::Op2(ValueType::double_type(), lhs_child, rhs_child)
{
}

InterpretedFunction::Instruction
DenseHammingDistance::compile_self(const ValueBuilderFactory &, Stash &) const
{
    auto op = int8_hamming_to_double_op;
    const auto &lhs_type = lhs().result_type();
    const auto &rhs_type = rhs().result_type();
    LOG_ASSERT(lhs_type.dense_subspace_size() == rhs_type.dense_subspace_size());
    return InterpretedFunction::Instruction(op, lhs_type.dense_subspace_size());
}

const TensorFunction &
DenseHammingDistance::optimize(const TensorFunction &expr, Stash &stash)
{
    const auto & res_type = expr.result_type();
    auto reduce = as<Reduce>(expr);
    if (res_type.is_double() && reduce && (reduce->aggr() == Aggr::SUM)) {
        auto join = as<Join>(reduce->child());
        if (join && (join->function() == operation::Hamming::f)) {
            const TensorFunction &lhs = join->lhs();
            const TensorFunction &rhs = join->rhs();
            if (compatible_types(lhs.result_type(), rhs.result_type())) {
                return stash.create<DenseHammingDistance>(lhs, rhs);
            }
        }
    }
    return expr;
}

} // namespace
