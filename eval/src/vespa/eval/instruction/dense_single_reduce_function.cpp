// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_single_reduce_function.h"
#include <vespa/vespalib/util/typify.h>
#include <vespa/eval/eval/value.h>
#include <cassert>

namespace vespalib::eval {

using namespace tensor_function;
using namespace aggr;

namespace {

struct Params {
    const ValueType &result_type;
    size_t outer_size;
    size_t reduce_size;
    size_t inner_size;
    Params(const ValueType &result_type_in, size_t outer_size_in, size_t reduce_size_in, size_t inner_size_in)
        : result_type(result_type_in), outer_size(outer_size_in), reduce_size(reduce_size_in), inner_size(inner_size_in) {}
};

template <typename ICT, typename AGGR>
auto reduce_cells(const ICT *src, size_t reduce_size, size_t stride) {
    AGGR aggr(*src);
    for (size_t i = 1; i < reduce_size; ++i) {
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

template <typename ICT, typename AGGR>
auto reduce_cells_atleast_8(const ICT *src, size_t n) {
    return reduce_cells_atleast_8<AGGR>(n, [&](size_t idx) { return src[idx]; });
}

template <typename ICT, typename AGGR>
auto reduce_cells_atleast_8(const ICT *src, size_t n, size_t stride) {
    return reduce_cells_atleast_8<AGGR>(n, [&](size_t idx) { return src[idx * stride]; });
}

template <typename ICT, typename OCT, typename AGGR, bool atleast_8, bool is_inner>
void trace_reduce_impl(const Params &params, const ICT *src, OCT *dst) {
    constexpr bool aggr_is_complex = is_complex(AGGR::enum_value());
    const size_t block_size = (params.reduce_size * params.inner_size);
    for (size_t outer = 0; outer < params.outer_size; ++outer) {
        for (size_t inner = 0; inner < params.inner_size; ++inner) {
            if (atleast_8 && !aggr_is_complex) {
                if (is_inner) {
                    *dst++ = reduce_cells_atleast_8<ICT, AGGR>(src + inner, params.reduce_size);
                } else {
                    *dst++ = reduce_cells_atleast_8<ICT, AGGR>(src + inner, params.reduce_size, params.inner_size);
                }
            } else {
                *dst++ = reduce_cells<ICT, AGGR>(src + inner, params.reduce_size, params.inner_size);
            }
        }
        src += block_size;
    }
}

template <typename ICT, typename OCT, typename AGGR>
void fold_reduce_impl(const Params &params, const ICT *src, OCT *dst) {
    for (size_t outer = 0; outer < params.outer_size; ++outer) {
        auto saved_dst = dst;
        for (size_t inner = 0; inner < params.inner_size; ++inner) {
            *dst++ = *src++;
        }
        for (size_t dim = 1; dim < params.reduce_size; ++dim) {
            dst = saved_dst;
            for (size_t inner = 0; inner < params.inner_size; ++inner) {
                *dst = AGGR::combine(*dst, *src++);
                ++dst;
            }
        }
    }
}

template <typename ICT, typename OCT, typename AGGR, bool atleast_8, bool is_inner>
void my_single_reduce_op(InterpretedFunction::State &state, uint64_t param) {
    static_assert(std::is_same_v<OCT,typename AGGR::value_type>);
    constexpr bool aggr_is_simple = is_simple(AGGR::enum_value());
    const auto &params = unwrap_param<Params>(param);
    const ICT *src = state.peek(0).cells().typify<ICT>().cbegin();
    auto dst_cells = state.stash.create_uninitialized_array<OCT>(params.outer_size * params.inner_size);
    OCT *dst = dst_cells.begin();
    if constexpr (aggr_is_simple && !is_inner) {
        fold_reduce_impl<ICT, OCT, AGGR>(params, src, dst);
    } else {
        trace_reduce_impl<ICT, OCT, AGGR, atleast_8, is_inner>(params, src, dst);
    }
    state.pop_push(state.stash.create<DenseValueView>(params.result_type, TypedCells(dst_cells)));
}

struct MyGetFun {
    template <typename ICM, typename AGGR, typename GE8, typename I1> static auto invoke() {
        using ICT = CellValueType<ICM::value.cell_type>;
        using OCT = CellValueType<ICM::value.reduce(false).cell_type>;
        using AggrType = typename AGGR::template templ<OCT>;
        return my_single_reduce_op<ICT, OCT, AggrType, GE8::value, I1::value>;
    }
};

using MyTypify = TypifyValue<TypifyCellMeta,TypifyAggr,TypifyBool>;

std::pair<std::vector<vespalib::string>,ValueType> sort_and_drop_trivial(const std::vector<vespalib::string> &list_in, const ValueType &type_in) {
    std::vector<vespalib::string> dropped;
    std::vector<vespalib::string> list_out;
    for (const auto &dim_name: list_in) {
        auto dim_idx = type_in.dimension_index(dim_name);
        assert(dim_idx != ValueType::Dimension::npos);
        const auto &dim = type_in.dimensions()[dim_idx];
        assert(dim.is_indexed());
        if (dim.is_trivial()) {
            dropped.push_back(dim_name);
        } else {
            list_out.push_back(dim_name);
        }
    }
    std::sort(list_out.begin(), list_out.end());
    ValueType type_out = dropped.empty() ? type_in : type_in.reduce(dropped);
    assert(!type_out.is_error());
    return {list_out, type_out};
}

template <typename T> struct VectorLookupLoop {
    const std::vector<T> &list;
    size_t index;
    VectorLookupLoop(const std::vector<T> &list_in) : list(list_in), index(0) {}
    bool valid() const { return (index < list.size()); }
    void next() { ++index; }
    const T &get() const { return list[index]; }
};

DenseSingleReduceSpec extract_next(const ValueType &type, Aggr aggr,
                                   std::vector<vespalib::string> &todo)
{
    size_t outer_size = 1;
    size_t reduce_size = 1;
    size_t inner_size = 1;
    auto dims = type.nontrivial_indexed_dimensions();
    std::vector<vespalib::string> do_now;
    std::vector<vespalib::string> do_later;
    auto a = VectorLookupLoop(dims);
    auto b = VectorLookupLoop(todo);
    while (a.valid() && b.valid() && (a.get().name < b.get())) {
        outer_size *= a.get().size;
        a.next();
    }
    while (a.valid() && b.valid() && (a.get().name == b.get())) {
        reduce_size *= a.get().size;
        do_now.push_back(b.get());
        a.next();
        b.next();
    }
    while (a.valid()) {
        inner_size *= a.get().size;
        a.next();
    }
    while (b.valid()) {
        do_later.push_back(b.get());
        b.next();
    }
    todo = do_later;
    assert(!do_now.empty());
    return {type.reduce(do_now), outer_size, reduce_size, inner_size, aggr};
}

} // namespace vespalib::eval::<unnamed>

std::vector<DenseSingleReduceSpec>
make_dense_single_reduce_list(const ValueType &type, Aggr aggr,
                              const std::vector<vespalib::string> &reduce_dims)
{
    auto res_type = type.reduce(reduce_dims);
    if (reduce_dims.empty() || !type.is_dense() || !res_type.is_dense()) {
        return {};
    }
    std::vector<DenseSingleReduceSpec> list;
    auto [todo, curr_type] = sort_and_drop_trivial(reduce_dims, type);
    while (!todo.empty()) {
        list.push_back(extract_next(curr_type, aggr, todo));
        curr_type = list.back().result_type;
    }
    assert(curr_type == res_type);
    if ((list.size() > 1) && !aggr::is_simple(aggr)) {
        return {};
    }
    return list;
}

DenseSingleReduceFunction::DenseSingleReduceFunction(const DenseSingleReduceSpec &spec,
                                                     const TensorFunction &child)
    : Op1(spec.result_type, child),
      _outer_size(spec.outer_size),
      _reduce_size(spec.reduce_size),
      _inner_size(spec.inner_size),
      _aggr(spec.aggr)
{
    assert(result_type().cell_meta().is_scalar == false);
}

DenseSingleReduceFunction::~DenseSingleReduceFunction() = default;

InterpretedFunction::Instruction
DenseSingleReduceFunction::compile_self(const ValueBuilderFactory &, Stash &stash) const
{
    auto op = typify_invoke<4,MyTypify,MyGetFun>(child().result_type().cell_meta().not_scalar(),
                                                 _aggr,
                                                 (_reduce_size >= 8), (_inner_size == 1));
    auto &params = stash.create<Params>(result_type(), _outer_size, _reduce_size, _inner_size);
    return InterpretedFunction::Instruction(op, wrap_param<Params>(params));
}

const TensorFunction &
DenseSingleReduceFunction::optimize(const TensorFunction &expr, Stash &stash)
{
    if (auto reduce = as<Reduce>(expr)) {
        const auto &child = reduce->child();
        auto spec_list = make_dense_single_reduce_list(child.result_type(), reduce->aggr(), reduce->dimensions()); 
        if (!spec_list.empty()) {
            const auto *prev = &child;
            for (const auto &spec: spec_list) {
                prev = &stash.create<DenseSingleReduceFunction>(spec, *prev);
            }
            return *prev;
        }
    }
    return expr;
}

} // namespace vespalib::eval
