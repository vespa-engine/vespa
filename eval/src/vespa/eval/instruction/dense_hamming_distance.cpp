// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_hamming_distance.h"
#include <vespa/eval/eval/operation.h>
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/hamming_distance.h>

#include <vespa/log/log.h>
LOG_SETUP(".eval.instruction.dense_hamming_distance");

namespace vespalib::eval {

using namespace tensor_function;

namespace {

/**
template <typename LCT, typename RCT>
struct MyHammingDistance {
    static double apply(const LCT * lhs, const RCT * rhs, size_t count) {
        double result = 0.0;
        for (size_t i = 0; i < count; ++i) {
            result += hamming_distance(lhs[i], rhs[i]);
        }
        return result;
    }
};
**/

float binary_hamming_distance(const void *lhs, const void *rhs, size_t sz) {
    const uint64_t *words_a = static_cast<const uint64_t *>(lhs);
    const uint64_t *words_b = static_cast<const uint64_t *>(rhs);
    size_t sum = 0;
    size_t i = 0;
    for (; i * 8 + 7 < sz; ++i) {
        uint64_t xor_bits = words_a[i] ^ words_b[i];
        sum += __builtin_popcountl(xor_bits);
    }
    if (__builtin_expect((i * 8 < sz), false)) {
        const uint8_t *bytes_a = static_cast<const uint8_t *>(lhs);
        const uint8_t *bytes_b = static_cast<const uint8_t *>(rhs);
        for (i *= 8; i < sz; ++i) {
            uint64_t xor_bits = bytes_a[i] ^ bytes_b[i];
            sum += __builtin_popcountl(xor_bits);
        }
    }
    return (float)sum;
};

struct DenseHammingDistanceParam {
    ValueType res_type;
    size_t vector_size;
    size_t out_subspace_size;

    DenseHammingDistanceParam(const ValueType &res_type_in,
                              const ValueType &mix_type,
                              const ValueType &vec_type)
        : res_type(res_type_in),
          vector_size(vec_type.dense_subspace_size()),
          out_subspace_size(res_type.dense_subspace_size())
    {
        assert(vector_size * out_subspace_size == mix_type.dense_subspace_size());
    }
};

void int8_hamming_to_double_op(InterpretedFunction::State &state, uint64_t vector_size) {
    const auto &lhs = state.peek(1);
    const auto &rhs = state.peek(0);
    LOG_ASSERT(lhs.index().size() == 1);
    LOG_ASSERT(rhs.index().size() == 1);
    auto a = lhs.cells();
    auto b = rhs.cells();
    LOG_ASSERT(a.type == CellType::INT8);
    LOG_ASSERT(b.type == CellType::INT8);
    LOG_ASSERT(a.size == vector_size);
    LOG_ASSERT(b.size == vector_size);
    double result = binary_hamming_distance(a.data, b.data, vector_size);
    state.pop_pop_push(state.stash.create<DoubleValue>(result));
}

} // namespace <unnamed>

DenseHammingDistance::DenseHammingDistance(const ValueType &res_type_in,
                                           const TensorFunction &dense_child,
                                           const TensorFunction &vector_child)
    : tensor_function::Op2(res_type_in, dense_child, vector_child)
{
}

InterpretedFunction::Instruction
DenseHammingDistance::compile_self(const ValueBuilderFactory &, Stash &) const
{
    auto op = int8_hamming_to_double_op;
    const auto &lhs_type = lhs().result_type();
    const auto &rhs_type = rhs().result_type();
    LOG_ASSERT(lhs_type.dense_subspace_size() == rhs_type.dense_subspace_size());
    return InterpretedFunction::Instruction(op, lhs_type.dense_subspace_size());
}

bool DenseHammingDistance::compatible_types(const ValueType &lhs, const ValueType &rhs) {
    if (lhs.cell_type() != CellType::INT8) return false;
    if (rhs.cell_type() != CellType::INT8) return false;
    if (! lhs.is_dense()) return false;
    if (! rhs.is_dense()) return false;
    return (lhs.nontrivial_indexed_dimensions() == rhs.nontrivial_indexed_dimensions());
}

const TensorFunction &
DenseHammingDistance::optimize(const TensorFunction &expr, Stash &stash)
{
    const auto & res_type = expr.result_type();
    auto reduce = as<Reduce>(expr);
    if (res_type.is_double() && reduce && (reduce->aggr() == Aggr::SUM)) {
        auto join = as<Join>(reduce->child());
        if (join && (join->function() == operation::Hamming::f)) {
            const TensorFunction &lhs = join->lhs();
            const TensorFunction &rhs = join->rhs();
            if (compatible_types(lhs.result_type(), rhs.result_type())) {
                return stash.create<DenseHammingDistance>(res_type, lhs, rhs);
            }
        }
    }
    return expr;
}

} // namespace
