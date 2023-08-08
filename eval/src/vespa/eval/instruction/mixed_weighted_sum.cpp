// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "mixed_weighted_sum.h"
#include <vespa/vespalib/objects/objectvisitor.h>
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/value_builder_factory.h>
#include <vespa/eval/eval/operation.h>
#include <vespa/eval/eval/inline_operation.h>
#include <vespa/vespalib/util/small_vector.h>
#include <vespa/vespalib/util/typify.h>
#include <optional>
#include <algorithm>

#include <vespa/log/log.h>
LOG_SETUP(".eval.instruction.mixed_weighted_sum");

namespace vespalib::eval {

using vespalib::ArrayRef;
using vespalib::SmallVector;

using namespace operation;
using namespace tensor_function;

using op_function = InterpretedFunction::op_function;
using Instruction = InterpretedFunction::Instruction;
using State = InterpretedFunction::State;

namespace {

struct MixedWeightedSumParam {
    const ValueType res_type;
    const size_t dense_subspace_size;
    const size_t res_mapped_dims;
    const ValueBuilderFactory &factory;
    const size_t select_dim_idx;
    MixedWeightedSumParam(const ValueType & res_type_in,
                              const ValueBuilderFactory &factory_in,
                              size_t select_dim_idx_in)
        : res_type(res_type_in),
          dense_subspace_size(res_type.dense_subspace_size()),
          res_mapped_dims(res_type.count_mapped_dimensions()),
          factory(factory_in),
          select_dim_idx(select_dim_idx_in)
        {}
};

struct MySparseState {
    string_id select_label;
    SmallVector<string_id *> select_label_ref;
    SmallVector<const string_id *> select_label_cref;
    SmallVector<string_id> result_labels;
    SmallVector<string_id *> result_labels_ref;
    SmallVector<string_id *> all_labels_ref;
    MySparseState(size_t res_mapped_dims_cnt, size_t select_dim_idx)
        : select_label(),
          select_label_ref(),
          select_label_cref(),
          result_labels(res_mapped_dims_cnt),
          all_labels_ref()
        {
            select_label_ref.push_back(&select_label);
            select_label_cref.push_back(&select_label);
            if (select_dim_idx == 0) {
                all_labels_ref.push_back(&select_label);
            }
            for (auto & l : result_labels) {
                result_labels_ref.push_back(&l);
                all_labels_ref.push_back(&l);
                if (all_labels_ref.size() == select_dim_idx) {
                    all_labels_ref.push_back(&select_label);
                }
            }
        }
    ~MySparseState();
};

MySparseState::~MySparseState() = default;

template <typename CT>
void my_weighted_sum_op(State &state, uint64_t param_in) {
    const auto &params = unwrap_param<MixedWeightedSumParam>(param_in);
    auto sel_cells = state.peek(0).cells().typify<CT>();
    auto mix_cells = state.peek(1).cells().typify<CT>();
    const auto &sel_index = state.peek(0).index();
    const auto &mix_index = state.peek(1).index();
    MySparseState sparse_state{params.res_mapped_dims, params.select_dim_idx};
    std::unique_ptr<ValueBuilder<CT>> builder;
    if (sel_cells.size() == 1 && sel_cells[0] == 1.0) {
        // actually select (partial lookup)
        LOG_ASSERT(sel_index.size() == 1);
        auto sel_view = sel_index.create_view({});
        sel_view->lookup({});
        size_t subspace_idx;
        bool ok = sel_view->next_result(sparse_state.select_label_ref, subspace_idx);
        LOG_ASSERT(ok);
        LOG_ASSERT(subspace_idx == 0);
        auto mix_view = mix_index.create_view({&params.select_dim_idx, 1});
        mix_view->lookup(sparse_state.select_label_cref);
        size_t matches = 0;
        while (mix_view->next_result(sparse_state.result_labels_ref, subspace_idx)) {
            ++matches;
        }
        builder = params.factory.create_transient_value_builder<CT>(params.res_type, params.res_mapped_dims, params.dense_subspace_size, matches);
        mix_view->lookup(sparse_state.select_label_cref);
        while (mix_view->next_result(sparse_state.result_labels_ref, subspace_idx)) {
            size_t offset = params.dense_subspace_size * subspace_idx;
            auto dst_cells = builder->add_subspace(sparse_state.result_labels);
            memcpy(dst_cells.data(), &mix_cells[offset], dst_cells.size() * sizeof(CT));
        }
        ok = ! sel_view->next_result(sparse_state.select_label_ref, subspace_idx);
        LOG_ASSERT(ok);
    } else {
        // weighted sum
        size_t sel_dim_idx = 0;
        auto sel_view = sel_index.create_view({&sel_dim_idx, 1});
        size_t mix_subspace_idx;
        std::map<SmallVector<string_id>, SmallVector<std::pair<size_t, size_t>>> mix_map;
        auto mix_view = mix_index.create_view({});
        mix_view->lookup({});
        while (mix_view->next_result(sparse_state.all_labels_ref, mix_subspace_idx)) {
            size_t sel_subspace_idx;
            sel_view->lookup(sparse_state.select_label_cref);
            if (sel_view->next_result({}, sel_subspace_idx)) {
                auto & todo_list = mix_map[sparse_state.result_labels];
                todo_list.push_back(std::make_pair(sel_subspace_idx, mix_subspace_idx));
            }
        }
        builder = params.factory.create_transient_value_builder<CT>(params.res_type, params.res_mapped_dims, params.dense_subspace_size, mix_map.size());
        for (const auto & [k, todo_list] : mix_map) {
            auto dst_cells = builder->add_subspace(k);
            memset(dst_cells.data(), 0, dst_cells.size() * sizeof(CT));
            for (const auto & idx_pair : todo_list) {
                CT weight = sel_cells[idx_pair.first];
                size_t offset = params.dense_subspace_size * idx_pair.second;
                for (size_t i = 0; i < params.dense_subspace_size; ++i) {
                    dst_cells[i] = dst_cells[i] + weight * mix_cells[offset + i];
                }
            }
        }
    }
    auto up = builder->build(std::move(builder));
    auto &result = state.stash.create<std::unique_ptr<Value>>(std::move(up));
    const Value &result_ref = *(result.get());
    state.pop_pop_push(result_ref);
}

//-----------------------------------------------------------------------------

struct SelectMixedWeightedSumOp {
    template<typename CM>
    static auto invoke() {
        using CT = CellValueType<CM::value.cell_type>;
        return my_weighted_sum_op<CT>;
    }
};

//-----------------------------------------------------------------------------


bool compatible_types(const ValueType &res, const ValueType &mix, const ValueType &sel, const vespalib::string &dim) {
    return ((mix.cell_type() == res.cell_type()) &&
            (sel.cell_type() == res.cell_type()) &&
            (res.is_mixed()) &&
            (mix.count_mapped_dimensions() == res.count_mapped_dimensions() + 1) &&
            (sel.count_mapped_dimensions() == 1) &&
            (mix.dimension_index(dim) != ValueType::Dimension::npos) &&
            (mix.dense_subspace_size() == res.dense_subspace_size()) &&
            (mix.dense_subspace_size() > 7) &&
            sel.is_sparse() &&
            (sel.dimensions()[0].name == dim));
}

size_t find_idx(const std::vector<ValueType::Dimension> & dim_list, const vespalib::string & dim) {
    size_t idx = 0;
    for (const auto & d : dim_list) {
        if (d.name == dim) return idx;
        ++idx;
    }
    LOG_ABORT("dim must exist in dim_list");
}

} // namespace vespalib::eval::<unnamed>

//-----------------------------------------------------------------------------

MixedWeightedSumFunction::MixedWeightedSumFunction(const ValueType &result_type,
                                                       const TensorFunction &lhs,
                                                       const TensorFunction &rhs,
                                                       const vespalib::string &dim)
    : tensor_function::Op2(result_type, lhs, rhs),
      _select_dim(dim)
{
}

MixedWeightedSumFunction::~MixedWeightedSumFunction() = default;

Instruction
MixedWeightedSumFunction::compile_self(const ValueBuilderFactory &factory, Stash &stash) const
{
    size_t mix_dim_idx = find_idx(lhs().result_type().mapped_dimensions(), _select_dim);
    const MixedWeightedSumParam &params = stash.create<MixedWeightedSumParam>(result_type(), factory, mix_dim_idx);
    auto res_meta = result_type().cell_meta().decay().limit();
    auto op = typify_invoke<1,TypifyCellMeta,SelectMixedWeightedSumOp>(res_meta);
    return Instruction(op, wrap_param<MixedWeightedSumParam>(params));
}

const TensorFunction &
MixedWeightedSumFunction::optimize(const TensorFunction &expr, Stash &stash)
{
    auto reduce = as<Reduce>(expr);
    if (reduce && (reduce->aggr() == Aggr::SUM) && (reduce->dimensions().size() == 1)) {
        const auto & dim = reduce->dimensions()[0];
        auto join = as<Join>(reduce->child());
        if (join && (join->function() == Mul::f)) {
            const auto & lhs = join->lhs();
            const auto & rhs = join->rhs();
            const auto & res_type = expr.result_type();
            const auto & left_type = lhs.result_type();
            const auto & right_type = rhs.result_type();
            if (compatible_types(res_type, left_type, right_type, dim)) {
                return stash.create<MixedWeightedSumFunction>(res_type, lhs, rhs, dim);
            }
            if (compatible_types(res_type, right_type, left_type, dim)) {
                return stash.create<MixedWeightedSumFunction>(res_type, rhs, lhs, dim);
            }
        }
    }
    return expr;
}

} // namespace vespalib::eval
