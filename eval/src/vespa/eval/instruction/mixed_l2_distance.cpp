// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "mixed_l2_distance.h"
#include <vespa/eval/eval/operation.h>
#include <vespa/eval/eval/value.h>
#include <vespa/vespalib/hwaccelrated/iaccelrated.h>
#include <vespa/vespalib/util/require.h>

#include <vespa/log/log.h>
LOG_SETUP(".eval.instruction.mixed_l2_distance");

namespace vespalib::eval {

using namespace tensor_function;

namespace {

static const auto &hw = hwaccelrated::IAccelrated::getAccelerator();

template <typename T>
double h_sq_l2(const T *a, const T *b, size_t len) {
    return hw.squaredEuclideanDistance(a, b, len);
}

template <>
double h_sq_l2<Int8Float>(const Int8Float *a, const Int8Float *b, size_t len) {
    return hw.squaredEuclideanDistance((const int8_t *)a, (const int8_t *)b, len);
}

template <>
double h_sq_l2<BFloat16>(const BFloat16 *a, const BFloat16 *b, size_t len) {
    float sum = 0.0;
    for (size_t i = 0; i < len; ++i) {
        float x = a[i];
        float y = b[i];
        float d = (x - y);
        sum += d * d;
    }
    return sum;
}

struct MixedSqL2Param {
    const ValueType res_type;
    const size_t vec_len;
    MixedSqL2Param(const ValueType &r, size_t vl) : res_type(r), vec_len(vl) {}
};

template <typename ICT, typename OCT>
void mixed_squared_l2_distance_op(InterpretedFunction::State &state, uint64_t param_in) {
    const auto &param = unwrap_param<MixedSqL2Param>(param_in);
    const Value &vec = state.peek(0);
    const Value &mix = state.peek(1);
    size_t output_size = mix.index().size();
    auto output_cells = state.stash.create_uninitialized_array<OCT>(output_size);
    auto vec_cells = (const ICT *) vec.cells().data;
    auto mix_cells = (const ICT *) mix.cells().data;
    for (size_t i = 0; i < output_size; ++i) {
        output_cells[i] = h_sq_l2<ICT>(vec_cells, mix_cells, param.vec_len);
        mix_cells += param.vec_len;
    }
    Value &result_ref = state.stash.create<ValueView>(param.res_type, mix.index(), TypedCells(output_cells));
    state.pop_pop_push(result_ref);
}

struct MultiSelectOp {
    template <typename ICM>
    static InterpretedFunction::op_function invoke() {
        using ICT = CellValueType<ICM::value.cell_type>;
        constexpr CellMeta ocm = ICM::value.decay();
        using OCT = CellValueType<ocm.cell_type>;
        return mixed_squared_l2_distance_op<ICT, OCT>;
    }
};

bool mixed_compatible_types(const ValueType &res, const ValueType &mix, const ValueType &vec) {
    return ((mix.cell_type() == vec.cell_type()) &&
           vec.is_dense() &&
           res.nontrivial_indexed_dimensions().empty() &&
           (res.mapped_dimensions().size() > 0) &&
            (mix.nontrivial_indexed_dimensions() == vec.nontrivial_indexed_dimensions()) &&
           (mix.mapped_dimensions() == res.mapped_dimensions()));
}


} // namespace <unnamed>

MixedL2Distance::MixedL2Distance(const ValueType &result_type, const TensorFunction &mix_in, const TensorFunction &vec_in)
    : tensor_function::Op2(result_type, mix_in, vec_in)
{
}

InterpretedFunction::Instruction
MixedL2Distance::compile_self(const ValueBuilderFactory &, Stash &stash) const
{
    auto mix_t = lhs().result_type();
    auto vec_t = rhs().result_type();
    REQUIRE_EQ(mix_t.cell_type(), vec_t.cell_type());
    REQUIRE_EQ(mix_t.dense_subspace_size(), vec_t.dense_subspace_size());
    const auto &param = stash.create<MixedSqL2Param>(result_type(), mix_t.dense_subspace_size());
    auto mix_cm = mix_t.cell_meta().not_scalar();
    auto res_cm = mix_t.cell_meta().decay();
    REQUIRE_EQ(res_cm.cell_type, result_type().cell_type());
    auto op = typify_invoke<1, TypifyCellMeta, MultiSelectOp>(mix_cm);
    return InterpretedFunction::Instruction(op, wrap_param<MixedSqL2Param>(param));
}

const TensorFunction &
MixedL2Distance::optimize(const TensorFunction &expr, Stash &stash)
{
    auto reduce = as<Reduce>(expr);
    if (reduce && (reduce->aggr() == Aggr::SUM)) {
        auto map = as<Map>(reduce->child());
        if (map && (map->function() == operation::Square::f)) {
            auto join = as<Join>(map->child());
            if (join && (join->function() == operation::Sub::f)) {
               const auto & res_type = expr.result_type();
               const auto & left_type = join->lhs().result_type();
               const auto & right_type = join->rhs().result_type();
               if (mixed_compatible_types(res_type, left_type, right_type)) {
                    return stash.create<MixedL2Distance>(res_type, join->lhs(), join->rhs());
               }
               if (mixed_compatible_types(res_type, right_type, left_type)) {
                    return stash.create<MixedL2Distance>(res_type, join->rhs(), join->lhs());
               }
            }
        }
    }
    return expr;
}

} // namespace
