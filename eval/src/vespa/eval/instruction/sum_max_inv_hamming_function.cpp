// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sum_max_inv_hamming_function.h"
#include <vespa/eval/eval/value.h>
#include <vespa/vespalib/util/binary_hamming_distance.h>

namespace vespalib::eval {

using namespace tensor_function;
using namespace operation;

namespace {

void my_sum_max_inv_hamming_op(InterpretedFunction::State &state, uint64_t vec_size) {
    double result = 0.0;
    auto query_cells = state.peek(1).cells().unsafe_typify<int8_t>();
    auto document_cells = state.peek(0).cells().unsafe_typify<int8_t>();
    if ((query_cells.size() > 0) && (document_cells.size() > 0)) {
        for (const int8_t *query = query_cells.data(); query < query_cells.data() + query_cells.size(); query += vec_size) {
            float max_inv_hamming = aggr::Max<float>::null_value();
            for (const int8_t *document = document_cells.data(); document < document_cells.data() + document_cells.size(); document += vec_size) {
                float my_inv_hamming = 1.0f / (1.0f + binary_hamming_distance(query, document, vec_size));
                max_inv_hamming = aggr::Max<float>::combine(max_inv_hamming, my_inv_hamming);
            }
            result += max_inv_hamming;
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

const Join *check_join(const TensorFunction &expr, op2_t op) {
    if (auto join = as<Join>(expr)) {
        if (join->function() == op) {
            return join;
        }
    }
    return nullptr;
}

bool is_one(const TensorFunction &expr) {
    if (expr.result_type().is_double()) {
        if (auto const_value = as<ConstValue>(expr)) {
            return (const_value->value().as_double() == 1.0);
        }
    }
    return false;
}

// 1/(1+x) -> x
// 1/(x+1) -> x
const TensorFunction *check_inv(const TensorFunction &expr) {
    if (auto div = check_join(expr, Div::f)) {
        if (is_one(div->lhs())) {
            if (auto add = check_join(div->rhs(), Add::f)) {
                if (is_one(add->lhs())) {
                    return &add->rhs();
                }
                if (is_one(add->rhs())) {
                    return &add->lhs();
                }
            }
        }
    }
    return nullptr;
}

bool check_params(const ValueType &res_type, const ValueType &query, const ValueType &document,
                  const std::string &sum_dim, const std::string &max_dim, const std::string &ham_dim)
{
    return (res_type.is_double() &&
            (query.dimensions().size() == 2) && (query.cell_type() == CellType::INT8) &&
            (document.dimensions().size() == 2) && (document.cell_type() == CellType::INT8) &&
            query.has_dimension(sum_dim) && (query.stride_of(ham_dim) == 1) &&
            document.has_dimension(max_dim) && (document.stride_of(ham_dim) == 1));
}

size_t get_dim_size(const ValueType &type, const std::string &dim) {
    size_t npos = ValueType::Dimension::npos;
    size_t idx = type.dimension_index(dim);
    assert(idx != npos);
    return type.dimensions()[idx].size;
}

} // namespace <unnamed>

SumMaxInvHammingFunction::SumMaxInvHammingFunction(const ValueType &res_type_in,
                                                   const TensorFunction &query,
                                                   const TensorFunction &document,
                                                   size_t vec_size)
    : tensor_function::Op2(res_type_in, query, document),
      _vec_size(vec_size)
{
}

InterpretedFunction::Instruction
SumMaxInvHammingFunction::compile_self(const ValueBuilderFactory &, Stash &) const
{
    return InterpretedFunction::Instruction(my_sum_max_inv_hamming_op, _vec_size);
}

const TensorFunction &
SumMaxInvHammingFunction::optimize(const TensorFunction &expr, Stash &stash)
{
    if (auto sum_reduce = check_reduce(expr, Aggr::SUM)) {
        if (auto max_reduce = check_reduce(sum_reduce->child(), Aggr::MAX)) {
            if (auto inverted = check_inv(max_reduce->child())) {
                if (auto ham_reduce = check_reduce(*inverted, Aggr::SUM)) {
                    if (auto ham = check_join(ham_reduce->child(), Hamming::f)) {
                        const auto &sum_dim = sum_reduce->dimensions()[0];
                        const auto &max_dim = max_reduce->dimensions()[0];
                        const auto &ham_dim = ham_reduce->dimensions()[0];
                        if (check_params(expr.result_type(), ham->lhs().result_type(), ham->rhs().result_type(),
                                         sum_dim, max_dim, ham_dim))
                        {
                            size_t vec_size = get_dim_size(ham->lhs().result_type(), ham_dim);
                            return stash.create<SumMaxInvHammingFunction>(expr.result_type(), ham->lhs(), ham->rhs(), vec_size);
                        }
                        if (check_params(expr.result_type(), ham->rhs().result_type(), ham->lhs().result_type(),
                                         sum_dim, max_dim, ham_dim))
                        {
                            size_t vec_size = get_dim_size(ham->rhs().result_type(), ham_dim);
                            return stash.create<SumMaxInvHammingFunction>(expr.result_type(), ham->rhs(), ham->lhs(), vec_size);
                        }
                    }
                }
            }
        }
    }
    return expr;
}

} // namespace
