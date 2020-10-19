// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "generic_reduce.h"
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/wrap_param.h>
#include <vespa/vespalib/util/stash.h>
#include <vespa/vespalib/util/typify.h>
#include <cassert>

using namespace vespalib::eval::tensor_function;

namespace vespalib::eval::instruction {

using State = InterpretedFunction::State;
using Instruction = InterpretedFunction::Instruction;

namespace {

//-----------------------------------------------------------------------------

struct ReduceParam {
    ValueType res_type;
    SparseReducePlan sparse_plan;
    DenseReducePlan dense_plan;
    const ValueBuilderFactory &factory;
    ReduceParam(const ValueType &type, const std::vector<vespalib::string> &dimensions,
                const ValueBuilderFactory &factory_in)
        : res_type(type.reduce(dimensions)),
          sparse_plan(type, res_type),
          dense_plan(type, res_type),
          factory(factory_in)
    {
        assert(!res_type.is_error());
        assert(dense_plan.in_size == type.dense_subspace_size());
        assert(dense_plan.out_size == res_type.dense_subspace_size());
    }
    ~ReduceParam();
};
ReduceParam::~ReduceParam() = default;

//-----------------------------------------------------------------------------

struct SparseReduceState {

    // TODO(havardpe): Optimize using vespalib::hash_map with
    // reference to slice of external vector of vespalib::stringref as
    // key. Track matching subspaces as linked lists contained in a
    // single shared vector.

    using MapKey = std::vector<vespalib::string>;
    using MapValue = std::vector<size_t>;
    using Map = std::map<MapKey,MapValue>;

    Map                               map;
    std::vector<vespalib::stringref>  full_address;
    std::vector<vespalib::stringref*> fetch_address;
    std::vector<vespalib::stringref*> keep_address;
    size_t                            subspace;

