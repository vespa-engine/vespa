// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/tensor_function.h>

namespace vespalib::eval {

/**
 * Tensor function combining multiple inverted hamming distances with
 * multiple layers of aggregation, resulting in a single scalar
 * result.
 *
 * inputs:
 *   query:    tensor<int8>(qt{},x[32])
 *   document: tensor<int8>(dt{},x[32])
 *
 * expression:
 *   reduce(
 *     reduce(
 *       1/(1+reduce(hamming(query, document), sum, x)),
 *       max, dt
 *     ),
 *     sum, qt
 *   )
 *
 * Both query and document contains a collection of binary int8
 * vectors. For each query vector, take the inverted hamming distance
 * against all document vectors and select the maximum result. Sum
 * these partial results into the final result value.
 **/
class SumMaxInvHammingFunction : public tensor_function::Op2
{
private:
    size_t _vec_size;
public:
    SumMaxInvHammingFunction(const ValueType &res_type_in,
                             const TensorFunction &query,
                             const TensorFunction &document,
                             size_t vec_size);
    InterpretedFunction::Instruction compile_self(const ValueBuilderFactory &factory, Stash &stash) const override;
    size_t vec_size() const { return _vec_size; }
    bool result_is_mutable() const override { return true; }
    static const TensorFunction &optimize(const TensorFunction &expr, Stash &stash);
};

} // namespace
