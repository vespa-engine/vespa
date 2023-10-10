// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "universal_dot_product.h"
#include "sparse_join_reduce_plan.h"
#include "dense_join_reduce_plan.h"
#include <vespa/eval/eval/inline_operation.h>
#include <vespa/eval/eval/fast_value.hpp>

namespace vespalib::eval {

using namespace tensor_function;
using namespace instruction;
using namespace operation;

namespace {

struct UniversalDotProductParam {
    ValueType            res_type;
    SparseJoinReducePlan sparse_plan;
    DenseJoinReducePlan  dense_plan;
    size_t               vector_size;

    UniversalDotProductParam(const ValueType &res_type_in,
                             const ValueType &lhs_type,
                             const ValueType &rhs_type)
      : res_type(res_type_in),
        sparse_plan(lhs_type, rhs_type, res_type),
        dense_plan(lhs_type, rhs_type, res_type),
        vector_size(1)
    {
        if (!dense_plan.loop_cnt.empty() &&
            dense_plan.lhs_stride.back() == 1 &&
            dense_plan.rhs_stride.back() == 1 &&
            dense_plan.res_stride.back() == 0)
        {
            vector_size = dense_plan.loop_cnt.back();
            dense_plan.loop_cnt.pop_back();
            dense_plan.lhs_stride.pop_back();
            dense_plan.rhs_stride.pop_back();
            dense_plan.res_stride.pop_back();
        }
    }
    bool forward() const { return sparse_plan.maybe_forward_lhs_index(); }
    bool distinct() const { return sparse_plan.is_distinct() && dense_plan.is_distinct(); }
    bool single() const { return vector_size == 1; }
};

template <typename OCT>
const Value &create_empty_result(const UniversalDotProductParam &param, Stash &stash) {
    if (param.sparse_plan.res_dims() == 0) {
        auto zero_cells = stash.create_array<OCT>(param.dense_plan.res_size);
        return stash.create<ValueView>(param.res_type, TrivialIndex::get(), TypedCells(zero_cells));
    } else {
        return stash.create<ValueView>(param.res_type, EmptyIndex::get(), TypedCells(nullptr, get_cell_type<OCT>(), 0));
    }
}

template <typename LCT, typename RCT, bool single> struct MyDotProduct;
template <typename LCT, typename RCT> struct MyDotProduct<LCT, RCT, false> {
    size_t vector_size;
    MyDotProduct(size_t vector_size_in) : vector_size(vector_size_in) {}
    auto operator()(const LCT *lhs, const RCT *rhs) const {
        return DotProduct<LCT,RCT>::apply(lhs, rhs, vector_size);
    }
};
template <typename LCT, typename RCT> struct MyDotProduct<LCT, RCT, true> {
    MyDotProduct(size_t) {}
    auto operator()(const LCT *lhs, const RCT *rhs) const {
        return (*lhs) * (*rhs);
    }
};

template <typename LCT, typename RCT, typename OCT, bool distinct, bool single>
struct DenseFun {
    [[no_unique_address]] MyDotProduct<LCT,RCT,single> dot_product;
    const LCT *lhs;
    const RCT *rhs;
    mutable OCT *dst;
    DenseFun(size_t vector_size_in, const Value &lhs_in, const Value &rhs_in)
      : dot_product(vector_size_in),
        lhs(lhs_in.cells().typify<LCT>().data()),
        rhs(rhs_in.cells().typify<RCT>().data()) {}
    void operator()(size_t lhs_idx, size_t rhs_idx) const requires distinct {
        *dst++ = dot_product(lhs + lhs_idx, rhs + rhs_idx);
    }
    void operator()(size_t lhs_idx, size_t rhs_idx, size_t dst_idx) const requires (!distinct) {
        dst[dst_idx] += dot_product(lhs + lhs_idx, rhs + rhs_idx);
    }
};

template <typename OCT, bool forward> struct Result {};
template <typename OCT> struct Result<OCT, false> {
    mutable FastValue<OCT,true> *fast;
};

template <typename LCT, typename RCT, typename OCT, bool forward, bool distinct, bool single>
struct SparseFun {
    const UniversalDotProductParam &param;
    DenseFun<LCT,RCT,OCT,distinct,single> dense_fun;
    [[no_unique_address]] Result<OCT, forward> result;
    SparseFun(uint64_t param_in, const Value &lhs_in, const Value &rhs_in)
      : param(unwrap_param<UniversalDotProductParam>(param_in)),
        dense_fun(param.vector_size, lhs_in, rhs_in),
        result() {}
    void operator()(size_t lhs_subspace, size_t rhs_subspace, ConstArrayRef<string_id> res_addr) const requires (!forward && !distinct) {
        auto [space, first] = result.fast->insert_subspace(res_addr);
        if (first) {
            std::fill(space.begin(), space.end(), OCT{});
        }
        dense_fun.dst = space.data();
        param.dense_plan.execute(lhs_subspace * param.dense_plan.lhs_size,
                                 rhs_subspace * param.dense_plan.rhs_size,
                                 0, dense_fun);
    };
    void operator()(size_t lhs_subspace, size_t rhs_subspace, ConstArrayRef<string_id> res_addr) const requires (!forward && distinct) {
        dense_fun.dst = result.fast->add_subspace(res_addr).data();
        param.dense_plan.execute_distinct(lhs_subspace * param.dense_plan.lhs_size,
                                          rhs_subspace * param.dense_plan.rhs_size,
                                          dense_fun);
    };
    void operator()(size_t lhs_subspace, size_t rhs_subspace) const requires (forward && !distinct) {
        param.dense_plan.execute(lhs_subspace * param.dense_plan.lhs_size,
                                 rhs_subspace * param.dense_plan.rhs_size,
                                 lhs_subspace * param.dense_plan.res_size, dense_fun);
    };
    void operator()(size_t lhs_subspace, size_t rhs_subspace) const requires (forward && distinct) {
        param.dense_plan.execute_distinct(lhs_subspace * param.dense_plan.lhs_size,
                                          rhs_subspace * param.dense_plan.rhs_size, dense_fun);
    };
    const Value &calculate_result(const Value::Index &lhs, const Value::Index &rhs, Stash &stash) const requires (!forward) {
        auto &stored_result = stash.create<std::unique_ptr<FastValue<OCT,true>>>(
            std::make_unique<FastValue<OCT,true>>(param.res_type, param.sparse_plan.res_dims(), param.dense_plan.res_size,
                                                  param.sparse_plan.estimate_result_size(lhs, rhs)));
        result.fast = stored_result.get();
        param.sparse_plan.execute(lhs, rhs, *this);
        if (result.fast->my_index.map.size() == 0 && param.sparse_plan.res_dims() == 0) {
            auto empty = result.fast->add_subspace(ConstArrayRef<string_id>());
            std::fill(empty.begin(), empty.end(), OCT{});
        }
        return *(result.fast);
    }
    const Value &calculate_result(const Value::Index &lhs, const Value::Index &rhs, Stash &stash) const requires forward {
        size_t lhs_size = lhs.size();
        size_t rhs_size = rhs.size();
        if (lhs_size == 0 || rhs_size == 0) {
            return create_empty_result<OCT>(param, stash);
        }
        auto dst_cells = (distinct)
            ? stash.create_uninitialized_array<OCT>(lhs_size * param.dense_plan.res_size)
            : stash.create_array<OCT>(lhs_size * param.dense_plan.res_size);
        dense_fun.dst = dst_cells.data();
        for (size_t lhs_idx = 0; lhs_idx < lhs_size; ++lhs_idx) {
            for (size_t rhs_idx = 0; rhs_idx < rhs_size; ++rhs_idx) {
                (*this)(lhs_idx, rhs_idx);
            }
        }
        return stash.create<ValueView>(param.res_type, lhs, TypedCells(dst_cells));
    }
};

template <typename LCT, typename RCT, typename OCT, bool forward, bool distinct, bool single>
void my_universal_dot_product_op(InterpretedFunction::State &state, uint64_t param_in) {
    SparseFun<LCT,RCT,OCT,forward,distinct,single> sparse_fun(param_in, state.peek(1), state.peek(0));
    state.pop_pop_push(sparse_fun.calculate_result(state.peek(1).index(), state.peek(0).index(), state.stash));
}

struct SelectUniversalDotProduct {
    template <typename LCM, typename RCM, typename SCALAR, typename FORWARD, typename DISTINCT, typename SINGLE>
    static auto invoke() {
        constexpr CellMeta ocm = CellMeta::join(LCM::value, RCM::value).reduce(SCALAR::value);
        using LCT = CellValueType<LCM::value.cell_type>;
        using RCT = CellValueType<RCM::value.cell_type>;
        using OCT = CellValueType<ocm.cell_type>;
        if constexpr ((std::same_as<LCT,float> && std::same_as<RCT,float>) ||
                      (std::same_as<LCT,double> && std::same_as<RCT,double>))
        {
            return my_universal_dot_product_op<LCT,RCT,OCT,FORWARD::value,DISTINCT::value,SINGLE::value>;
        }
        return my_universal_dot_product_op<LCT,RCT,OCT,FORWARD::value,false,false>;
    }
};

bool check_types(const ValueType &lhs, const ValueType &rhs) {
    if (lhs.is_double() || rhs.is_double()) {
        return false;
    }
    if (lhs.count_mapped_dimensions() > 0 && rhs.count_mapped_dimensions() > 0) {
        return true;
    }
    return false;
}

} // namespace <unnamed>

UniversalDotProduct::UniversalDotProduct(const ValueType &res_type_in,
                                         const TensorFunction &lhs_in,
                                         const TensorFunction &rhs_in)
  : tensor_function::Op2(res_type_in, lhs_in, rhs_in)
{
}

InterpretedFunction::Instruction
UniversalDotProduct::compile_self(const ValueBuilderFactory &, Stash &stash) const
{
    auto &param = stash.create<UniversalDotProductParam>(result_type(), lhs().result_type(), rhs().result_type());
    using MyTypify = TypifyValue<TypifyCellMeta,TypifyBool>;
    auto op = typify_invoke<6,MyTypify,SelectUniversalDotProduct>(lhs().result_type().cell_meta(),
                                                                  rhs().result_type().cell_meta(),
                                                                  result_type().cell_meta().is_scalar,
                                                                  param.forward(),
                                                                  param.distinct(),
                                                                  param.single());
    return InterpretedFunction::Instruction(op, wrap_param<UniversalDotProductParam>(param));
}

bool
UniversalDotProduct::forward() const
{
    UniversalDotProductParam param(result_type(), lhs().result_type(), rhs().result_type());
    return param.forward();
}

bool
UniversalDotProduct::distinct() const
{
    UniversalDotProductParam param(result_type(), lhs().result_type(), rhs().result_type());
    return param.distinct();
}

bool
UniversalDotProduct::single() const
{
    UniversalDotProductParam param(result_type(), lhs().result_type(), rhs().result_type());
    return param.single();
}

const TensorFunction &
UniversalDotProduct::optimize(const TensorFunction &expr, Stash &stash, bool force)
{
    if (auto reduce = as<Reduce>(expr); reduce && (reduce->aggr() == Aggr::SUM)) {
        if (auto join = as<Join>(reduce->child()); join && (join->function() == Mul::f)) {
            const ValueType &res_type = expr.result_type();
            const ValueType &lhs_type = join->lhs().result_type();
            const ValueType &rhs_type = join->rhs().result_type();
            if (force || check_types(lhs_type, rhs_type)) {
                SparseJoinReducePlan sparse_plan(lhs_type, rhs_type, res_type);
                if (sparse_plan.maybe_forward_rhs_index() && !sparse_plan.maybe_forward_lhs_index()) {
                    return stash.create<UniversalDotProduct>(res_type, join->rhs(), join->lhs());
                }
                return stash.create<UniversalDotProduct>(res_type, join->lhs(), join->rhs());
            }
        }
    }
    return expr;
}

} // namespace
