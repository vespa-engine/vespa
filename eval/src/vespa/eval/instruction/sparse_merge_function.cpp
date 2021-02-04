// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sparse_merge_function.h"
#include "generic_join.h"
#include <vespa/eval/eval/fast_value.hpp>
#include <vespa/vespalib/util/typify.h>

namespace vespalib::eval {

using namespace tensor_function;
using namespace operation;
using namespace instruction;

namespace {

struct SparseMergeParam {
    const ValueType res_type;
    const join_fun_t function;
    const size_t num_mapped_dimensions;
    std::vector<size_t> all_view_dims;
    const ValueBuilderFactory &factory;

    SparseMergeParam(const ValueType &res_type_in, join_fun_t function_in, const ValueBuilderFactory &factory_in)
      : res_type(res_type_in),
        function(function_in),
        num_mapped_dimensions(res_type.count_mapped_dimensions()),
        all_view_dims(num_mapped_dimensions),
        factory(factory_in)
    {
        assert(!res_type.is_error());
        for (size_t i = 0; i < num_mapped_dimensions; ++i) {
            all_view_dims[i] = i;
        }
    }
    ~SparseMergeParam();
};
SparseMergeParam::~SparseMergeParam() = default;

template <typename CT, typename Fun>
std::unique_ptr<Value> my_sparse_merge_fallback(const Value::Index &a_idx, const Value::Index &b_idx,
                                                ConstArrayRef<CT> a_cells, ConstArrayRef<CT> b_cells,
                                                const SparseMergeParam &param) __attribute__((noinline));
template <typename CT, typename Fun>
std::unique_ptr<Value> my_sparse_merge_fallback(const Value::Index &a_idx, const Value::Index &b_idx,
                                                ConstArrayRef<CT> a_cells, ConstArrayRef<CT> b_cells,
                                                const SparseMergeParam &params)
{
    Fun fun(params.function);
    const size_t num_mapped = params.num_mapped_dimensions;
    const size_t subspace_size = 1u;
    size_t guess_subspaces = std::max(a_idx.size(), b_idx.size());
    auto builder = params.factory.create_transient_value_builder<CT>(params.res_type, num_mapped, subspace_size, guess_subspaces);
    std::vector<string_id> address(num_mapped);
    std::vector<const string_id *> addr_cref;
    std::vector<string_id *> addr_ref;
    for (auto & ref : address) {
        addr_cref.push_back(&ref);
        addr_ref.push_back(&ref);
    }
    size_t a_subspace;
    size_t b_subspace;
    auto inner = b_idx.create_view(params.all_view_dims);
    auto outer = a_idx.create_view({});
    outer->lookup({});
    while (outer->next_result(addr_ref, a_subspace)) {
        ArrayRef<CT> dst = builder->add_subspace(address);
        inner->lookup(addr_cref);
        if (inner->next_result({}, b_subspace)) {
            dst[0] = fun(a_cells[a_subspace], b_cells[b_subspace]);
        } else {
            dst[0] = a_cells[a_subspace];
        }
    }
    inner = a_idx.create_view(params.all_view_dims);
    outer = b_idx.create_view({});
    outer->lookup({});
    while (outer->next_result(addr_ref, b_subspace)) {
        inner->lookup(addr_cref);
        if (! inner->next_result({}, a_subspace)) {
            ArrayRef<CT> dst = builder->add_subspace(address);
            dst[0] = b_cells[b_subspace];
        }
    }
    return builder->build(std::move(builder));
}


template <typename CT, bool single_dim, typename Fun>
const Value& my_fast_sparse_merge(const FastAddrMap &a_map, const FastAddrMap &b_map,
                                  const CT *a_cells, const CT *b_cells,
                                  const SparseMergeParam &params,
                                  Stash &stash)
{
    Fun fun(params.function);
    size_t guess_size = a_map.size() + b_map.size();
    auto &result = stash.create<FastValue<CT,true>>(params.res_type, params.num_mapped_dimensions, 1u, guess_size);
    if constexpr (single_dim) {
        string_id cur_label;
        ConstArrayRef<string_id> addr(&cur_label, 1);
        const auto &a_labels = a_map.labels();
        for (size_t i = 0; i < a_labels.size(); ++i) {
            cur_label = a_labels[i];
            result.add_mapping(addr, cur_label.hash());
            result.my_cells.push_back_fast(a_cells[i]);
        }
        const auto &b_labels = b_map.labels();
        for (size_t i = 0; i < b_labels.size(); ++i) {
            cur_label = b_labels[i];
            auto result_subspace = result.my_index.map.lookup_singledim(cur_label);
            if (result_subspace == FastAddrMap::npos()) {
                result.add_mapping(addr, cur_label.hash());
                result.my_cells.push_back_fast(b_cells[i]);
            } else {
                CT *out_cell = result.my_cells.get(result_subspace);
                out_cell[0] = fun(out_cell[0], b_cells[i]);
            }
        }
    } else {
        a_map.each_map_entry([&](auto lhs_subspace, auto hash)
        {
            result.add_mapping(a_map.get_addr(lhs_subspace), hash);
            result.my_cells.push_back_fast(a_cells[lhs_subspace]);
        });
        b_map.each_map_entry([&](auto rhs_subspace, auto hash)
        {
            auto rhs_addr = b_map.get_addr(rhs_subspace);
            auto result_subspace = result.my_index.map.lookup(rhs_addr, hash);
            if (result_subspace == FastAddrMap::npos()) {
                result.add_mapping(rhs_addr, hash);
                result.my_cells.push_back_fast(b_cells[rhs_subspace]);
            } else {
                CT *out_cell = result.my_cells.get(result_subspace);
                out_cell[0] = fun(out_cell[0], b_cells[rhs_subspace]);
            }
        });
    }
    return result;


}

template <typename CT, bool single_dim, typename Fun>
void my_sparse_merge_op(InterpretedFunction::State &state, uint64_t param_in) {
    const auto &param = unwrap_param<SparseMergeParam>(param_in);
    const auto &a_idx = state.peek(1).index();
    const auto &b_idx = state.peek(0).index();
    auto a_cells = state.peek(1).cells().typify<CT>();
    auto b_cells = state.peek(0).cells().typify<CT>();
    //assert(a_idx.size() == a_cells.size());
    //assert(b_idx.size() == b_cells.size());
    if (__builtin_expect(are_fast(a_idx, b_idx), true)) {
        const Value &v = my_fast_sparse_merge<CT,single_dim,Fun>(as_fast(a_idx).map, as_fast(b_idx).map, a_cells.cbegin(), b_cells.cbegin(), param, state.stash);
        state.pop_pop_push(v);
    } else {
        auto up = my_sparse_merge_fallback<CT,Fun>(a_idx, b_idx, a_cells, b_cells, param);
        state.pop_pop_push(*state.stash.create<std::unique_ptr<Value>>(std::move(up)));
    }
}

struct SelectSparseMergeOp {
    template <typename CT, typename SINGLE_DIM, typename Fun>
    static auto invoke() { return my_sparse_merge_op<CT,SINGLE_DIM::value,Fun>; }
};

using MyTypify = TypifyValue<TypifyCellType,TypifyBool,operation::TypifyOp2>;

} // namespace <unnamed>

SparseMergeFunction::SparseMergeFunction(const tensor_function::Merge &original)
  : tensor_function::Merge(original.result_type(),
                           original.lhs(),
                           original.rhs(),
                           original.function())
{
    assert(compatible_types(result_type(), lhs().result_type(), rhs().result_type()));
}

InterpretedFunction::Instruction
SparseMergeFunction::compile_self(const ValueBuilderFactory &factory, Stash &stash) const
{
    assert(result_type() == ValueType::join(lhs().result_type(), rhs().result_type()));
    const auto &param = stash.create<SparseMergeParam>(result_type(), function(), factory);
    size_t num_dims = result_type().count_mapped_dimensions();
    auto op = typify_invoke<3,MyTypify,SelectSparseMergeOp>(result_type().cell_type(),
                                                            num_dims == 1,
                                                            function());
    return InterpretedFunction::Instruction(op, wrap_param<SparseMergeParam>(param));
}

bool
SparseMergeFunction::compatible_types(const ValueType &res, const ValueType &lhs, const ValueType &rhs)
{
    if ((lhs.cell_type() == rhs.cell_type())
        && (lhs.count_mapped_dimensions() > 0)
        && (lhs.dense_subspace_size() == 1))
    {
        assert(rhs.dense_subspace_size() == lhs.dense_subspace_size());
        assert(rhs.count_mapped_dimensions() == lhs.count_mapped_dimensions());

        assert(res.cell_type() == lhs.cell_type());
        assert(res.dense_subspace_size() == lhs.dense_subspace_size());
        assert(res.count_mapped_dimensions() == lhs.count_mapped_dimensions());

        return true;
    }
    return false;
}

const TensorFunction &
SparseMergeFunction::optimize(const TensorFunction &expr, Stash &stash)
{
    if (auto merge = as<Merge>(expr)) {
        const TensorFunction &lhs = merge->lhs();
        const TensorFunction &rhs = merge->rhs();
        if (compatible_types(expr.result_type(), lhs.result_type(), rhs.result_type())) {
            return stash.create<SparseMergeFunction>(*merge);
        }
    }
    return expr;
}

} // namespace
