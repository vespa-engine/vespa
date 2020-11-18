// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_single_reduce_function.h"
#include "dense_tensor_view.h"
#include <vespa/vespalib/util/typify.h>
#include <vespa/eval/eval/value.h>

namespace vespalib::tensor {

using eval::Aggr;
using eval::InterpretedFunction;
using eval::TensorEngine;
using eval::TensorFunction;
using eval::Value;
using eval::ValueType;
using eval::TypifyCellType;
using eval::TypifyAggr;
using eval::as;

using namespace eval::tensor_function;
using namespace eval::aggr;

namespace {

struct Params {
    const ValueType &result_type;
    size_t outer_size;
    size_t dim_size;
    size_t inner_size;
    Params(const ValueType &result_type_in, const ValueType &child_type, size_t dim_idx)
        : result_type(result_type_in), outer_size(1), dim_size(1), inner_size(1)
    {
        for (size_t i = 0; i < child_type.dimensions().size(); ++i) {
            if (i < dim_idx) {
                outer_size *= child_type.dimensions()[i].size;
            } else if (i == dim_idx) {
                dim_size *= child_type.dimensions()[i].size;
            } else {
                inner_size *= child_type.dimensions()[i].size;
            }
        }
    }
};

template <typename CT, typename AGGR>
CT reduce_cells(const CT *src, size_t dim_size, size_t stride) {
    AGGR aggr(*src);
    for (size_t i = 1; i < dim_size; ++i) {
        src += stride;
        aggr.sample(*src);
    }
    return aggr.result();
}

template <typename AGGR, typename GET>
auto reduce_cells_atleast_8(size_t n, GET &&get) {
    std::array<AGGR,8> aggrs = { AGGR{get(0)}, AGGR{get(1)}, AGGR{get(2)}, AGGR{get(3)},
                                 AGGR{get(4)}, AGGR{get(5)}, AGGR{get(6)}, AGGR{get(7)} };
    size_t i = 8;
    for (; (i + 7) < n; i += 8) {
        for (size_t j = 0; j < 8; ++j) {
            aggrs[j].sample(get(i + j));
        }
    }
    for (size_t j = 0; (i + j) < n; ++j) {
        aggrs[j].sample(get(i + j));
    }
    aggrs[0].merge(aggrs[4]);
    aggrs[1].merge(aggrs[5]);
    aggrs[2].merge(aggrs[6]);
    aggrs[3].merge(aggrs[7]);
    aggrs[0].merge(aggrs[2]);
    aggrs[1].merge(aggrs[3]);
    aggrs[0].merge(aggrs[1]);
    return aggrs[0].result();
}

template <typename CT, typename AGGR>
auto reduce_cells_atleast_8(const CT *src, size_t n) {
    return reduce_cells_atleast_8<AGGR>(n, [&](size_t idx) { return src[idx]; });
}

template <typename CT, typename AGGR>
auto reduce_cells_atleast_8(const CT *src, size_t n, size_t stride) {
    return reduce_cells_atleast_8<AGGR>(n, [&](size_t idx) { return src[idx * stride]; });
}

template <typename CT, typename AGGR, bool atleast_8, bool is_inner>
void my_single_reduce_op(InterpretedFunction::State &state, uint64_t param) {
    const auto &params = unwrap_param<Params>(param);
    const CT *src = state.peek(0).cells().typify<CT>().cbegin();
    auto dst_cells = state.stash.create_uninitialized_array<CT>(params.outer_size * params.inner_size);
    CT *dst = dst_cells.begin();
    const size_t block_size = (params.dim_size * params.inner_size);
    for (size_t outer = 0; outer < params.outer_size; ++outer) {
        for (size_t inner = 0; inner < params.inner_size; ++inner) {
            if (atleast_8) {
                if (is_inner) {
                    *dst++ = reduce_cells_atleast_8<CT, AGGR>(src + inner, params.dim_size);
                } else {
                    *dst++ = reduce_cells_atleast_8<CT, AGGR>(src + inner, params.dim_size, params.inner_size);
                }
            } else {
                *dst++ = reduce_cells<CT, AGGR>(src + inner, params.dim_size, params.inner_size);
            }
        }
        src += block_size;
    }
    state.pop_push(state.stash.create<DenseTensorView>(params.result_type, TypedCells(dst_cells)));
}

struct MyGetFun {
    template <typename R1, typename R2, typename R3, typename R4> static auto invoke() {
        using AggrType = typename R2::template templ<R1>;
        return my_single_reduce_op<R1, AggrType, R3::value, R4::value>;
    }
};

using MyTypify = TypifyValue<TypifyCellType,TypifyAggr,TypifyBool>;

} // namespace vespalib::tensor::<unnamed>

DenseSingleReduceFunction::DenseSingleReduceFunction(const ValueType &result_type,
                                                     const TensorFunction &child,
                                                     size_t dim_idx, Aggr aggr)
    : Op1(result_type, child),
      _dim_idx(dim_idx),
      _aggr(aggr)
{
}

DenseSingleReduceFunction::~DenseSingleReduceFunction() = default;

InterpretedFunction::Instruction
DenseSingleReduceFunction::compile_self(eval::EngineOrFactory, Stash &stash) const
{
    auto &params = stash.create<Params>(result_type(), child().result_type(), _dim_idx);
    auto op = typify_invoke<4,MyTypify,MyGetFun>(result_type().cell_type(), _aggr,
                                                 (params.dim_size >= 8), (params.inner_size == 1));
    return InterpretedFunction::Instruction(op, wrap_param<Params>(params));
}

const TensorFunction &
DenseSingleReduceFunction::optimize(const TensorFunction &expr, Stash &stash)
{
    auto reduce = as<Reduce>(expr);
    if (reduce && (reduce->dimensions().size() == 1) &&
        reduce->child().result_type().is_dense() &&
        expr.result_type().is_dense())
    {
        size_t dim_idx = reduce->child().result_type().dimension_index(reduce->dimensions()[0]);
        assert(dim_idx != ValueType::Dimension::npos);
        assert(expr.result_type().cell_type() == reduce->child().result_type().cell_type());
        return stash.create<DenseSingleReduceFunction>(expr.result_type(), reduce->child(), dim_idx, reduce->aggr());
    }
    return expr;
}

} // namespace vespalib::tensor
