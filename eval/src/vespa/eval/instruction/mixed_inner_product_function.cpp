// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "mixed_inner_product_function.h"
#include <vespa/eval/eval/array_array_map.h>
#include <vespa/eval/eval/operation.h>
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/value_builder_factory.h>
#include <vespa/vespalib/util/overload.h>
#include <vespa/vespalib/util/small_vector.h>
#include <vespa/vespalib/util/visit_ranges.h>
#include <cblas.h>

#include <vespa/log/log.h>
LOG_SETUP(".eval.instruction.dotproduct_inside_mixed");

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


// --- DotproductInsideMixed --->

struct SelectMixedInnerProduct {
    template <typename MCT, typename VCT, typename OCT>
    static auto invoke() { return my_mixed_inner_product_op<MCT,VCT,OCT>; }
};

enum class SparseDimSource : char { LEFT, RIGHT, BOTH };

struct SparseDimMeta {
    const SparseDimSource src;
    const bool keep;
};

struct DotproductInsideMixedParam {
    const ValueType res_type;
    const size_t res_mapped_dims;
    const size_t left_mapped_dims;
    const size_t right_mapped_dims;
    const size_t dense_subspace_size;
    SmallVector<SparseDimMeta> sparse_dims_meta;
    SmallVector<size_t> right_view_dims;
    const ValueBuilderFactory &factory;
    DotproductInsideMixedParam(const ValueType &res_type_in,
                               const ValueType &left_type,
                               const ValueType &right_type,
                               const ValueBuilderFactory &factory_in)
        : res_type(res_type_in),
          res_mapped_dims(res_type.mapped_dimensions().size()),
          left_mapped_dims(left_type.mapped_dimensions().size()),
          right_mapped_dims(right_type.mapped_dimensions().size()),
          dense_subspace_size(left_type.dense_subspace_size()),
          right_view_dims(),
          factory(factory_in)
    {
        size_t right_idx = 0;
        auto check_kept = [&](const auto &dim) -> bool {
            return res_type.dimension_index(dim.name) != ValueType::Dimension::npos;
        };
        auto visitor = overload
                {
                    [&](visit_ranges_first, const auto &a) {
                        sparse_dims_meta.push_back({SparseDimSource::LEFT, check_kept(a)});
                    },
                    [&](visit_ranges_second, const auto &b) {
                        sparse_dims_meta.push_back({SparseDimSource::RIGHT,check_kept(b)});
                        ++right_idx;
                    },
                    [&](visit_ranges_both, const auto &a, const auto &) {
                        sparse_dims_meta.push_back({SparseDimSource::BOTH, check_kept(a)});
                        right_view_dims.push_back(right_idx++);
                    }
                };
        auto left_dims = left_type.mapped_dimensions();
        auto right_dims = right_type.mapped_dimensions();
        visit_ranges(visitor, left_dims.begin(), left_dims.end(), right_dims.begin(), right_dims.end(),
                     [](const auto &a, const auto &b){ return (a.name < b.name); });
        LOG_ASSERT(dense_subspace_size == right_type.dense_subspace_size());
    }
};

struct DotproductInsideMixedSparseState {
    SmallVector<string_id>                  result_labels;
    SmallVector<string_id>                  other_labels;
    SmallVector<string_id*>                 left_address;
    SmallVector<const string_id*>           address_overlap;
    SmallVector<string_id*>                 right_only_address;
    size_t                                  left_subspace;
    size_t                                  right_subspace;
    DotproductInsideMixedSparseState(const DotproductInsideMixedParam &params)
        : result_labels(params.res_mapped_dims),
          other_labels(params.sparse_dims_meta.size() - params.res_mapped_dims),
          left_subspace(-1),
          right_subspace(-1)
    {
        auto result_label_iter = result_labels.begin();
        auto other_label_iter = other_labels.begin();
        for (const auto &dim : params.sparse_dims_meta) {
            string_id * p = dim.keep ? result_label_iter++ : other_label_iter++;
            switch (dim.src) {
                case SparseDimSource::BOTH:
                    left_address.push_back(p);
                    address_overlap.push_back(p);
                    break;
                case SparseDimSource::LEFT:
                    left_address.push_back(p);
                    break;
                case SparseDimSource::RIGHT:
                    right_only_address.push_back(p);
                    break;
            }
        }
        LOG_ASSERT(result_label_iter == result_labels.end());
        LOG_ASSERT(other_label_iter == other_labels.end());
        LOG_ASSERT(address_overlap.size() == params.right_view_dims.size());
        LOG_ASSERT(left_address.size() == params.left_mapped_dims);
        LOG_ASSERT(address_overlap.size() + right_only_address.size() == params.right_mapped_dims);
    }
    ~DotproductInsideMixedSparseState();
};

DotproductInsideMixedSparseState::~DotproductInsideMixedSparseState() = default;

