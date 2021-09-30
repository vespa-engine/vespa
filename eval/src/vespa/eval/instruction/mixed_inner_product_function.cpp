// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "mixed_inner_product_function.h"
#include <vespa/eval/eval/operation.h>
#include <vespa/eval/eval/value.h>
#include <cblas.h>

namespace vespalib::eval {

using namespace tensor_function;
using namespace operation;

namespace {

template <typename LCT, typename RCT>
struct MyDotProduct {
    static double apply(const LCT * lhs, const RCT * rhs, size_t count) {
        double result = 0.0;
        for (size_t i = 0; i < count; ++i) {
            result += lhs[i] * rhs[i];
        }
        return result;
    }
};

template <>
struct MyDotProduct<double,double> {
    static double apply(const double * lhs, const double * rhs, size_t count) {
        return cblas_ddot(count, lhs, 1, rhs, 1);
    }
};

template <>
struct MyDotProduct<float,float> {
    static float apply(const float * lhs, const float * rhs, size_t count) {
        return cblas_sdot(count, lhs, 1, rhs, 1);
    }
};

struct MixedInnerProductParam {
    ValueType res_type;
    size_t vector_size;
    size_t out_subspace_size;

    MixedInnerProductParam(const ValueType &res_type_in,
                           const ValueType &mix_type,
                           const ValueType &vec_type)
      : res_type(res_type_in),
        vector_size(vec_type.dense_subspace_size()),
        out_subspace_size(res_type.dense_subspace_size())
    {
        assert(vector_size * out_subspace_size == mix_type.dense_subspace_size());
    }
};

template <typename MCT, typename VCT, typename OCT>
void my_mixed_inner_product_op(InterpretedFunction::State &state, uint64_t param_in) {
    const auto &param = unwrap_param<MixedInnerProductParam>(param_in);
    const auto &mixed = state.peek(1);
    const auto &vector = state.peek(0);
    auto m_cells = mixed.cells().typify<MCT>();
    auto v_cells = vector.cells().typify<VCT>();
    const auto &index = mixed.index();
    size_t num_subspaces = index.size();
    size_t num_output_cells = num_subspaces * param.out_subspace_size;
    ArrayRef<OCT> out_cells = state.stash.create_uninitialized_array<OCT>(num_output_cells);
    const MCT *m_cp = m_cells.begin();
    const VCT *v_cp = v_cells.begin();
    for (OCT &out : out_cells) {
        out = MyDotProduct<MCT,VCT>::apply(m_cp, v_cp, param.vector_size);
        m_cp += param.vector_size;
    }
    assert(m_cp == m_cells.end());
    state.pop_pop_push(state.stash.create<ValueView>(param.res_type, index, TypedCells(out_cells)));
}
        

struct SelectMixedInnerProduct {
    template <typename MCT, typename VCT, typename OCT>
    static auto invoke() { return my_mixed_inner_product_op<MCT,VCT,OCT>; }
};

} // namespace <unnamed>

MixedInnerProductFunction::MixedInnerProductFunction(const ValueType &res_type_in,
                                                     const TensorFunction &mixed_child,
                                                     const TensorFunction &vector_child)
  : tensor_function::Op2(res_type_in, mixed_child, vector_child)
{
}

InterpretedFunction::Instruction
MixedInnerProductFunction::compile_self(const ValueBuilderFactory &, Stash &stash) const
{
    const auto &mix_type = lhs().result_type();
    const auto &vec_type = rhs().result_type();
    auto &param = stash.create<MixedInnerProductParam>(result_type(), mix_type, vec_type);
    using MyTypify = TypifyValue<TypifyCellType>;
    auto op = typify_invoke<3,MyTypify,SelectMixedInnerProduct>(mix_type.cell_type(),
                                                                vec_type.cell_type(),
                                                                result_type().cell_type());
    return InterpretedFunction::Instruction(op, wrap_param<MixedInnerProductParam>(param));
}

bool
MixedInnerProductFunction::compatible_types(const ValueType &res, const ValueType &mixed, const ValueType &vector)
{
    if (vector.is_dense() && ! res.is_double()) {
        auto dense_dims = vector.nontrivial_indexed_dimensions();
        auto mixed_dims = mixed.nontrivial_indexed_dimensions();
        while (! dense_dims.empty()) {
            if (mixed_dims.empty()) {
                return false;
            }
            const auto &name = dense_dims.back().name;
            if (res.dimension_index(name) != ValueType::Dimension::npos) {
                return false;
            }
            if (name != mixed_dims.back().name) {
                return false;
            }
            dense_dims.pop_back();
            mixed_dims.pop_back();
        }
        while (! mixed_dims.empty()) {
            const auto &name = mixed_dims.back().name;
            if (res.dimension_index(name) == ValueType::Dimension::npos) {
                return false;
            }
            mixed_dims.pop_back();
        }
        return (res.mapped_dimensions() == mixed.mapped_dimensions());
    }
    return false;
}

const TensorFunction &
MixedInnerProductFunction::optimize(const TensorFunction &expr, Stash &stash)
{
    const auto & res_type = expr.result_type();
    auto reduce = as<Reduce>(expr);
    if ((! res_type.is_double()) && reduce && (reduce->aggr() == Aggr::SUM)) {
        auto join = as<Join>(reduce->child());
        if (join && (join->function() == Mul::f)) {
            const TensorFunction &lhs = join->lhs();
            const TensorFunction &rhs = join->rhs();
            if (compatible_types(res_type, lhs.result_type(), rhs.result_type())) {
                return stash.create<MixedInnerProductFunction>(res_type, lhs, rhs);
            }
            if (compatible_types(res_type, rhs.result_type(), lhs.result_type())) {
                return stash.create<MixedInnerProductFunction>(res_type, rhs, lhs);
            }
        }
    }
    return expr;
}

} // namespace
