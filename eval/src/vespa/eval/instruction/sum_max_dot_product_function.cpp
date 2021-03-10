// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sum_max_dot_product_function.h"
#include <vespa/eval/eval/operation.h>
#include <vespa/eval/eval/value.h>
#include <cblas.h>

namespace vespalib::eval {

using namespace tensor_function;
using namespace operation;

namespace {

void my_sum_max_dot_product_op(InterpretedFunction::State &state, uint64_t dp_size) {
    double result = 0.0;
    auto query_cells = state.peek(1).cells().typify<float>();
    auto document_cells = state.peek(0).cells().typify<float>();
    if ((query_cells.size() > 0) && (document_cells.size() > 0)) {
        for (const float *query = query_cells.begin(); query < query_cells.end(); query += dp_size) {
            float max_dp = aggr::Max<float>::null_value();
            for (const float *document = document_cells.begin(); document < document_cells.end(); document += dp_size) {
                max_dp = aggr::Max<float>::combine(max_dp, cblas_sdot(dp_size, query, 1, document, 1));
            }
            result += max_dp;
        }
    }
    state.pop_pop_push(state.stash.create<DoubleValue>(result));
}

const Reduce *check_reduce(const TensorFunction &expr, Aggr aggr) {
    if (auto reduce = as<Reduce>(expr)) {
        if ((reduce->aggr() == aggr) && (reduce->dimensions().size() == 1)) {
            return reduce;
        }
    }
    return nullptr;
}

const Join *check_mul(const TensorFunction &expr) {
    if (auto join = as<Join>(expr)) {
        if (join->function() == Mul::f) {
            return join;
        }
    }
    return nullptr;
}

bool check_params(const ValueType &res_type, const ValueType &query, const ValueType &document,
                  const vespalib::string &sum_dim, const vespalib::string &max_dim, const vespalib::string &dp_dim)
{
    if (res_type.is_double() &&
        (query.dimensions().size() == 2) && (query.cell_type() == CellType::FLOAT) &&
        (document.dimensions().size() == 2) && (document.cell_type() == CellType::FLOAT))
    {
        size_t npos = ValueType::Dimension::npos;
        size_t sum_idx = query.dimension_index(sum_dim);
        size_t max_idx = document.dimension_index(max_dim);
        size_t query_dp_idx = query.dimension_index(dp_dim);
        size_t document_dp_idx = document.dimension_index(dp_dim);
        if ((sum_idx != npos) && (max_idx != npos) && (query_dp_idx != npos) && (document_dp_idx != npos)) {
            if (query.dimensions()[sum_idx].is_mapped() && document.dimensions()[max_idx].is_mapped() &&
                query.dimensions()[query_dp_idx].is_indexed() && !query.dimensions()[query_dp_idx].is_trivial())
            {
                assert(query.dimensions()[query_dp_idx].size == document.dimensions()[document_dp_idx].size);
                return true;
            }
        }
    }
    return false;
}

size_t get_dim_size(const ValueType &type, const vespalib::string &dim) {
    size_t npos = ValueType::Dimension::npos;
    size_t idx = type.dimension_index(dim);
    assert(idx != npos);
    assert(type.dimensions()[idx].is_indexed());
    assert(!type.dimensions()[idx].is_trivial());
    return type.dimensions()[idx].size;
}

} // namespace <unnamed>

SumMaxDotProductFunction::SumMaxDotProductFunction(const ValueType &res_type_in,
                                                   const TensorFunction &query,
                                                   const TensorFunction &document,
                                                   size_t dp_size)
    : tensor_function::Op2(res_type_in, query, document),
      _dp_size(dp_size)
{
}

InterpretedFunction::Instruction
SumMaxDotProductFunction::compile_self(const ValueBuilderFactory &, Stash &) const
{
    return InterpretedFunction::Instruction(my_sum_max_dot_product_op, _dp_size);
}

const TensorFunction &
SumMaxDotProductFunction::optimize(const TensorFunction &expr, Stash &stash)
{
    if (auto sum_reduce = check_reduce(expr, Aggr::SUM)) {
        if (auto max_reduce = check_reduce(sum_reduce->child(), Aggr::MAX)) {
            if (auto dp_sum = check_reduce(max_reduce->child(), Aggr::SUM)) {
                if (auto dp_mul = check_mul(dp_sum->child())) {
                    const auto &sum_dim = sum_reduce->dimensions()[0];
                    const auto &max_dim = max_reduce->dimensions()[0];
                    const auto &dp_dim = dp_sum->dimensions()[0];
                    const TensorFunction &lhs = dp_mul->lhs();
                    const TensorFunction &rhs = dp_mul->rhs();
                    if (check_params(expr.result_type(), lhs.result_type(), rhs.result_type(),
                                     sum_dim, max_dim, dp_dim))
                    {
                        size_t dp_size = get_dim_size(lhs.result_type(), dp_dim);
                        return stash.create<SumMaxDotProductFunction>(expr.result_type(), lhs, rhs, dp_size);
                    }
                    if (check_params(expr.result_type(), rhs.result_type(), lhs.result_type(),
                                     sum_dim, max_dim, dp_dim))
                    {
                        size_t dp_size = get_dim_size(rhs.result_type(), dp_dim);
                        return stash.create<SumMaxDotProductFunction>(expr.result_type(), rhs, lhs, dp_size);
                    }
                }
            }
        }
    }
    return expr;
}

} // namespace
