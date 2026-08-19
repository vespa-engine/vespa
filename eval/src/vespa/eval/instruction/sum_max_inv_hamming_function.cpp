// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sum_max_inv_hamming_function.h"

#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/wrap_param.h>
#include <vespa/vespalib/hwaccelerated/functions.h>

#include <algorithm>
#include <array>
#include <unordered_map>
#include <vector>

namespace vespalib::eval {

using namespace tensor_function;
using namespace operation;

namespace {

void my_sum_max_inv_hamming_op(InterpretedFunction::State& state, uint64_t vec_size) {
    double result = 0.0;
    auto   query_cells = state.peek(1).cells().unsafe_typify<int8_t>();
    auto   document_cells = state.peek(0).cells().unsafe_typify<int8_t>();
    if ((query_cells.size() > 0) && (document_cells.size() > 0)) {
        for (const int8_t* query = query_cells.data(); query < query_cells.data() + query_cells.size();
             query += vec_size)
        {
            float max_inv_hamming = aggr::Max<float>::null_value();
            for (const int8_t* document = document_cells.data();
                 document < document_cells.data() + document_cells.size(); document += vec_size)
            {
                float my_inv_hamming =
                    1.0f / (1.0f + hwaccelerated::binary_hamming_distance(query, document, vec_size));
                max_inv_hamming = aggr::Max<float>::combine(max_inv_hamming, my_inv_hamming);
            }
            result += max_inv_hamming;
        }
    }
    state.pop_pop_push(state.stash.create<DoubleValue>(result));
}

struct ChunkedParam {
    size_t vec_size;
    size_t chunk_dim;
    ChunkedParam(size_t vec_size_in, size_t chunk_dim_in) : vec_size(vec_size_in), chunk_dim(chunk_dim_in) {}
};

void my_max_sum_max_inv_hamming_op(InterpretedFunction::State& state, uint64_t param_in) {
    const auto&  param = unwrap_param<ChunkedParam>(param_in);
    double       result = 0.0;
    auto         query_cells = state.peek(1).cells().unsafe_typify<int8_t>();
    const Value& document = state.peek(0);
    auto         document_cells = document.cells().unsafe_typify<int8_t>();
    if ((query_cells.size() > 0) && (document_cells.size() > 0)) {
        size_t                               num_query_vectors = query_cells.size() / param.vec_size;
        size_t                               max_distance = param.vec_size * 8;
        size_t                               num_chunks = 0;
        std::unordered_map<uint32_t, size_t> chunk_index;
        std::vector<size_t>                  min_distances;
        std::array<string_id, 2>             address;
        std::array<string_id*, 2>            address_ref = {&address[0], &address[1]};
        size_t                               subspace = 0;
        auto                                 view = document.index().create_view({});
        view->lookup({});
        while (view->next_result(address_ref, subspace)) {
            auto [pos, inserted] = chunk_index.try_emplace(address[param.chunk_dim].value(), num_chunks);
            if (inserted) {
                min_distances.resize(++num_chunks * num_query_vectors, max_distance);
            }
            size_t*       chunk_distances = min_distances.data() + (pos->second * num_query_vectors);
            const int8_t* document_vector = document_cells.data() + (subspace * param.vec_size);
            for (size_t i = 0; i < num_query_vectors; ++i) {
                if (chunk_distances[i] != 0) {
                    const int8_t* query_vector = query_cells.data() + (i * param.vec_size);
                    chunk_distances[i] = std::min(
                        chunk_distances[i],
                        hwaccelerated::binary_hamming_distance(query_vector, document_vector, param.vec_size));
                }
            }
        }
        // sum in float to match the cell type of the per-chunk tensor produced by generic evaluation
        float max_chunk_score = aggr::Max<float>::null_value();
        for (size_t chunk = 0; chunk < num_chunks; ++chunk) {
            const size_t* chunk_distances = min_distances.data() + (chunk * num_query_vectors);
            float         chunk_score = 0.0f;
            for (size_t i = 0; i < num_query_vectors; ++i) {
                chunk_score += 1.0f / (1.0f + chunk_distances[i]);
            }
            max_chunk_score = aggr::Max<float>::combine(max_chunk_score, chunk_score);
        }
        result = max_chunk_score;
    }
    state.pop_pop_push(state.stash.create<DoubleValue>(result));
}

const Reduce* check_reduce(const TensorFunction& expr, Aggr aggr) {
    if (auto reduce = as<Reduce>(expr)) {
        if ((reduce->aggr() == aggr) && (reduce->dimensions().size() == 1)) {
            return reduce;
        }
    }
    return nullptr;
}

const Join* check_join(const TensorFunction& expr, op2_t op) {
    if (auto join = as<Join>(expr)) {
        if (join->function() == op) {
            return join;
        }
    }
    return nullptr;
}

bool is_one(const TensorFunction& expr) {
    if (expr.result_type().is_double()) {
        if (auto const_value = as<ConstValue>(expr)) {
            return (const_value->value().as_double() == 1.0);
        }
    }
    return false;
}

// 1/(1+x) -> x
// 1/(x+1) -> x
const TensorFunction* check_inv(const TensorFunction& expr) {
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

bool is_mapped_dim(const ValueType& type, const std::string& dim) {
    size_t npos = ValueType::Dimension::npos;
    size_t idx = type.dimension_index(dim);
    return (idx != npos) && type.dimensions()[idx].is_mapped();
}

// requirements shared by the plain and the chunked optimization: a double result, an
// int8 query holding the sum dimension, and int8 cells with the hamming dimension
// innermost in both inputs. What differs is the shape of the document; see below.
bool check_common_params(const ValueType& res_type, const ValueType& query, const ValueType& document,
                         const std::string& sum_dim, const std::string& ham_dim) {
    return (res_type.is_double() && (query.dimensions().size() == 2) && (query.cell_type() == CellType::INT8) &&
            query.has_dimension(sum_dim) && (query.stride_of(ham_dim) == 1) &&
            (document.cell_type() == CellType::INT8) && (document.stride_of(ham_dim) == 1));
}

// the document holds the max dimension, which may be either mapped or indexed
bool check_params(const ValueType& res_type, const ValueType& query, const ValueType& document,
                  const std::string& sum_dim, const std::string& max_dim, const std::string& ham_dim) {
    return (check_common_params(res_type, query, document, sum_dim, ham_dim) && (document.dimensions().size() == 2) &&
            document.has_dimension(max_dim));
}

// the document groups its vectors into chunks, so both the max dimension and the
// chunk dimension must be mapped
bool check_chunked_params(const ValueType& res_type, const ValueType& query, const ValueType& document,
                          const std::string& sum_dim, const std::string& max_dim, const std::string& chunk_dim,
                          const std::string& ham_dim) {
    return (check_common_params(res_type, query, document, sum_dim, ham_dim) && (document.dimensions().size() == 3) &&
            (document.count_mapped_dimensions() == 2) && is_mapped_dim(document, max_dim) &&
            is_mapped_dim(document, chunk_dim));
}

size_t get_dim_size(const ValueType& type, const std::string& dim) {
    size_t npos = ValueType::Dimension::npos;
    size_t idx = type.dimension_index(dim);
    assert(idx != npos);
    return type.dimensions()[idx].size;
}

// check_chunked_params has verified that the document has exactly two mapped
// dimensions and that the chunk dimension is one of them
size_t get_chunk_dim(const ValueType& document, const std::string& chunk_dim) {
    return (document.mapped_dimensions()[0].name == chunk_dim) ? 0 : 1;
}

} // namespace

SumMaxInvHammingFunction::SumMaxInvHammingFunction(const ValueType& res_type_in, const TensorFunction& query,
                                                   const TensorFunction& document, size_t vec_size)
    : tensor_function::Op2(res_type_in, query, document), _vec_size(vec_size) {
}

InterpretedFunction::Instruction SumMaxInvHammingFunction::compile_self(const ValueBuilderFactory&, Stash&) const {
    return InterpretedFunction::Instruction(my_sum_max_inv_hamming_op, _vec_size);
}

const TensorFunction& SumMaxInvHammingFunction::optimize(const TensorFunction& expr, Stash& stash) {
    if (auto sum_reduce = check_reduce(expr, Aggr::SUM)) {
        if (auto max_reduce = check_reduce(sum_reduce->child(), Aggr::MAX)) {
            if (auto inverted = check_inv(max_reduce->child())) {
                if (auto ham_reduce = check_reduce(*inverted, Aggr::SUM)) {
                    if (auto ham = check_join(ham_reduce->child(), Hamming::f)) {
                        const auto& sum_dim = sum_reduce->dimensions()[0];
                        const auto& max_dim = max_reduce->dimensions()[0];
                        const auto& ham_dim = ham_reduce->dimensions()[0];
                        if (check_params(expr.result_type(), ham->lhs().result_type(), ham->rhs().result_type(),
                                         sum_dim, max_dim, ham_dim))
                        {
                            size_t vec_size = get_dim_size(ham->lhs().result_type(), ham_dim);
                            return stash.create<SumMaxInvHammingFunction>(expr.result_type(), ham->lhs(), ham->rhs(),
                                                                          vec_size);
                        }
                        if (check_params(expr.result_type(), ham->rhs().result_type(), ham->lhs().result_type(),
                                         sum_dim, max_dim, ham_dim))
                        {
                            size_t vec_size = get_dim_size(ham->rhs().result_type(), ham_dim);
                            return stash.create<SumMaxInvHammingFunction>(expr.result_type(), ham->rhs(), ham->lhs(),
                                                                          vec_size);
                        }
                    }
                }
            }
        }
    }
    return expr;
}

MaxSumMaxInvHammingFunction::MaxSumMaxInvHammingFunction(const ValueType& res_type_in, const TensorFunction& query,
                                                         const TensorFunction& document, size_t vec_size,
                                                         size_t chunk_dim)
    : tensor_function::Op2(res_type_in, query, document), _vec_size(vec_size), _chunk_dim(chunk_dim) {
}

InterpretedFunction::Instruction MaxSumMaxInvHammingFunction::compile_self(const ValueBuilderFactory&,
                                                                           Stash& stash) const {
    const auto& param = stash.create<ChunkedParam>(_vec_size, _chunk_dim);
    return InterpretedFunction::Instruction(my_max_sum_max_inv_hamming_op, wrap_param<ChunkedParam>(param));
}

const TensorFunction& MaxSumMaxInvHammingFunction::optimize(const TensorFunction& expr, Stash& stash) {
    if (auto chunk_reduce = check_reduce(expr, Aggr::MAX)) {
        if (auto sum_reduce = check_reduce(chunk_reduce->child(), Aggr::SUM)) {
            if (auto max_reduce = check_reduce(sum_reduce->child(), Aggr::MAX)) {
                if (auto inverted = check_inv(max_reduce->child())) {
                    if (auto ham_reduce = check_reduce(*inverted, Aggr::SUM)) {
                        if (auto ham = check_join(ham_reduce->child(), Hamming::f)) {
                            const auto& sum_dim = sum_reduce->dimensions()[0];
                            const auto& max_dim = max_reduce->dimensions()[0];
                            const auto& chunk_dim = chunk_reduce->dimensions()[0];
                            const auto& ham_dim = ham_reduce->dimensions()[0];
                            if (check_chunked_params(expr.result_type(), ham->lhs().result_type(),
                                                     ham->rhs().result_type(), sum_dim, max_dim, chunk_dim, ham_dim))
                            {
                                size_t vec_size = get_dim_size(ham->lhs().result_type(), ham_dim);
                                return stash.create<MaxSumMaxInvHammingFunction>(
                                    expr.result_type(), ham->lhs(), ham->rhs(), vec_size,
                                    get_chunk_dim(ham->rhs().result_type(), chunk_dim));
                            }
                            if (check_chunked_params(expr.result_type(), ham->rhs().result_type(),
                                                     ham->lhs().result_type(), sum_dim, max_dim, chunk_dim, ham_dim))
                            {
                                size_t vec_size = get_dim_size(ham->rhs().result_type(), ham_dim);
                                return stash.create<MaxSumMaxInvHammingFunction>(
                                    expr.result_type(), ham->rhs(), ham->lhs(), vec_size,
                                    get_chunk_dim(ham->lhs().result_type(), chunk_dim));
                            }
                        }
                    }
                }
            }
        }
    }
    return expr;
}

} // namespace vespalib::eval