template<typename CT>
void my_dotproduct_inside_mixed_op(InterpretedFunction::State &state, uint64_t param_in) {
    const auto &params = unwrap_param<DotproductInsideMixedParam>(param_in);
    const auto &left = state.peek(1);
    const auto &right = state.peek(0);
    const auto & l_index = left.index();
    const auto & r_index = right.index();
    LOG(debug, "left type: %s", left.type().to_spec().c_str());
    LOG(debug, "right type: %s", right.type().to_spec().c_str());
    const CT *l_cells = left.cells().typify<CT>().data();
    const CT *r_cells = right.cells().typify<CT>().data();
    DotproductInsideMixedSparseState sparse{params};
    ConstArrayRef<string_id> keep_addr{sparse.result_labels};
    ArrayArrayMap<string_id,CT> my_map{params.res_mapped_dims, 1, l_index.size() + r_index.size()};
    auto outer = l_index.create_view({});
    auto inner = r_index.create_view(params.right_view_dims);
    outer->lookup({});
    LOG(debug, "outer->next_result [%u]", sparse.left_address.size());
    while (outer->next_result(sparse.left_address, sparse.left_subspace)) {
        LOG(debug, "inner->lookup [%u]", sparse.address_overlap.size());
        inner->lookup(sparse.address_overlap);
        LOG(debug, "inner->next_result [%u]", sparse.right_only_address.size());
        while (inner->next_result(sparse.right_only_address, sparse.right_subspace)) {
            auto [tag, ignore] = my_map.lookup_or_add_entry(keep_addr);
            CT *dst = my_map.get_values(tag).begin();
            size_t l_offset = params.dense_subspace_size * sparse.left_subspace;
            size_t r_offset = params.dense_subspace_size * sparse.right_subspace;
            const CT *l_cp = l_cells + l_offset;
            const CT *r_cp = r_cells + r_offset;
            *dst += MyDotProduct<CT,CT>::apply(l_cp, r_cp, params.dense_subspace_size);
        }
    }
    auto builder = params.factory.create_transient_value_builder<CT>(params.res_type, params.res_mapped_dims, 1, my_map.size());
    my_map.each_entry([&](vespalib::ConstArrayRef<string_id> keys, vespalib::ConstArrayRef<CT> values)
    {
        CT *dst = builder->add_subspace(keys).begin();
        *dst = values[0];
    });
    auto up = builder->build(std::move(builder));
    auto &result = state.stash.create<std::unique_ptr<Value>>(std::move(up));
    const Value &result_ref = *(result.get());
    state.pop_pop_push(result_ref);
}

struct SelectDotproductInsideMixed {
    template <typename ICM>
    static auto invoke() {
        constexpr CellMeta ocm = ICM::value.reduce(false);
        using CT = CellValueType<ocm.cell_type>;
        return my_dotproduct_inside_mixed_op<CT>;
    }
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
            if (DotproductInsideMixed::compatible_types(res_type, lhs.result_type(), rhs.result_type())) {
                if (DotproductInsideMixed::prefer_swap(lhs.result_type(), rhs.result_type())) {
                    return stash.create<DotproductInsideMixed>(res_type, rhs, lhs);
                }
                return stash.create<DotproductInsideMixed>(res_type, lhs, rhs);
            }
        }
    }
    return expr;
}

bool
DotproductInsideMixed::compatible_types(const ValueType &res, const ValueType &left, const ValueType &right) {
    return ((left.cell_type() == res.cell_type())
            && (right.cell_type() == res.cell_type())
            && (res.count_mapped_dimensions() > 0)
            && (res.count_nontrivial_indexed_dimensions() == 0)
            && (left.count_mapped_dimensions() > 0)
            && (right.count_mapped_dimensions() > 0)
            && (left.count_nontrivial_indexed_dimensions() > 0)
            && (right.count_nontrivial_indexed_dimensions() > 0)
            && (left.nontrivial_indexed_dimensions() == right.nontrivial_indexed_dimensions()));
}

bool
DotproductInsideMixed::prefer_swap(const ValueType &left, const ValueType &right) {
    bool some_overlap = false;
    bool all_overlap_l = true;
    bool all_overlap_r = true;
    const auto &left_dims = left.mapped_dimensions();
    const auto &right_dims = right.mapped_dimensions();
    auto visitor = overload
            {
                [&](visit_ranges_first, const auto &) { all_overlap_l = false; },
                [&](visit_ranges_second, const auto &) { all_overlap_r = false; },
                [&](visit_ranges_both, const auto &, const auto &) { some_overlap = true; }
            };
    visit_ranges(visitor, left_dims.begin(), left_dims.end(), right_dims.begin(), right_dims.end(),
                 [](const auto &a, const auto &b){ return (a.name < b.name); });
    return some_overlap && all_overlap_l && !all_overlap_r;
}

DotproductInsideMixed::DotproductInsideMixed(
        const ValueType &res_type_in,
        const TensorFunction &left_child,
        const TensorFunction &right_child)
    : tensor_function::Op2(res_type_in, left_child, right_child)
{
}

InterpretedFunction::Instruction
DotproductInsideMixed::compile_self(const ValueBuilderFactory &factory, Stash &stash) const
{
    const auto &res_type = result_type();
    const auto &left_type = lhs().result_type();
    const auto &right_type = rhs().result_type();
    auto &param = stash.create<DotproductInsideMixedParam>(res_type, left_type, right_type, factory);
    using MyTypify = TypifyValue<TypifyCellMeta>;
    auto op = typify_invoke<1,MyTypify,SelectDotproductInsideMixed>(left_type.cell_meta());
    return InterpretedFunction::Instruction(op, wrap_param<DotproductInsideMixedParam>(param));
}

} // namespace