    SparseReduceState(const SparseReducePlan &plan)
        : full_address(plan.keep_dims.size() + plan.num_reduce_dims),
          fetch_address(full_address.size(), nullptr),
          keep_address(plan.keep_dims.size(), nullptr)
    {
        for (size_t i = 0; i < keep_address.size(); ++i) {
            keep_address[i] = &full_address[plan.keep_dims[i]];
        }
        for (size_t i = 0; i < full_address.size(); ++i) {
            fetch_address[i] = &full_address[i];
        }
    }
    ~SparseReduceState();
    void populate_map(std::unique_ptr<Value::Index::View> full_view) {
        full_view->lookup({});
        while (full_view->next_result(fetch_address, subspace)) {
            std::vector<vespalib::string> key;
            key.reserve(keep_address.size());
            for (const vespalib::stringref *label: keep_address) {
                key.emplace_back(*label);
            }
            map[key].push_back(subspace);
        }
    }
    template <typename T>
    std::vector<vespalib::stringref> make_addr(const T &map_entry) {
        std::vector<vespalib::stringref> addr;
        addr.reserve(map_entry.first.size());
        for (const vespalib::string &label: map_entry.first) {
            addr.push_back(label);
        }
        return addr;
    }
};
SparseReduceState::~SparseReduceState() = default;

template <typename ICT, typename OCT, typename AGGR>
Value::UP
generic_reduce(const Value &value, const ReduceParam &param) {
    SparseReduceState sparse(param.sparse_plan);
    sparse.populate_map(value.index().create_view({}));
    auto cells = value.cells().typify<ICT>();
    AGGR aggr;
    auto first = [&](size_t idx) { aggr.first(cells[idx]); };
    auto next = [&](size_t idx) { aggr.next(cells[idx]); };
    auto builder = param.factory.create_value_builder<OCT>(param.res_type, param.sparse_plan.keep_dims.size(), param.dense_plan.out_size, sparse.map.size());
    for (const auto &entry: sparse.map) {
        OCT *dst = builder->add_subspace(sparse.make_addr(entry)).begin();
        auto reduce_cells = [&](size_t rel_idx)
                            {
                                auto pos = entry.second.begin();
                                param.dense_plan.execute_reduce((*pos * param.dense_plan.in_size) + rel_idx, first, next);
                                for (++pos; pos != entry.second.end(); ++pos) {
                                    param.dense_plan.execute_reduce((*pos * param.dense_plan.in_size) + rel_idx, next);
                                }
                                *dst++ = aggr.result();
                            };
        param.dense_plan.execute_keep(reduce_cells);
    }
    if (sparse.map.empty() && param.sparse_plan.keep_dims.empty()) {
        auto zero = builder->add_subspace({});
        for (size_t i = 0; i < zero.size(); ++i) {
            zero[i] = OCT{};
        }
    }
    return builder->build(std::move(builder));
}

template <typename ICT, typename OCT, typename AGGR>
void my_generic_reduce_op(State &state, uint64_t param_in) {
    const auto &param = unwrap_param<ReduceParam>(param_in);
    const Value &value = state.peek(0);
    auto up = generic_reduce<ICT, OCT, AGGR>(value, param);
    auto &result = state.stash.create<std::unique_ptr<Value>>(std::move(up));
    const Value &result_ref = *(result.get());
    state.pop_push(result_ref);
};

struct SelectGenericReduceOp {
    template <typename ICT, typename OCT, typename AGGR> static auto invoke() {
        return my_generic_reduce_op<ICT, OCT, typename AGGR::template templ<ICT>>;
    }
};

struct PerformGenericReduce {
    template <typename ICT, typename OCT, typename AGGR>
    static auto invoke(const Value &input, const ReduceParam &param) {
        return generic_reduce<ICT, OCT, typename AGGR::template templ<ICT>>(input, param);
    }
};

//-----------------------------------------------------------------------------

} // namespace <unnamed>

//-----------------------------------------------------------------------------

DenseReducePlan::DenseReducePlan(const ValueType &type, const ValueType &res_type)
    : in_size(1),
      out_size(1),
      keep_loop(),
      keep_stride(),
      reduce_loop(),
      reduce_stride()
{
    std::vector<bool> keep;
    std::vector<size_t> size;
    for (const auto &dim: type.nontrivial_indexed_dimensions()) {
        keep.push_back(res_type.dimension_index(dim.name) != ValueType::Dimension::npos);
        size.push_back(dim.size);
    }
    std::vector<size_t> stride(size.size(), 0);
    for (size_t i = stride.size(); i-- > 0; ) {
        stride[i] = in_size;
        in_size *= size[i];
        if (keep[i]) {
            out_size *= size[i];
        }
    }
    int prev_case = 2;
    for (size_t i = 0; i < size.size(); ++i) {
        int my_case = keep[i] ? 1 : 0;
        auto &my_loop = keep[i] ? keep_loop : reduce_loop;
        auto &my_stride = keep[i] ? keep_stride : reduce_stride;
        if (my_case == prev_case) {
            assert(!my_loop.empty());
            my_loop.back() *= size[i];
            my_stride.back() = stride[i];
        } else {
            my_loop.push_back(size[i]);
            my_stride.push_back(stride[i]);
        }
        prev_case = my_case;
    }
}

DenseReducePlan::~DenseReducePlan() = default;

//-----------------------------------------------------------------------------

SparseReducePlan::SparseReducePlan(const ValueType &type, const ValueType &res_type)
    : num_reduce_dims(0),
      keep_dims()
{
    auto dims = type.mapped_dimensions();
    for (size_t i = 0; i < dims.size(); ++i) {
        bool keep = (res_type.dimension_index(dims[i].name) != ValueType::Dimension::npos);
        if (keep) {
            keep_dims.push_back(i);
        } else {
            ++num_reduce_dims;
        }
    }
}

SparseReducePlan::~SparseReducePlan() = default;

//-----------------------------------------------------------------------------

using ReduceTypify = TypifyValue<TypifyCellType,TypifyAggr>;

Instruction
GenericReduce::make_instruction(const ValueType &type, Aggr aggr, const std::vector<vespalib::string> &dimensions,
                                const ValueBuilderFactory &factory, Stash &stash)
{
    auto &param = stash.create<ReduceParam>(type, dimensions, factory);
    auto fun = typify_invoke<3,ReduceTypify,SelectGenericReduceOp>(type.cell_type(), param.res_type.cell_type(), aggr);
    return Instruction(fun, wrap_param<ReduceParam>(param));
}


Value::UP
GenericReduce::perform_reduce(const Value &a, Aggr aggr,
                              const std::vector<vespalib::string> &dimensions,
                              const ValueBuilderFactory &factory)
{
    ReduceParam param(a.type(), dimensions, factory);
    return typify_invoke<3,ReduceTypify,PerformGenericReduce>(
            a.type().cell_type(), param.res_type.cell_type(), aggr,
            a, param);
}

} // namespace
