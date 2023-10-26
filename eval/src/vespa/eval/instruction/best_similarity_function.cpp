// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "best_similarity_function.h"
#include <vespa/eval/eval/inline_operation.h>
#include <vespa/eval/eval/value.h>
#include <vespa/vespalib/util/binary_hamming_distance.h>

namespace vespalib::eval {

using namespace tensor_function;
using namespace operation;

namespace {

struct BestSimParam {
    ValueType res_type;
    size_t inner_size;
    BestSimParam(const ValueType &res_type_in, size_t inner_size_in)
      : res_type(res_type_in), inner_size(inner_size_in) {}
};

struct UseDotProduct {
    static float calc(const float *pri, const float *sec, size_t size) {
        return DotProduct<float,float>::apply(pri, sec, size);
    }
};

struct UseHammingDist {
    static float calc(const Int8Float *pri, const Int8Float *sec, size_t size) {
        return binary_hamming_distance(pri, sec, size);
    }
};

template <typename CT, typename AGGR, typename DIST>
float best_similarity(const CT *pri, ConstArrayRef<CT> sec_cells, size_t inner_size) {
    AGGR aggr;
    for (const CT *sec = sec_cells.begin(); sec < sec_cells.end(); sec += inner_size) {
        aggr.sample(DIST::calc(pri, sec, inner_size));
    }
    return aggr.result();
}

template <bool is_double>
const Value &create_empty_result(const ValueType &type, Stash &stash) {
    if (is_double) {
        return stash.create<DoubleValue>(0.0);
    } else if (type.count_mapped_dimensions() == 0) {
        auto zero_cells = stash.create_array<float>(type.dense_subspace_size());
        return stash.create<ValueView>(type, TrivialIndex::get(), TypedCells(zero_cells));
    } else {
        return stash.create<ValueView>(type, EmptyIndex::get(), TypedCells(nullptr, CellType::FLOAT, 0));
    }
}

template <bool is_double, typename CT, typename AGGR, typename DIST>
void my_best_similarity_op(InterpretedFunction::State &state, uint64_t param) {
    size_t inner_size = is_double ? param : unwrap_param<BestSimParam>(param).inner_size;
    const ValueType &res_type = is_double ? DoubleValue::shared_type() : unwrap_param<BestSimParam>(param).res_type;
    const Value &pri_value = state.peek(1);
    auto pri_cells = pri_value.cells().typify<CT>();
    auto sec_cells = state.peek(0).cells().typify<CT>();
    if ((pri_cells.size() == 0) || (sec_cells.size() == 0)) {
        return state.pop_pop_push(create_empty_result<is_double>(res_type, state.stash));
    }
    if (is_double) {
        auto best_sim = best_similarity<CT, AGGR, DIST>(pri_cells.begin(), sec_cells, inner_size);
        return state.pop_pop_push(state.stash.create<DoubleValue>(best_sim));
    }
    auto out_cells = state.stash.create_uninitialized_array<float>(pri_cells.size() / inner_size);
    const CT *pri = pri_cells.begin();
    for (auto &out: out_cells) {
        out = best_similarity<CT, AGGR, DIST>(pri, sec_cells, inner_size);
        pri += inner_size;
    }
    Value &result_ref = state.stash.create<ValueView>(res_type, pri_value.index(), TypedCells(out_cells));
    state.pop_pop_push(result_ref);
}

//-----------------------------------------------------------------------------

size_t stride(const ValueType &type, const vespalib::string &name) {
    size_t stride = 0;
    for (const auto &dim: type.dimensions()) {
        if (dim.is_indexed()) {
            if (dim.name == name) {
                stride = 1;
            } else {
                stride *= dim.size;
            }
        }
    }
    return stride;
}

bool check_dims(const ValueType &pri, const ValueType &sec,
                const vespalib::string &best, const vespalib::string &inner)
{
    if ((stride(pri, inner) != 1) || (stride(sec, inner) != 1)) {
        return false;
    }
    if (pri.dimension_index(best) != ValueType::Dimension::npos) {
        return false;
    }
    if (sec.dimension_index(best) == ValueType::Dimension::npos) {
        return false;
    }
    for (auto &&type = sec.reduce({inner,best}); auto &&dim: type.dimensions()) {
        if (!dim.is_trivial()) {
            return false;
        }
    }
    return true;
}

size_t get_dim_size(const ValueType &type, const vespalib::string &dim) {
    size_t npos = ValueType::Dimension::npos;
    size_t idx = type.dimension_index(dim);
    assert(idx != npos);
    assert(type.dimensions()[idx].is_indexed());
    return type.dimensions()[idx].size;
}

const Reduce *check_reduce(const TensorFunction &expr, std::initializer_list<Aggr> allow) {
    if (auto reduce = as<Reduce>(expr)) {
        if (reduce->dimensions().size() == 1) {
            if (std::find(allow.begin(), allow.end(), reduce->aggr()) != allow.end()) {
                return reduce;
            }
        }
    }
    return nullptr;
}

const Join *check_join(const TensorFunction &expr, std::initializer_list<op2_t> allow) {
    if (auto join = as<Join>(expr)) {
        if (std::find(allow.begin(), allow.end(), join->function()) != allow.end()) {
            return join;
        }
    }
    return nullptr;
}

struct SelectFun {
    const ValueType &res_type;
    const ValueType &lhs_type;
    const ValueType &rhs_type;
    template <typename ResType, typename LhsType, typename RhsType>
    SelectFun(const ResType &res, const LhsType &lhs, const RhsType &rhs)
      : res_type(res.result_type()), lhs_type(lhs.result_type()), rhs_type(rhs.result_type()) {}
    template <typename R1> static InterpretedFunction::op_function invoke(Aggr best_aggr, op2_t join_fun, CellType cell_types) {
        if ((best_aggr == Aggr::MAX) && (join_fun == Mul::f) && (cell_types == CellType::FLOAT)) {
            return my_best_similarity_op<R1::value, float, aggr::Max<float>, UseDotProduct>;
        }
        if ((best_aggr == Aggr::MIN) && (join_fun == Hamming::f) && (cell_types == CellType::INT8)) {
            return my_best_similarity_op<R1::value, Int8Float, aggr::Min<float>, UseHammingDist>;
        }
        return nullptr;
    }
    InterpretedFunction::op_function operator()(Aggr best_aggr, op2_t join_fun) {
        static_assert(std::is_same_v<float, CellValueType<CellType::FLOAT>>);
        static_assert(std::is_same_v<Int8Float, CellValueType<CellType::INT8>>);
        if (lhs_type.cell_type() != rhs_type.cell_type()) {
            return nullptr;
        }
        return typify_invoke<1,TypifyBool,SelectFun>(res_type.is_double(), best_aggr, join_fun, lhs_type.cell_type());
    }
};

} // namespace <unnamed>

uint64_t
BestSimilarityFunction::make_param(Stash &stash) const
{
    if (result_type().is_double()) {
        return _inner_size;
    }
    return wrap_param<BestSimParam>(stash.create<BestSimParam>(result_type(), _inner_size));
}

BestSimilarityFunction::BestSimilarityFunction(const ValueType &res_type_in,
                                               const TensorFunction &pri,
                                               const TensorFunction &sec,
                                               InterpretedFunction::op_function my_fun,
                                               size_t inner_size)
  : tensor_function::Op2(res_type_in, pri, sec),
    _my_fun(my_fun),
    _inner_size(inner_size)
{
}

InterpretedFunction::Instruction
BestSimilarityFunction::compile_self(const ValueBuilderFactory &, Stash &stash) const
{
    return InterpretedFunction::Instruction(_my_fun, make_param(stash));
}

const TensorFunction &
BestSimilarityFunction::optimize(const TensorFunction &expr, Stash &stash)
{
    if (auto best_reduce = check_reduce(expr, {Aggr::MAX, Aggr::MIN})) {
        if (auto sum_reduce = check_reduce(best_reduce->child(), {Aggr::SUM})) {
            if (auto join = check_join(sum_reduce->child(), {Mul::f, Hamming::f})) {
                SelectFun select_fun(expr, join->lhs(), join->rhs());
                if (auto my_fun = select_fun(best_reduce->aggr(), join->function())) {
                    const auto &best_dim = best_reduce->dimensions()[0];
                    const auto &inner_dim = sum_reduce->dimensions()[0];
                    const TensorFunction &lhs = join->lhs();
                    const TensorFunction &rhs = join->rhs();
                    if (check_dims(lhs.result_type(), rhs.result_type(), best_dim, inner_dim)) {
                        size_t inner_size = get_dim_size(lhs.result_type(), inner_dim);
                        return stash.create<BestSimilarityFunction>(expr.result_type(), lhs, rhs, my_fun, inner_size);
                    }
                    if (check_dims(rhs.result_type(), lhs.result_type(), best_dim, inner_dim)) {
                        size_t inner_size = get_dim_size(rhs.result_type(), inner_dim);
                        return stash.create<BestSimilarityFunction>(expr.result_type(), rhs, lhs, my_fun, inner_size);
                    }
                }
            }
        }
    }
    return expr;
}

} // namespace
