// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "generic_reduce.h"
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/wrap_param.h>
#include <vespa/eval/eval/array_array_map.h>
#include <vespa/vespalib/util/stash.h>
#include <vespa/vespalib/util/typify.h>
#include <vespa/vespalib/util/overload.h>
#include <vespa/vespalib/util/visit_ranges.h>
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
    std::vector<vespalib::stringref>  full_address;
    std::vector<vespalib::stringref*> fetch_address;
    std::vector<vespalib::stringref*> keep_address;
    size_t                            subspace;

    SparseReduceState(const SparseReducePlan &plan)
        : full_address(plan.keep_dims.size() + plan.num_reduce_dims),
          fetch_address(full_address.size(), nullptr),
          keep_address(plan.keep_dims.size(), nullptr),
          subspace()
    {
        for (size_t i = 0; i < keep_address.size(); ++i) {
            keep_address[i] = &full_address[plan.keep_dims[i]];
        }
        for (size_t i = 0; i < full_address.size(); ++i) {
            fetch_address[i] = &full_address[i];
        }
    }
    ~SparseReduceState();
};
SparseReduceState::~SparseReduceState() = default;

template <typename ICT, typename OCT, typename AGGR>
Value::UP
generic_reduce(const Value &value, const ReduceParam &param) {
    auto cells = value.cells().typify<ICT>();
    ArrayArrayMap<vespalib::stringref,AGGR> map(param.sparse_plan.keep_dims.size(),
                                                param.dense_plan.out_size,
                                                value.index().size());
    SparseReduceState sparse(param.sparse_plan);
    auto full_view = value.index().create_view({});
    full_view->lookup({});
    ConstArrayRef<vespalib::stringref*> keep_addr(sparse.keep_address);
    while (full_view->next_result(sparse.fetch_address, sparse.subspace)) {
        auto [tag, ignore] = map.lookup_or_add_entry(keep_addr);
        AGGR *dst = map.get_values(tag).begin();
        auto sample = [&](size_t src_idx, size_t dst_idx) { dst[dst_idx].sample(cells[src_idx]); };
        param.dense_plan.execute(sparse.subspace * param.dense_plan.in_size, sample);
    }
    auto builder = param.factory.create_value_builder<OCT>(param.res_type, param.sparse_plan.keep_dims.size(), param.dense_plan.out_size, map.size());
    map.each_entry([&](const auto &keys, const auto &values)
                   {
                       OCT *dst = builder->add_subspace(keys).begin();
                       for (const AGGR &aggr: values) {
                           *dst++ = aggr.result();
                       }
                   });
    if ((map.size() == 0) && param.sparse_plan.keep_dims.empty()) {
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

template <typename ICT, typename OCT, typename AGGR>
void my_full_reduce_op(State &state, uint64_t) {
    auto cells = state.peek(0).cells().typify<ICT>();
    if (cells.size() > 0) {
        AGGR aggr[4];
        size_t i = 0;
        for (; (i + 3) < cells.size(); i += 4) {
            aggr[0].sample(cells[i+0]);
            aggr[1].sample(cells[i+1]);
            aggr[2].sample(cells[i+2]);
            aggr[3].sample(cells[i+3]);
        }
        for (; i < cells.size(); ++i) {
            aggr[0].sample(cells[i]);
        }
        aggr[0].merge(aggr[1]);
        aggr[0].merge(aggr[2]);
        aggr[0].merge(aggr[3]);
        state.pop_push(state.stash.create<ScalarValue<OCT>>(aggr[0].result()));
    } else {
        state.pop_push(state.stash.create<ScalarValue<OCT>>(0.0));
    }
};

struct SelectGenericReduceOp {
    template <typename ICT, typename OCT, typename AGGR> static auto invoke(const ReduceParam &param) {
        if (param.res_type.is_scalar()) {
            return my_full_reduce_op<ICT, OCT, typename AGGR::template templ<ICT>>;
        }
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
      loop_cnt(),
      in_stride(),
      out_stride()
{
    enum class Case { NONE, KEEP, REDUCE };
    Case prev_case = Case::NONE;
    auto update_plan = [&](Case my_case, size_t my_size) {
        if (my_case == prev_case) {
            assert(!loop_cnt.empty());
            loop_cnt.back() *= my_size;
        } else {
            loop_cnt.push_back(my_size);
            in_stride.push_back(1);
            out_stride.push_back((my_case == Case::KEEP) ? 1 : 0);
            prev_case = my_case;
        }
    };
    auto visitor = overload
                   {
                       [&](visit_ranges_either, const auto &a) { update_plan(Case::REDUCE, a.size); },
                       [&](visit_ranges_both, const auto &a, const auto &) { update_plan(Case::KEEP, a.size); }
                   };
    auto in_dims = type.nontrivial_indexed_dimensions();
    auto out_dims = res_type.nontrivial_indexed_dimensions();
    visit_ranges(visitor, in_dims.begin(), in_dims.end(), out_dims.begin(), out_dims.end(),
                 [](const auto &a, const auto &b){ return (a.name < b.name); });
    for (size_t i = loop_cnt.size(); i-- > 0; ) {
        in_stride[i] = in_size;
        in_size *= loop_cnt[i];
        if (out_stride[i] != 0) {
            out_stride[i] = out_size;
            out_size *= loop_cnt[i];
        }
    }
    for (size_t i = 1; i < loop_cnt.size(); ++i) {
        for (size_t j = i; j > 0; --j) {
            if ((out_stride[j] == 0) && (out_stride[j - 1] > 0)) {
                std::swap(loop_cnt[j], loop_cnt[j - 1]);
                std::swap(in_stride[j], in_stride[j - 1]);
                std::swap(out_stride[j], out_stride[j - 1]);
            }
        }
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
    auto fun = typify_invoke<3,ReduceTypify,SelectGenericReduceOp>(type.cell_type(), param.res_type.cell_type(), aggr, param);
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
